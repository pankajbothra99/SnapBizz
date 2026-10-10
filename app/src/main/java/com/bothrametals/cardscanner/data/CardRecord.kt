package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.OcrLine
import org.json.JSONArray
import org.json.JSONObject

/** One scanned business card as stored on the phone. */
data class CardRecord(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val frontImage: String?,
    val backImage: String?,
    val rawLines: List<OcrLine>,
    val fields: CardFields,
    val savedToContacts: Boolean = false,
)

object JsonCodec {

    fun toJson(r: CardRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
        put("frontImage", r.frontImage ?: JSONObject.NULL)
        put("backImage", r.backImage ?: JSONObject.NULL)
        put("savedToContacts", r.savedToContacts)
        put("rawLines", JSONArray().apply {
            r.rawLines.forEach { put(JSONObject().put("t", it.text).put("h", it.height).put("s", it.side)) }
        })
        put("fields", fieldsToJson(r.fields))
    }

    fun fromJson(o: JSONObject): CardRecord {
        val linesJson = o.optJSONArray("rawLines") ?: JSONArray()
        val lines = (0 until linesJson.length()).map { i ->
            val l = linesJson.getJSONObject(i)
            OcrLine(l.optString("t"), l.optInt("h"), l.optInt("s"))
        }
        return CardRecord(
            id = o.getString("id"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            frontImage = nullableString(o, "frontImage"),
            backImage = nullableString(o, "backImage"),
            rawLines = lines,
            fields = fieldsFromJson(o.optJSONObject("fields") ?: JSONObject()),
            savedToContacts = o.optBoolean("savedToContacts"),
        )
    }

    private fun fieldsToJson(f: CardFields) = JSONObject().apply {
        put("prefix", f.prefix)
        put("name", f.name)
        put("jobTitle", f.jobTitle)
        put("company", f.company)
        put("mobiles", JSONArray(f.mobiles))
        put("phones", JSONArray(f.phones))
        put("faxes", JSONArray(f.faxes))
        put("emails", JSONArray(f.emails))
        put("websites", JSONArray(f.websites))
        put("address", f.address)
        put("gstin", f.gstin)
        put("extraInfo", f.extraInfo)
        put("notes", f.notes)
    }

    private fun fieldsFromJson(o: JSONObject) = CardFields(
        prefix = o.optString("prefix"),
        name = o.optString("name"),
        jobTitle = o.optString("jobTitle"),
        company = o.optString("company"),
        mobiles = strings(o.optJSONArray("mobiles")),
        phones = strings(o.optJSONArray("phones")),
        faxes = strings(o.optJSONArray("faxes")),
        emails = strings(o.optJSONArray("emails")),
        websites = strings(o.optJSONArray("websites")),
        address = o.optString("address"),
        gstin = o.optString("gstin"),
        extraInfo = o.optString("extraInfo"),
        notes = o.optString("notes"),
    )

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }

    private fun nullableString(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
}
