package com.bothrametals.cardscanner.web

import java.net.URI

/**
 * Turns a web page's HTML into plain text the AI can read: title, description and the visible text,
 * without menus' code, scripts or styles. Also finds the "About us" and "Products" pages.
 * Pure Kotlin so it can be unit tested on a PC.
 */
object HtmlText {

    data class Page(val title: String, val description: String, val text: String, val links: List<Link>)
    data class Link(val href: String, val label: String)

    private val DOT = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    fun extract(html: String, maxChars: Int = 6000): Page {
        val noComments = html.replace(Regex("<!--.*?-->", DOT), " ")
        val title = Regex("<title[^>]*>(.*?)</title>", DOT).find(noComments)?.groupValues?.get(1)?.let { clean(it) }.orEmpty()

        var description = ""
        for (m in Regex("<meta\\s[^>]*>", DOT).findAll(noComments)) {
            val attrs = attributes(m.value)
            val key = (attrs["name"] ?: attrs["property"] ?: "").lowercase()
            if (key == "description" || key == "og:description") {
                val c = clean(attrs["content"].orEmpty())
                if (c.length > description.length) description = c
            }
        }

        val links = Regex("<a\\s([^>]*)>(.*?)</a>", DOT).findAll(noComments).mapNotNull { m ->
            val href = attributes("<a " + m.groupValues[1] + ">")["href"]?.trim() ?: return@mapNotNull null
            Link(href, clean(stripTags(m.groupValues[2])))
        }.toList()

        var body = noComments
        for (tag in listOf("script", "style", "noscript", "svg", "template", "iframe", "head")) {
            body = body.replace(Regex("<$tag\\b.*?</$tag\\s*>", DOT), " ")
        }
        body = body.replace(Regex("<(br|p|div|li|h[1-6]|tr|td|th|section|article|header|footer|ul|ol|table|nav|dt|dd)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        val lines = stripTags(body).split('\n').map { clean(it) }.filter { it.length >= 3 }
        val seen = HashSet<String>()
        val text = StringBuilder()
        for (l in lines) {
            if (!seen.add(l.lowercase())) continue
            if (text.length + l.length + 1 > maxChars) break
            text.append(l).append('\n')
        }
        return Page(title, description, text.toString().trim(), links)
    }

    /** Up to two pages on the same site worth reading: "About us" first, then "Products / Services". */
    fun infoLinks(pageUrl: String, links: List<Link>): List<String> {
        val base = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        val host = base.host?.lowercase()?.removePrefix("www.") ?: return emptyList()
        fun resolve(href: String): String? {
            if (href.startsWith("#") || href.startsWith("mailto:", true) || href.startsWith("tel:", true) ||
                href.startsWith("javascript:", true) || href.startsWith("whatsapp:", true)
            ) return null
            val u = runCatching { base.resolve(href.replace(" ", "%20")) }.getOrNull() ?: return null
            if (u.scheme != "http" && u.scheme != "https") return null
            if (u.host?.lowercase()?.removePrefix("www.") != host) return null
            val s = u.toString().substringBefore('#')
            if (Regex("""\.(pdf|jpg|jpeg|png|gif|zip|docx?|xlsx?)$""", RegexOption.IGNORE_CASE).containsMatchIn(s)) return null
            return s
        }
        val about = Regex("about|company|profile|who-we-are|who_we_are|overview|introduction", RegexOption.IGNORE_CASE)
        val products = Regex("product|service|solution|what-we-do|capabilit|our-range|range|offering|portfolio", RegexOption.IGNORE_CASE)
        val home = base.toString().substringBefore('#').trimEnd('/')
        fun first(rx: Regex) = links.firstNotNullOfOrNull { l ->
            if (rx.containsMatchIn(l.href) || rx.containsMatchIn(l.label)) resolve(l.href)?.takeIf { it.trimEnd('/') != home } else null
        }
        return listOfNotNull(first(about), first(products)).distinct()
    }

    private fun attributes(tag: String): Map<String, String> {
        val out = HashMap<String, String>()
        Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""").findAll(tag).forEach { m ->
            val v = m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { m.groupValues[5] }
            out.putIfAbsent(m.groupValues[1].lowercase(), decodeEntities(v))
        }
        return out
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]*>"), " ")

    private fun clean(s: String) = decodeEntities(stripTags(s)).replace(Regex("""[\s ]+"""), " ").trim()

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "hellip" to "…", "copy" to "©", "reg" to "®",
        "trade" to "™", "deg" to "°", "times" to "×", "rupee" to "₹", "bull" to "•", "middot" to "·",
    )

    fun decodeEntities(s: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { codePoint(it) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { codePoint(it) } ?: m.value
            else -> NAMED[e.lowercase()] ?: m.value
        }
    }

    private fun codePoint(c: Int): String? = runCatching { String(Character.toChars(c)) }.getOrNull()
}
