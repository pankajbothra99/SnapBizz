package com.bothrametals.cardscanner.ai

import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.CardParser
import com.bothrametals.cardscanner.parser.CardParser.PhoneType
import com.bothrametals.cardscanner.parser.KeywordExtractor

/** Field keys used for the orange "please check" highlight. */
object FieldKeys {
    const val NAME = "name"
    const val TITLE = "jobTitle"
    const val COMPANY = "company"
    const val MOBILES = "mobiles"
    const val PHONES = "phones"
    const val FAXES = "faxes"
    const val EMAILS = "emails"
    const val WEBSITES = "websites"
    const val ADDRESS = "address"
    const val GSTIN = "gstin"

    /** Maps the AI's field names to ours. */
    fun fromAi(name: String): String? = when (name.trim().lowercase()) {
        "name" -> NAME
        "job_title", "jobtitle", "designation" -> TITLE
        "company" -> COMPANY
        "mobiles", "mobile" -> MOBILES
        "office_phones", "phones", "phone" -> PHONES
        "faxes", "fax" -> FAXES
        "emails", "email" -> EMAILS
        "websites", "website" -> WEBSITES
        "address" -> ADDRESS
        "gstin" -> GSTIN
        else -> null
    }
}

/** Turns the AI's answer into clean contact fields, in the same formats the rest of the app uses. */
object AiPostProcess {

    data class Output(val fields: CardFields, val uncertain: Set<String>)

    fun toFields(ai: AiCard): Output {
        val uncertain = ai.uncertainFields.mapNotNull { FieldKeys.fromAi(it) }.toMutableSet()

        val mobiles = LinkedHashSet<String>()
        val phones = LinkedHashSet<String>()
        val faxes = LinkedHashSet<String>()
        fun addNumbers(raw: List<String>, hint: PhoneType, key: String) {
            for (r in raw) {
                val formatted = CardParser.formatPhones(r, hint)
                if (formatted.isEmpty()) {
                    // Keep what the AI wrote but ask the user to check it
                    val target = when (hint) { PhoneType.MOBILE -> mobiles; PhoneType.FAX -> faxes; else -> phones }
                    target += r.trim()
                    uncertain += key
                }
                for ((num, type) in formatted) when (type) {
                    PhoneType.MOBILE -> mobiles += num
                    PhoneType.FAX -> faxes += num
                    PhoneType.WORK, PhoneType.HOME -> phones += num
                }
            }
        }
        addNumbers(ai.mobiles, PhoneType.MOBILE, FieldKeys.MOBILES)
        addNumbers(ai.officePhones, PhoneType.WORK, FieldKeys.PHONES)
        addNumbers(ai.faxes, PhoneType.FAX, FieldKeys.FAXES)
        phones.removeAll(mobiles)
        faxes.removeAll(mobiles)

        val emails = ai.emails.map { it.replace(" ", "").lowercase().trim('.', ',', ';') }.filter { it.contains('@') }.distinct()
        val websites = ai.websites.map { cleanWebsite(it) }.filter { it.isNotEmpty() }.distinct()

        var gstin = ""
        if (ai.gstin.isNotBlank()) {
            val g = CardParser.cleanGstin(ai.gstin)
            if (g != null) gstin = g else {
                gstin = ai.gstin.uppercase().filter { it.isLetterOrDigit() }
                uncertain += FieldKeys.GSTIN
            }
        }

        val extras = ArrayList<String>()
        ai.otherAddresses.forEach { extras += if (it.contains(':')) it else "Other address: $it" }
        if (ai.otherText.isNotBlank()) extras += ai.otherText.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }

        val (prefix, name) = splitPrefixIfNeeded(ai.prefix, ai.name)

        val fields = CardFields(
            prefix = prefix,
            name = CardParser.smartCase(name),
            jobTitle = CardParser.smartCase(ai.jobTitle),
            company = ai.company,
            mobiles = mobiles.toList(),
            phones = phones.toList(),
            faxes = faxes.toList(),
            emails = emails,
            websites = websites,
            address = ai.address.replace(Regex("""\s+"""), " ").trim(),
            gstin = gstin,
            business = ai.business.replace(Regex("""\s+"""), " ").trim(),
            keywords = KeywordExtractor.clean(ai.keywords).ifEmpty {
                KeywordExtractor.keywords(listOf(ai.company, ai.business, ai.otherText))
            },
            extraInfo = extras.joinToString("\n"),
        )
        return Output(fields, uncertain)
    }

    fun cleanWebsite(raw: String): String = raw.trim().lowercase()
        .replace(" ", "")
        .removePrefix("https://").removePrefix("http://")
        .trimEnd('/', '.', ',')

    /** If the AI left "Dr." inside the name, move it to the prefix. */
    private fun splitPrefixIfNeeded(prefix: String, name: String): Pair<String, String> {
        if (prefix.isNotBlank()) return prefix.trim() to name.trim()
        val m = Regex("""^(?i)(dr|mr|mrs|ms|er|ca|cs|shri|smt|prof)\.?\s+(.+)$""").find(name.trim()) ?: return "" to name.trim()
        val p = m.groupValues[1]
        val shown = if (p.equals("ca", true) || p.equals("cs", true)) p.uppercase() else p.replaceFirstChar { it.uppercase() } + if (p.length <= 3) "." else ""
        return shown to m.groupValues[2]
    }
}
