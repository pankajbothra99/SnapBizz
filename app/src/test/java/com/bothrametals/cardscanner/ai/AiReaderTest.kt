package com.bothrametals.cardscanner.ai

import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.CrossCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AiReaderTest {

    private val images = listOf(CardImage("Front of card:", "AAAA"), CardImage("Back of card:", "BBBB"))

    private fun geminiReply(json: String): String {
        val escaped = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(json))
        return """{"candidates":[{"content":{"role":"model","parts":[{"text":"thinking...","thought":true},{"text":$escaped}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1800,"candidatesTokenCount":200}}"""
    }

    private val sampleCard = """
        {"prefix":"","name":"PANKAJ BOTHRA","job_title":"DIRECTOR","company":"Bothra Metals & Alloys Ltd.",
         "mobiles":["+91 98140 12345","0161-2345678"],"office_phones":["+91 98150 67890","0161 2345678/79"],"faxes":[],
         "emails":["Pankaj@BothraMetals.com"],"websites":["https://www.bothrametals.com/"],
         "address":"Plot No. 45,  Focal Point, Phase VIII, Ludhiana, Punjab 141010",
         "other_addresses":["Works: Village Sahnewal, Ludhiana"],"gstin":"O3AABCB1234C1Z5",
         "business":"Manufacturers of aluminium alloy ingots","keywords":["aluminium","aluminum","alloy ingots","ADC12","Aluminium"],
         "other_text":"Aluminium Ingots | ISO 9001:2015","uncertain_fields":["gstin"]}
    """.trimIndent()

    @Test
    fun requestHasImagesSchemaAndThinking() {
        val body = Json.parseToJsonElement(GeminiProtocol.buildRequest("gemini-3.5-flash-lite", images, "BOTHRA METALS")).jsonObject
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("2 labels + 2 images + 1 text", 5, parts.size)
        assertEquals("image/jpeg", parts[1].jsonObject["inline_data"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
        assertTrue("ocr hint included", parts[4].jsonObject["text"]!!.jsonPrimitive.content.contains("BOTHRA METALS"))
        val cfg = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", cfg["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals("minimal", cfg["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
        assertTrue("schema present", cfg["responseSchema"] != null)
        assertTrue("system prompt", body["systemInstruction"] != null)

        val full = Json.parseToJsonElement(GeminiProtocol.buildRequest("gemini-3.8-flash", images, "")).jsonObject
        assertEquals("low", full["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)

        val simple = Json.parseToJsonElement(GeminiProtocol.buildRequest("gemini-3.8-flash", images, "", simple = true)).jsonObject
        val sc = simple["generationConfig"]!!.jsonObject
        assertTrue("simple has no schema", sc["responseSchema"] == null && sc["thinkingConfig"] == null && sc["mediaResolution"] == null)
    }

    @Test
    fun parsesReplySkippingThoughts() {
        val text = GeminiProtocol.extractText(geminiReply(sampleCard))
        assertTrue("thought skipped", !text.startsWith("thinking"))
        val card = GeminiProtocol.parseCard("```json\n$text\n```")
        assertEquals("PANKAJ BOTHRA", card.name)
        assertEquals(listOf("gstin"), card.uncertainFields)
    }

    @Test
    fun blockedReplyThrows() {
        try {
            GeminiProtocol.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
            throw AssertionError("should throw")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }
    }

    @Test
    fun postProcessCleansAndReclassifies() {
        val out = AiPostProcess.toFields(GeminiProtocol.parseCard(sampleCard))
        val f = out.fields
        assertEquals("Pankaj Bothra", f.name)
        assertEquals("Director", f.jobTitle)
        assertEquals("mobile listed as office moves to mobiles", listOf("+91 98140 12345", "+91 98150 67890"), f.mobiles)
        assertEquals("landline listed as mobile moves to phones, /79 expanded",
            listOf("+91 161 2345678", "+91 161 2345679"), f.phones)
        assertEquals(listOf("pankaj@bothrametals.com"), f.emails)
        assertEquals(listOf("www.bothrametals.com"), f.websites)
        assertEquals("03AABCB1234C1Z5", f.gstin)
        assertEquals("Plot No. 45, Focal Point, Phase VIII, Ludhiana, Punjab 141010", f.address)
        assertTrue(f.extraInfo.contains("Works: Village Sahnewal"))
        assertTrue(f.extraInfo.contains("ISO 9001:2015"))
        assertTrue(FieldKeys.GSTIN in out.uncertain)
        assertEquals("Manufacturers of aluminium alloy ingots", f.business)
        assertEquals("keywords cleaned, duplicates removed", listOf("aluminium", "aluminum", "alloy ingots", "ADC12"), f.keywords)
    }

    @Test
    fun prefixInsideNameIsMoved() {
        val out = AiPostProcess.toFields(AiCard(name = "Dr. Anita Sharma", company = "X Ltd", mobiles = listOf("9909988776")))
        assertEquals("Dr.", out.fields.prefix)
        assertEquals("Anita Sharma", out.fields.name)
        assertEquals(listOf("+91 99099 88776"), out.fields.mobiles)
    }

    // ------------------------------------------------------------ client retry / fallback logic

    private class FakeTransport(val replies: MutableList<Any>) : Transport {
        val calls = ArrayList<Pair<String, String>>()
        override fun post(url: String, apiKey: String, body: String): HttpResult {
            calls += url to body
            val r = replies.removeAt(0)
            if (r is IOException) throw r
            return r as HttpResult
        }
    }

    @Test
    fun quotaOnFirstModelFallsBackToNext_andSimpleRetryOn400() {
        val t = FakeTransport(mutableListOf(
            HttpResult(429, """{"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED"}}"""),
            HttpResult(400, """{"error":{"code":400,"message":"Invalid JSON payload: unknown field thinkingLevel","status":"INVALID_ARGUMENT"}}"""),
            HttpResult(200, geminiReply(sampleCard)),
        ))
        val client = GeminiClient("key", listOf("m1", "m2"), t) {}
        val r = client.readCard(images, "")
        assertEquals("m2", r.model)
        assertTrue(t.calls[0].first.endsWith("models/m1:generateContent"))
        assertTrue("third call is the simple retry", !t.calls[2].second.contains("thinkingConfig"))
        assertEquals("PANKAJ BOTHRA", r.card.name)
    }

    @Test
    fun allModelsOutOfQuota() {
        val q = HttpResult(429, """{"error":{"code":429,"message":"quota"}}""")
        val client = GeminiClient("key", listOf("m1", "m2"), FakeTransport(mutableListOf(q, q))) {}
        try { client.readCard(images, ""); throw AssertionError("should throw") } catch (e: AiException) {
            assertEquals(AiException.Kind.QUOTA, e.kind)
        }
    }

    @Test
    fun badKeyStopsImmediately() {
        val t = FakeTransport(mutableListOf(HttpResult(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""")))
        try { GeminiClient("bad", listOf("m1", "m2"), t) {}.readCard(images, ""); throw AssertionError("should throw") } catch (e: AiException) {
            assertEquals(AiException.Kind.AUTH, e.kind)
            assertEquals(1, t.calls.size)
        }
    }

    @Test
    fun noInternet() {
        val t = FakeTransport(mutableListOf(IOException("Unable to resolve host")))
        try { GeminiClient("k", listOf("m1"), t) {}.readCard(images, ""); throw AssertionError("should throw") } catch (e: AiException) {
            assertEquals(AiException.Kind.OFFLINE, e.kind)
        }
    }

    @Test
    fun serverBusyRetriesOnceThenSucceeds() {
        val t = FakeTransport(mutableListOf(HttpResult(503, """{"error":{"code":503,"message":"overloaded"}}"""), HttpResult(200, geminiReply(sampleCard))))
        val r = GeminiClient("k", listOf("m1"), t) {}.readCard(images, "")
        assertEquals("m1", r.model)
    }

    // ------------------------------------------------------------ sub-groups

    @Test
    fun subGroupsAskedOnlyWhenTheEventHasThem() {
        val plain = GeminiProtocol.buildRequest("m", images, "")
        assertTrue("no sub-groups → not mentioned", !plain.contains("sub_groups"))
        val body = Json.parseToJsonElement(GeminiProtocol.buildRequest("m", images, "OCR", subGroups = listOf("Scrap", "Finished products"))).jsonObject
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val ask = parts.last().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(ask.contains("\"Scrap\", \"Finished products\""))
        assertTrue("OCR hint still there", ask.contains("OCR"))
        val props = body["generationConfig"]!!.jsonObject["responseSchema"]!!.jsonObject["properties"]!!.jsonObject
        assertTrue(props.containsKey("sub_groups"))
        val card = GeminiProtocol.parseCard("""{"name":"A","sub_groups":["Scrap"]}""")
        assertEquals(listOf("Scrap"), card.subGroups)
    }

    @Test
    fun sortManyCardsInOneRequest() {
        val items = listOf(GeminiProtocol.SortItem("c1", "Company: Gupta Metals; What they do: scrap dealers"),
            GeminiProtocol.SortItem("c2", "Company: Apex; What they do: die castings"))
        val req = GeminiProtocol.buildSortRequest("m-lite", listOf("Scrap", "Finished products"), items)
        assertTrue(req.contains("c1: Company: Gupta Metals") && req.contains("results"))
        val reply = """{"results":[{"key":"c1","sub_groups":["Scrap"]},{"key":"c2","sub_groups":["Finished products"," "]}]}"""
        val t = FakeTransport(mutableListOf(HttpResult(429, "{}"), HttpResult(200, geminiReply(reply))))
        val out = GeminiClient("k", listOf("m1", "m2"), t) {}.sortIntoSubGroups(listOf("Scrap", "Finished products"), items)
        assertEquals(mapOf("c1" to listOf("Scrap"), "c2" to listOf("Finished products")), out)
    }

    @Test
    fun websiteNote() {
        val req = Json.parseToJsonElement(GeminiProtocol.buildWebNoteRequest("m-lite", "Apex Castings", "https://apex.in",
            "Title: Apex\nWe make die castings", listOf("aluminium"))).jsonObject
        val text = req["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("\"Apex Castings\"") && text.contains("WEBSITE TEXT:\nTitle: Apex") && text.contains("aluminium"))
        assertTrue(req["generationConfig"]!!.jsonObject["responseSchema"] != null)

        val byUrl = Json.parseToJsonElement(GeminiProtocol.buildWebNoteFromUrlRequest("m", "Apex", "https://apex.in", emptyList())).jsonObject
        assertTrue("uses the URL tool", byUrl["tools"]!!.jsonArray[0].jsonObject.containsKey("url_context"))
        assertTrue("no JSON mode with tools", byUrl["generationConfig"] == null)

        val reply = "```json\n{\"note\": \"Makes aluminium  die castings for auto OEMs.\", \"keywords\": [\"die castings\", \" \", \"gearbox housing\"]}\n```"
        val t = FakeTransport(mutableListOf(HttpResult(200, geminiReply(reply))))
        val n = GeminiClient("k", listOf("m1"), t) {}.webNote("Apex", "https://apex.in", "text", emptyList())
        assertEquals("Makes aluminium die castings for auto OEMs.", n.note)
        assertEquals(listOf("die castings", "gearbox housing"), n.keywords)
    }

    // ------------------------------------------------------------ cross-checks

    @Test
    fun crossCheckFixesEmailTypos() {
        val r = CrossCheck.apply(CardFields(name = "Sunil Jain", company = "Jain Trading", mobiles = listOf("+91 98110 22334"),
            emails = listOf("jaintrading77@gmai1.com", "sunil@jain.corn")))
        assertEquals(listOf("jaintrading77@gmail.com", "sunil@jain.com"), r.fields.emails)
    }

    @Test
    fun crossCheckWebsiteVsEmails() {
        val r = CrossCheck.apply(CardFields(name = "A B", company = "C", mobiles = listOf("+91 98110 22334"),
            emails = listOf("info@bothrametals.com", "sales@bothrametals.com"), websites = listOf("www.bothrametai.com")))
        assertEquals(listOf("www.bothrametals.com"), r.fields.websites)

        val r2 = CrossCheck.apply(CardFields(name = "A B", company = "C", mobiles = listOf("+91 98110 22334"),
            emails = listOf("info@bothrametals.com"), websites = listOf("www.bothrametai.com")))
        assertTrue("one email only: flag, don't guess", FieldKeys.WEBSITES in r2.uncertain)
    }

    @Test
    fun crossCheckNameVsEmail() {
        val bad = CrossCheck.apply(CardFields(name = "Pankai Bothra", company = "C", mobiles = listOf("+91 98110 22334"), emails = listOf("pankaj.bothra@x.com")))
        assertTrue(FieldKeys.NAME in bad.uncertain)
        val ok = CrossCheck.apply(CardFields(name = "Pankaj Bothra", company = "C", mobiles = listOf("+91 98110 22334"), emails = listOf("pbothra@x.com")))
        assertTrue("initial+surname is fine", FieldKeys.NAME !in ok.uncertain)
    }

    @Test
    fun crossCheckGstin() {
        val good = CrossCheck.apply(CardFields(name = "A B", company = "C", mobiles = listOf("+91 98110 22334"), gstin = "27AAPFU0939F1ZV"))
        assertTrue(FieldKeys.GSTIN !in good.uncertain)
        val bad = CrossCheck.apply(CardFields(name = "A B", company = "C", mobiles = listOf("+91 98110 22334"), gstin = "27AAPFU0939F1ZX"))
        assertTrue(FieldKeys.GSTIN in bad.uncertain)
    }
}
