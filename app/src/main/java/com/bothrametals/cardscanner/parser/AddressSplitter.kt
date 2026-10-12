package com.bothrametals.cardscanner.parser

/**
 * Splits a one-line address into street / city / state / PIN / country, because many Contacts apps
 * (Google Contacts, Samsung Contacts) only show addresses that come in these separate parts.
 * Tuned for Indian addresses; anything it can't split stays in "street". Pure Kotlin.
 */
object AddressSplitter {

    data class Parts(
        val street: String,
        val city: String = "",
        val state: String = "",
        val postcode: String = "",
        val country: String = "",
    )

    private val PIN = Regex("""(?<!\d)([1-9]\d{2})\s?(\d{3})(?!\d)""")

    /** Longest names first so "Uttar Pradesh" wins over "Pradesh". */
    private val STATES = listOf(
        "Andaman and Nicobar Islands", "Dadra and Nagar Haveli and Daman and Diu", "Jammu and Kashmir", "Jammu & Kashmir",
        "Andhra Pradesh", "Arunachal Pradesh", "Himachal Pradesh", "Madhya Pradesh", "Uttar Pradesh", "West Bengal",
        "Tamil Nadu", "Chhattisgarh", "Uttarakhand", "Uttaranchal", "Maharashtra", "Karnataka", "Telangana", "Rajasthan",
        "Puducherry", "Pondicherry", "Meghalaya", "Nagaland", "Mizoram", "Manipur", "Tripura", "Jharkhand", "Haryana",
        "Gujarat", "Punjab", "Kerala", "Odisha", "Orissa", "Sikkim", "Assam", "Bihar", "Goa", "Delhi", "Chandigarh", "Ladakh",
        "Lakshadweep",
    )
    private val STATE_SHORT = mapOf(
        "u.p." to "Uttar Pradesh", "up" to "Uttar Pradesh", "m.p." to "Madhya Pradesh", "mp" to "Madhya Pradesh",
        "h.p." to "Himachal Pradesh", "a.p." to "Andhra Pradesh", "t.n." to "Tamil Nadu", "w.b." to "West Bengal",
        "j&k" to "Jammu and Kashmir", "mh" to "Maharashtra",
    )
    /** Cities that are also states/UTs: keep them as the city too. */
    private val CITY_STATES = setOf("Delhi", "Chandigarh", "Puducherry", "Goa")

    fun split(address: String): Parts {
        var rest = " " + address.replace(Regex("""\s+"""), " ").trim() + " "
        if (rest.isBlank()) return Parts("")

        // PIN code (last one on the line)
        var postcode = ""
        PIN.findAll(rest).lastOrNull()?.let { m ->
            postcode = m.groupValues[1] + m.groupValues[2]
            rest = rest.removeRange(m.range)
        }

        // Country
        var country = ""
        Regex("""(?i)(?<![a-z])india(?![a-z])""").findAll(rest).lastOrNull()?.let { m ->
            country = "India"
            rest = rest.removeRange(m.range)
        }
        if (country.isEmpty() && postcode.isNotEmpty()) country = "India"

        // "New Delhi" is a city, not "New" + the state Delhi
        var city = ""
        var state = ""
        Regex("""(?i)(?<![a-z])new\s+delhi(?![a-z])""").findAll(rest).lastOrNull()?.let { m ->
            city = "New Delhi"
            state = "Delhi"
            rest = rest.removeRange(m.range)
            // "…, New Delhi, Delhi 110020": drop the separate state word too
            Regex("""(?i)(?<![a-z])delhi(?![a-z])""").findAll(rest).lastOrNull()?.let { d -> rest = rest.removeRange(d.range) }
        }

        // State (full name or common short form, possibly in brackets)
        if (state.isEmpty()) for (s in STATES) {
            val m = Regex("""(?i)(?<![a-z])${Regex.escape(s)}(?![a-z])""").findAll(rest).lastOrNull() ?: continue
            state = if (s == "Jammu & Kashmir") "Jammu and Kashmir" else s
            rest = rest.removeRange(m.range)
            break
        }
        if (state.isEmpty()) {
            for ((short, full) in STATE_SHORT) {
                val m = Regex("""(?i)(?<![a-z.&])${Regex.escape(short)}(?![a-z])""").findAll(rest).lastOrNull() ?: continue
                // Only treat "UP"/"MP" etc. as a state near the end of the address
                if (m.range.first < rest.length * 0.5) continue
                state = full
                rest = rest.removeRange(m.range)
                break
            }
        }

        // Tidy what's left: drop empty brackets, dangling dashes and commas
        rest = rest.replace(Regex("""\(\s*\)"""), " ")
            .replace(Regex("""\s*[-–]\s*(?=,|$|\s*$)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*,\s*(,\s*)+"""), ", ")
            .trim()
            .trim(',', '-', '–', ' ', '.')
            .trim()

        if (postcode.isEmpty() && state.isEmpty() && country.isEmpty()) {
            return Parts(street = address.trim()) // e.g. a foreign address: keep it as it is
        }

        // City = the last comma-separated part (or the last word if there are no commas)
        val segments = rest.split(',').map { it.trim().trim('-', '–').trim() }.filter { it.isNotEmpty() }
        var street = rest
        if (city.isNotEmpty()) {
            street = segments.joinToString(", ")
        } else if (segments.size >= 2) {
            val last = segments.last()
            if (last.split(' ').size <= 3 && last.none { it.isDigit() }) {
                city = last
                street = segments.dropLast(1).joinToString(", ")
            }
        } else if (segments.size == 1) {
            val words = segments[0].split(' ')
            val lastWord = words.last().trim('-', '–', '.')
            if (words.size >= 2 && lastWord.none { it.isDigit() } && lastWord.length >= 3) {
                city = lastWord
                street = words.dropLast(1).joinToString(" ").trim().trim(',', '-', '–').trim()
            }
        }
        if (city.isEmpty() && state in CITY_STATES) city = state
        return Parts(street = street, city = city, state = state, postcode = postcode, country = country)
    }
}
