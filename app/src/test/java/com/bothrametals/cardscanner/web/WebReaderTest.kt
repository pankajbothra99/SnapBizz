package com.bothrametals.cardscanner.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebReaderTest {

    private val home = """
        <!doctype html><html><head><title>Apex Castings &amp; Alloys | Home</title>
        <meta property="og:description" content="Aluminium die castings for auto parts">
        <meta content="Leading manufacturer of aluminium pressure die castings and LM6 / ADC12 ingots in Rajkot." name="description">
        <style>.x{color:red}</style><script>var a = "<p>not text</p>";</script></head>
        <body><nav><a href="/">Home</a> <a href="/about-us.html">About Us</a> <a href="products/">Our Range</a>
        <a href="https://facebook.com/apex">Facebook</a> <a href="mailto:info@apex.in">Mail</a> <a href="/brochure.pdf">Brochure</a></nav>
        <h1>Precision Die Castings</h1><p>We supply   aluminium&nbsp;castings to OEMs.</p><p>We supply aluminium castings to OEMs.</p>
        <!-- hidden comment --><div>ISO 9001:2015 &#8226; IATF 16949</div></body></html>
    """.trimIndent()

    @Test
    fun extractsReadableText() {
        val p = HtmlText.extract(home)
        assertEquals("Apex Castings & Alloys | Home", p.title)
        assertTrue("longest description wins", p.description.startsWith("Leading manufacturer"))
        assertTrue(p.text.contains("Precision Die Castings"))
        assertTrue(p.text.contains("We supply aluminium castings to OEMs."))
        assertEquals("repeated line kept once", 1, Regex("We supply").findAll(p.text).count())
        assertTrue("entities decoded", p.text.contains("ISO 9001:2015 • IATF 16949"))
        assertTrue("no script or style", !p.text.contains("not text") && !p.text.contains("color"))
        assertTrue(!p.text.contains("hidden comment"))
    }

    @Test
    fun findsAboutAndProductPagesOnSameSite() {
        val p = HtmlText.extract(home)
        assertEquals(
            listOf("https://www.apex.in/about-us.html", "https://www.apex.in/products/"),
            HtmlText.infoLinks("https://www.apex.in/", p.links),
        )
    }

    @Test
    fun readsHomeThenAboutAndProducts_fallsBackToHttp() {
        val pages = mapOf(
            "http://www.apex.in" to WebReader.Fetched("http://www.apex.in/", home),
            "http://www.apex.in/about-us.html" to WebReader.Fetched("http://www.apex.in/about-us.html",
                "<title>About</title><p>Founded in 1998, 200 staff, exports to Europe.</p>"),
            "http://www.apex.in/products/" to WebReader.Fetched("http://www.apex.in/products/",
                "<title>Products</title><li>Gearbox housings</li><li>Motor end covers</li>"),
        )
        val asked = ArrayList<String>()
        val r = WebReader.read("www.apex.in") { url -> asked += url; pages[url] }!!
        assertEquals("https first, then http", listOf("https://www.apex.in", "http://www.apex.in"), asked.take(2))
        assertEquals("http://www.apex.in/", r.url)
        assertTrue(r.text.startsWith("Title: Apex Castings"))
        assertTrue(r.text.contains("Founded in 1998"))
        assertTrue(r.text.contains("Gearbox housings"))
        assertEquals(null, WebReader.read("www.nowhere.in") { null })
    }
}
