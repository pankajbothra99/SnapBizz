package com.bothrametals.cardscanner.parser

import com.bothrametals.cardscanner.ai.FieldKeys

/**
 * Combines what was read from the printed card with what the card's QR code holds.
 * The QR code is exact (no reading mistakes), so where the two differ the QR wins and the field is
 * marked orange so the user can glance at it. Anything only in the QR is added.
 * Pure Kotlin so it can be unit tested on a PC.
 */
object QrMerge {

    data class Result(val fields: CardFields, val flagged: Set<String>)

    fun merge(card: CardFields, qr: QrParser.QrInfo): Result {
        if (qr.isEmpty) return Result(card, emptySet())
        val flags = HashSet<String>()
        val q = qr.contact
        var f = card

        if (qr.hasContact) {
            f = f.copy(
                name = pick(f.name, q.name, FieldKeys.NAME, flags),
                jobTitle = pick(f.jobTitle, q.jobTitle, FieldKeys.TITLE, flags),
                company = pick(f.company, q.company, FieldKeys.COMPANY, flags),
            )
            if (q.address.isNotBlank()) {
                when {
                    f.address.isBlank() -> f = f.copy(address = q.address)
                    !sameText(f.address, q.address) && !letters(f.address).contains(letters(q.address)) ->
                        // The printed address is usually the fuller one; keep the QR's version to compare
                        f = f.copy(extraInfo = addLine(f.extraInfo, "Address in QR code: ${q.address}"))
                }
            }
        }

        // Phone numbers: same number → nothing to do; one digit different → the QR fixes a misread; else add
        var mobiles = f.mobiles
        var phones = f.phones
        var faxes = f.faxes
        fun addNumber(n: String, kind: String) {
            val key = digitsKey(n)
            if (key.length < 6) return
            if ((mobiles + phones + faxes).any { digitsKey(it) == key }) return
            val misread = { list: List<String> -> list.indexOfFirst { oneDigitOff(digitsKey(it), key) } }
            for ((list, flag) in listOf(mobiles to FieldKeys.MOBILES, phones to FieldKeys.PHONES, faxes to FieldKeys.FAXES)) {
                val i = misread(list)
                if (i >= 0) {
                    val fixed = list.toMutableList().also { it[i] = n }
                    when (flag) {
                        FieldKeys.MOBILES -> mobiles = fixed
                        FieldKeys.PHONES -> phones = fixed
                        else -> faxes = fixed
                    }
                    flags += flag
                    return
                }
            }
            when (kind) {
                "m" -> mobiles = mobiles + n
                "f" -> faxes = faxes + n
                else -> phones = phones + n
            }
        }
        q.mobiles.forEach { addNumber(it, "m") }
        qr.whatsapp.forEach { addNumber(it, "m") }
        q.phones.forEach { addNumber(it, "p") }
        q.faxes.forEach { addNumber(it, "f") }

        // Emails: a near-miss of a QR email is a misread
        var emails = f.emails
        for (e in q.emails) {
            if (emails.any { it.equals(e, ignoreCase = true) }) continue
            val i = emails.indexOfFirst { CrossCheck.editDistance(it.lowercase(), e) in 1..2 }
            if (i >= 0) {
                emails = emails.toMutableList().also { it[i] = e }
                flags += FieldKeys.EMAILS
            } else emails = emails + e
        }

        // Websites: compare by host; a near-miss is a misread
        var websites = f.websites
        for (w in (q.websites + qr.websites).distinct()) {
            val host = hostOf(w)
            if (websites.any { hostOf(it) == host && (it.contains('/') == w.contains('/') || !w.contains('/')) }) continue
            val i = websites.indexOfFirst { CrossCheck.editDistance(hostOf(it), host) in 1..2 && !it.contains('/') && !w.contains('/') }
            if (i >= 0) {
                websites = websites.toMutableList().also { it[i] = w }
                flags += FieldKeys.WEBSITES
            } else websites = websites + w
        }

        var extra = f.extraInfo
        qr.other.forEach { if (!extra.contains(it)) extra = addLine(extra, it) }

        return Result(
            f.copy(mobiles = mobiles, phones = phones, faxes = faxes, emails = emails, websites = websites, extraInfo = extra),
            flags,
        )
    }

    /** Printed vs QR value: QR wins when they really differ (and the field turns orange). */
    private fun pick(printed: String, fromQr: String, key: String, flags: MutableSet<String>): String {
        val q = fromQr.trim()
        if (q.isEmpty()) return printed
        if (printed.isBlank()) return tidy(q)
        if (sameText(printed, q)) return printed
        flags += key
        return tidy(q)
    }

    /** QR codes are often in capitals; show them like the rest of the card. */
    private fun tidy(s: String) = if (s.any { it.isLowerCase() }) s else CardParser.smartCase(s)

    private fun letters(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Same words, ignoring case, spaces, punctuation and "Ltd" vs "Limited" style endings. */
    private fun sameText(a: String, b: String): Boolean {
        fun norm(s: String) = letters(
            s.lowercase()
                .replace(Regex("""\b(pvt|private)\b"""), "pvt")
                .replace(Regex("""\b(ltd|limited)\b"""), "ltd")
                .replace("&", "and")
        )
        return norm(a) == norm(b)
    }

    /** Last 10 digits, so "+91 98140 12345" and "098140 12345" match. */
    private fun digitsKey(n: String) = n.filter { it.isDigit() }.takeLast(10)

    private fun oneDigitOff(a: String, b: String) =
        a.length == b.length && a.length >= 8 && a.indices.count { a[it] != b[it] } == 1

    private fun hostOf(w: String) = w.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')

    private fun addLine(old: String, line: String) = if (old.isBlank()) line else "$old\n$line"
}
