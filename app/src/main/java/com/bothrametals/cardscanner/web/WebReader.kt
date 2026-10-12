package com.bothrametals.cardscanner.web

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * Opens a company's website on the phone (home page, plus its "About us" and "Products" pages)
 * and returns the readable text for the AI. Uses only the phone's internet; nothing is paid for.
 */
object WebReader {

    class Fetched(val finalUrl: String, val html: String)

    /** The text gathered from the site, or null when the site couldn't be opened. */
    class Result(val url: String, val text: String)

    /**
     * [site] like "www.example.com" or "https://example.com/about".
     * [fetch] is replaceable for tests; by default it downloads with [download].
     */
    fun read(site: String, maxChars: Int = 10_000, fetch: (String) -> Fetched? = { download(it) }): Result? {
        val start = if (site.startsWith("http://", true) || site.startsWith("https://", true)) site else "https://$site"
        val home = fetch(start)
            ?: (if (start.startsWith("https://", true)) fetch("http://" + start.substring(8)) else null)
            ?: return null
        val page = HtmlText.extract(home.html)
        val sb = StringBuilder()
        if (page.title.isNotBlank()) sb.append("Title: ").append(page.title).append('\n')
        if (page.description.isNotBlank()) sb.append("Description: ").append(page.description).append('\n')
        sb.append("\n[Home page]\n").append(page.text.take(5000)).append('\n')
        for (link in HtmlText.infoLinks(home.finalUrl, page.links)) {
            if (sb.length > maxChars - 500) break
            val sub = runCatching { fetch(link) }.getOrNull() ?: continue
            val p = HtmlText.extract(sub.html, maxChars = 3000)
            if (p.text.isBlank()) continue
            sb.append("\n[").append(p.title.ifBlank { link }).append("]\n").append(p.text).append('\n')
        }
        return Result(home.finalUrl, sb.toString().take(maxChars).trim())
    }

    /** Downloads a page (follows up to 5 redirects, at most 1.5 MB, 10 s timeouts). Null on any failure. */
    fun download(url: String, maxBytes: Int = 1_500_000, timeoutMs: Int = 10_000): Fetched? {
        var current = url
        repeat(6) {
            val conn = (runCatching { URL(current).openConnection() as HttpURLConnection }.getOrNull()) ?: return null
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/vcard,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                val type = conn.contentType.orEmpty().lowercase()
                if (type.isNotEmpty() && !type.contains("html") && !type.contains("text") && !type.contains("vcard") && !type.contains("xml")) return null
                val bytes = conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < maxBytes) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                val charset = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)
                    ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                return Fetched(current, String(bytes, charset))
            } catch (e: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }
}
