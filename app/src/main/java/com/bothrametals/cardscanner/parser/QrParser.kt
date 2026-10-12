package com.bothrametals.cardscanner.parser

import com.bothrametals.cardscanner.parser.CardParser.PhoneType

/**
 * Understands what a QR code on a business card holds: a contact (vCard / MeCard / BizCard),
 * a website, a WhatsApp link, a phone number, an email or a UPI id.
 * Pure Kotlin so it can be unit tested on a PC.
 */
object QrParser {

    data class QrInfo(
        /** Details from a contact QR; empty when the QR held no contact. */
        val contact: CardFields = CardFields(),
        val hasContact: Boolean = false,
        /** Websites, e.g. "www.example.com" or "dcard.in/AbC12" (path kept as is). */
        val websites: List<String> = emptyList(),
        /** Numbers from WhatsApp links (wa.me/…), formatted like "+91 98140 12345". */
        val whatsapp: List<String> = emptyList(),
        /** Other useful text (social media, map link, UPI id, plain text), for "Other text". */
        val other: List<String> = emptyList(),
        /** Links to a contact file (.vcf) that the app can download. */
        val vcfLinks: List<String> = emptyList(),
    ) {
        val isEmpty: Boolean get() = !hasContact && websites.isEmpty() && whatsapp.isEmpty() && other.isEmpty() && vcfLinks.isEmpty()
    }

    /** Everything from all the QR codes found on the front and back. */
    fun parse(raws: List<String>): QrInfo {
        var out = QrInfo()
        for (r in raws.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) out = combine(out, parseOne(r))
        return out
    }

    fun parseOne(raw: String): QrInfo {
        val t = raw.trim()
        val lower = t.lowercase()
        return when {
            lower.startsWith("begin:vcard") -> vcard(t)
            lower.startsWith("mecard:") -> mecard(t.substring(7))
            lower.startsWith("bizcard:") -> bizcard(t.substring(8))
            lower.startsWith("tel:") -> phoneOnly(t.substring(4))
            lower.startsWith("mailto:") -> {
                val e = t.substring(7).substringBefore('?').trim().lowercase()
                if (e.contains('@')) QrInfo(contact = CardFields(emails = listOf(e)), hasContact = true) else QrInfo()
            }
            lower.startsWith("upi://") -> {
                val pa = Regex("""[?&]pa=([^&]+)""").find(t)?.groupValues?.get(1)?.let { decode(it) }
                if (pa != null) QrInfo(other = listOf("UPI: $pa")) else QrInfo()
            }
            lower.startsWith("wifi:") || lower.startsWith("begin:vevent") || lower.startsWith("begin:vcalendar") -> QrInfo()
            looksLikeUrl(t) -> url(t)
            else -> {
                // Plain text: use a link inside it if there is one, else keep the text
                val link = Regex("""(https?://\S+|www\.\S+)""", RegexOption.IGNORE_CASE).find(t)?.value
                if (link != null) combine(url(link), QrInfo(other = listOf(t.take(200)))) else QrInfo(other = listOf(t.take(200)))
            }
        }
    }

    // ------------------------------------------------------------------ links

    private val SOCIAL = mapOf(
        "linkedin.com" to "LinkedIn", "facebook.com" to "Facebook", "fb.com" to "Facebook", "instagram.com" to "Instagram",
        "youtube.com" to "YouTube", "youtu.be" to "YouTube", "twitter.com" to "X (Twitter)", "x.com" to "X (Twitter)",
        "t.me" to "Telegram",
    )

    fun looksLikeUrl(t: String): Boolean {
        val s = t.trim().lowercase()
        if (s.contains(' ') || s.contains('\n')) return false
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("www.") ||
            Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}(/.*)?$""").matches(s)
    }

    private fun url(raw: String): QrInfo {
        val u = raw.trim().trimEnd('.', ',', ';')
        val noScheme = u.replace(Regex("""^(?i)https?://"""), "")
        val hostPart = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = hostPart.lowercase().removePrefix("www.")
        val path = noScheme.removePrefix(hostPart)
        val full = if (u.lowercase().startsWith("http")) u else "https://$u"
        return when {
            host == "wa.me" || host.endsWith("whatsapp.com") -> {
                val digits = (Regex("""phone=(\d+)""").find(path)?.groupValues?.get(1) ?: path.trim('/').substringBefore('?'))
                    .filter { it.isDigit() }
                val num = CardParser.formatPhones("+$digits", PhoneType.MOBILE).firstOrNull()?.first
                if (digits.length >= 10 && num != null) QrInfo(whatsapp = listOf(num)) else QrInfo(other = listOf("WhatsApp: $full"))
            }
            SOCIAL.keys.any { host == it || host.endsWith(".$it") } ->
                QrInfo(other = listOf("${SOCIAL.entries.first { host == it.key || host.endsWith("." + it.key) }.value}: $full"))
            host.startsWith("maps.") || host == "goo.gl" && path.startsWith("/maps") || host == "maps.app.goo.gl" ||
                (host == "google.com" || host == "google.co.in") && path.startsWith("/maps") -> QrInfo(other = listOf("Map: $full"))
            host == "play.google.com" || host == "apps.apple.com" -> QrInfo(other = listOf("App: $full"))
            path.substringBefore('?').lowercase().endsWith(".vcf") -> QrInfo(vcfLinks = listOf(full))
            else -> {
                // Host in lower case; the path is kept as it is (short links are case-sensitive)
                val cleanPath = path.substringBefore('#').let { p -> if (p.contains("utm_")) p.substringBefore('?') else p }.trimEnd('/')
                val site = (if (hostPart.lowercase().startsWith("www.")) "www.$host" else host) + cleanPath
                QrInfo(websites = listOf(site))
            }
        }
    }

    // ------------------------------------------------------------------ contacts

    private class Builder {
        var name = ""
        var title = ""
        var company = ""
        var address = ""
        val mobiles = LinkedHashSet<String>()
        val phones = LinkedHashSet<String>()
        val faxes = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
        val websites = LinkedHashSet<String>()
        val other = ArrayList<String>()
        val vcfLinks = ArrayList<String>()
        val whatsapp = ArrayList<String>()

        fun phone(raw: String, hint: PhoneType?) {
            val found = CardParser.formatPhones(raw, hint)
            if (found.isEmpty() && raw.count { it.isDigit() } >= 6) { phones += raw.trim(); return }
            for ((n, type) in found) when {
                hint == PhoneType.FAX -> faxes += n
                type == PhoneType.MOBILE -> mobiles += n
                type == PhoneType.FAX -> faxes += n
                else -> phones += n
            }
        }

        fun addUrl(raw: String) {
            val info = QrParser.url(raw)
            websites += info.websites
            other += info.other
            vcfLinks += info.vcfLinks
            whatsapp += info.whatsapp
        }

        fun build(): QrInfo {
            val contact = CardFields(
                name = name.trim(),
                jobTitle = title.trim(),
                company = company.trim(),
                mobiles = (mobiles + whatsapp).toList().distinct(),
                phones = (phones - mobiles).toList(),
                faxes = faxes.toList(),
                emails = emails.toList(),
                websites = websites.toList(),
                address = address.trim(),
            )
            val has = contact.name.isNotBlank() || contact.company.isNotBlank() || contact.mobiles.isNotEmpty() ||
                contact.phones.isNotEmpty() || contact.emails.isNotEmpty()
            return QrInfo(contact = contact, hasContact = has, websites = websites.toList(), other = other.toList(), vcfLinks = vcfLinks.toList())
        }
    }

    /** vCard 2.1 / 3.0 / 4.0. */
    fun vcard(text: String): QrInfo {
        val b = Builder()
        val unfolded = ArrayList<String>()
        for (l in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && unfolded.isNotEmpty()) unfolded[unfolded.lastIndex] += l.substring(1)
            else unfolded += l
        }
        var fn = ""
        var n = ""
        for (line in unfolded) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            val rawValue = line.substring(colon + 1)
            val prop = head.substringBefore(';').substringAfterLast('.').uppercase()
            val params = head.substringAfter(';', "").uppercase()
            val value = unescape(rawValue).trim()
            when (prop) {
                "FN" -> fn = value
                "N" -> {
                    val p = splitEscaped(rawValue, ';').map { unescape(it).trim() }
                    // Family;Given;Middle;Prefix;Suffix → "Given Middle Family"
                    n = listOf(p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, p.getOrElse(0) { "" }).filter { it.isNotBlank() }.joinToString(" ")
                }
                "ORG" -> b.company = splitEscaped(rawValue, ';').map { unescape(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
                "TITLE", "ROLE" -> if (b.title.isBlank()) b.title = value
                "TEL" -> b.phone(value.removePrefix("tel:"), when {
                    "FAX" in params -> PhoneType.FAX
                    "CELL" in params || "MOBILE" in params || "IPHONE" in params -> PhoneType.MOBILE
                    "WORK" in params || "VOICE" in params -> PhoneType.WORK
                    else -> null
                })
                "EMAIL" -> value.lowercase().removePrefix("mailto:").takeIf { it.contains('@') }?.let { b.emails += it }
                "URL" -> if (value.isNotBlank()) b.addUrl(value)
                "ADR" -> if (b.address.isBlank()) {
                    // PO box;extended;street;city;region;postcode;country
                    b.address = splitEscaped(rawValue, ';').map { unescape(it).replace('\n', ' ').trim() }
                        .filter { it.isNotBlank() }.joinToString(", ")
                }
                "NOTE" -> if (value.isNotBlank()) b.other += value.replace('\n', ' ').take(300)
                "X-SOCIALPROFILE" -> if (value.isNotBlank()) b.addUrl(value)
            }
        }
        b.name = fn.ifBlank { n }
        return b.build()
    }

    /** MECARD:N:Bothra,Pankaj;TEL:+919814012345;EMAIL:…;URL:…;ADR:…;ORG:…;NOTE:…;; */
    fun mecard(body: String): QrInfo {
        val b = Builder()
        for (part in splitEscaped(body, ';')) {
            val key = part.substringBefore(':', "").uppercase().trim()
            val value = unescape(part.substringAfter(':', "")).trim()
            if (value.isEmpty()) continue
            when (key) {
                "N" -> b.name = value.split(',').map { it.trim() }.let { p -> if (p.size >= 2) "${p[1]} ${p[0]}" else p[0] }
                "TEL" -> b.phone(value, null)
                "TEL-AV" -> b.phone(value, PhoneType.MOBILE)
                "EMAIL" -> if (value.contains('@')) b.emails += value.lowercase()
                "URL" -> b.addUrl(value)
                "ADR" -> b.address = value.replace(Regex("""\s*,\s*"""), ", ")
                "ORG" -> b.company = value
                "TITLE" -> b.title = value
                "NOTE" -> b.other += value.take(300)
            }
        }
        return b.build()
    }

    /** BIZCARD:N:Sean;X:Owen;T:Engineer;C:Company;A:Address;B:phone;M:mobile;F:fax;E:email;; */
    fun bizcard(body: String): QrInfo {
        val b = Builder()
        var first = ""
        var last = ""
        for (part in splitEscaped(body, ';')) {
            val key = part.substringBefore(':', "").uppercase().trim()
            val value = unescape(part.substringAfter(':', "")).trim()
            if (value.isEmpty()) continue
            when (key) {
                "N" -> first = value
                "X" -> last = value
                "T" -> b.title = value
                "C" -> b.company = value
                "A" -> b.address = value
                "B" -> b.phone(value, PhoneType.WORK)
                "M" -> b.phone(value, PhoneType.MOBILE)
                "F" -> b.phone(value, PhoneType.FAX)
                "E" -> if (value.contains('@')) b.emails += value.lowercase()
            }
        }
        b.name = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
        return b.build()
    }

    private fun phoneOnly(raw: String): QrInfo {
        val b = Builder()
        b.phone(raw, null)
        return b.build()
    }

    // ------------------------------------------------------------------ helpers

    private fun combine(a: QrInfo, b: QrInfo): QrInfo {
        val c1 = a.contact
        val c2 = b.contact
        val contact = CardFields(
            name = c1.name.ifBlank { c2.name },
            jobTitle = c1.jobTitle.ifBlank { c2.jobTitle },
            company = c1.company.ifBlank { c2.company },
            mobiles = (c1.mobiles + c2.mobiles).distinct(),
            phones = (c1.phones + c2.phones).distinct(),
            faxes = (c1.faxes + c2.faxes).distinct(),
            emails = (c1.emails + c2.emails).distinct(),
            websites = (c1.websites + c2.websites).distinct(),
            address = c1.address.ifBlank { c2.address },
        )
        return QrInfo(
            contact = contact,
            hasContact = a.hasContact || b.hasContact,
            websites = (a.websites + b.websites).distinctBy { it.lowercase().removePrefix("www.") },
            whatsapp = (a.whatsapp + b.whatsapp).distinct(),
            other = (a.other + b.other).distinct(),
            vcfLinks = (a.vcfLinks + b.vcfLinks).distinct(),
        )
    }

    /** Splits on [sep] but not on an escaped "\;". */
    private fun splitEscaped(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                cur.append(ch).append(s[i + 1])
                i += 2
                continue
            }
            if (ch == sep) {
                out += cur.toString()
                cur.clear()
            } else cur.append(ch)
            i++
        }
        out += cur.toString()
        return out
    }

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\:", ":").replace("\\\\", "\\")

    private fun decode(s: String): String = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
