package com.bothrametals.cardscanner.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of one HTTP call. */
class HttpResult(val code: Int, val body: String)

/** Sends a JSON body to a URL. Swappable so the retry logic can be tested without the internet. */
fun interface Transport {
    @Throws(IOException::class)
    fun post(url: String, apiKey: String, body: String): HttpResult
}

object UrlConnectionTransport : Transport {
    override fun post(url: String, apiKey: String, body: String): HttpResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 90_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }
}

/** What came back: the card plus which model read it. */
class AiReadResult(val card: AiCard, val model: String)

/**
 * Reads a card with Gemini's free tier.
 * - Tries each model in [models] in turn. When one model's free allowance is used up (HTTP 429)
 *   or it isn't available (404), the next model is tried.
 * - If a model rejects an optional setting (HTTP 400), the same model is retried with a plainer request.
 * - Never uses anything that needs billing, so it cannot cost money.
 */
class GeminiClient(
    private val apiKey: String,
    private val models: List<String>,
    private val transport: Transport = UrlConnectionTransport,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {

    /** [subGroups] = names of the event's sub-groups, so the AI can suggest which ones fit (empty = don't ask). */
    fun readCard(images: List<CardImage>, ocrHint: String, subGroups: List<String> = emptyList()): AiReadResult {
        if (apiKey.isBlank()) throw AiException(AiException.Kind.AUTH, "No AI key set. Add your free key in Settings.")
        return acrossModels { model ->
            val text = callWithRetries(model) { simple -> GeminiProtocol.buildRequest(model, images, ocrHint, simple, subGroups) }
            AiReadResult(GeminiProtocol.parseCard(text), model)
        }
    }

    /** Sorts already-read cards into sub-groups (text only). Returns key → sub-group names. */
    fun sortIntoSubGroups(subGroups: List<String>, items: List<GeminiProtocol.SortItem>): Map<String, List<String>> {
        if (apiKey.isBlank()) throw AiException(AiException.Kind.AUTH, "No AI key set. Add your free key in Settings.")
        if (subGroups.isEmpty() || items.isEmpty()) return emptyMap()
        return acrossModels { model ->
            GeminiProtocol.parseSort(callWithRetries(model) { simple -> GeminiProtocol.buildSortRequest(model, subGroups, items, simple) })
        }
    }

    /** Note about the company from its website text (downloaded by the phone). */
    fun webNote(company: String, url: String, pageText: String, knownKeywords: List<String>): GeminiProtocol.WebNote {
        if (apiKey.isBlank()) throw AiException(AiException.Kind.AUTH, "No AI key set. Add your free key in Settings.")
        return acrossModels { model ->
            GeminiProtocol.parseWebNote(callWithRetries(model) { simple ->
                GeminiProtocol.buildWebNoteRequest(model, company, url, pageText, knownKeywords, simple)
            })
        }
    }

    /** Same, but Gemini opens the page itself (for sites the phone can't read). */
    fun webNoteFromUrl(company: String, url: String, knownKeywords: List<String>): GeminiProtocol.WebNote {
        if (apiKey.isBlank()) throw AiException(AiException.Kind.AUTH, "No AI key set. Add your free key in Settings.")
        return acrossModels { model ->
            GeminiProtocol.parseWebNote(callWithRetries(model) { GeminiProtocol.buildWebNoteFromUrlRequest(model, company, url, knownKeywords) })
        }
    }

    /** Tries each free model in turn until one answers. */
    private fun <T> acrossModels(block: (model: String) -> T): T {
        var lastError: AiException? = null
        var quotaHits = 0
        val list = models.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { GeminiProtocol.DEFAULT_MODELS }

        for (model in list) {
            try {
                return block(model)
            } catch (e: AiException) {
                when (e.kind) {
                    AiException.Kind.AUTH, AiException.Kind.OFFLINE -> throw e
                    AiException.Kind.QUOTA -> { quotaHits++; lastError = e }
                    AiException.Kind.OTHER -> lastError = e
                }
            }
        }
        if (quotaHits == list.size) {
            throw AiException(AiException.Kind.QUOTA, "Today's free AI limit is used up. The card was read on the phone instead. Try \"Read with AI\" again later.")
        }
        throw lastError ?: AiException(AiException.Kind.OTHER, "The AI could not read this card")
    }

    /** Checks that the key works (used by the Test button). Returns the model that answered. */
    fun test(): String {
        if (apiKey.isBlank()) throw AiException(AiException.Kind.AUTH, "No AI key set")
        var last: AiException? = null
        for (model in models.ifEmpty { GeminiProtocol.DEFAULT_MODELS }) {
            try {
                callWithRetries(model) { GeminiProtocol.buildTestRequest() }
                return model
            } catch (e: AiException) {
                if (e.kind == AiException.Kind.AUTH || e.kind == AiException.Kind.OFFLINE) throw e
                last = e
            }
        }
        throw last ?: AiException(AiException.Kind.OTHER, "Test failed")
    }

    private fun callWithRetries(model: String, body: (simple: Boolean) -> String): String {
        val url = GeminiProtocol.ENDPOINT + model + ":generateContent"
        var simple = false
        var serverRetries = 0
        while (true) {
            val res = try {
                transport.post(url, apiKey, body(simple))
            } catch (e: IOException) {
                throw AiException(AiException.Kind.OFFLINE, "No internet connection. The card was read on the phone instead.")
            }
            val msg = GeminiProtocol.errorMessage(res.body)
            when {
                res.code in 200..299 -> return GeminiProtocol.extractText(res.body)
                res.code == 400 && msg.contains("API key", ignoreCase = true) ->
                    throw AiException(AiException.Kind.AUTH, "The AI key is not valid. Check it in Settings.")
                res.code == 401 || res.code == 403 ->
                    throw AiException(AiException.Kind.AUTH, "The AI key was refused: $msg")
                res.code == 400 && !simple -> simple = true // retry without optional settings
                res.code == 429 -> throw AiException(AiException.Kind.QUOTA, "Free limit reached for $model")
                res.code == 404 -> throw AiException(AiException.Kind.OTHER, "Model $model is not available")
                res.code >= 500 && serverRetries < 1 -> { serverRetries++; sleep(2_000) }
                else -> throw AiException(AiException.Kind.OTHER, "AI error ${res.code}: $msg")
            }
        }
    }
}
