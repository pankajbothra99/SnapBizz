package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.OcrLine
import org.json.JSONArray
import org.json.JSONObject

/** Saves cards as JSON files on the phone. */
object JsonCodec {

    fun toJson(r: CardRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
        put("frontImage", r.frontImage ?: JSONObject.NULL)
        put("backImage", r.backImage ?: JSONObject.NULL)
        put("savedToContacts", r.savedToContacts)
        put("readBy", r.readBy)
        put("uncertain", JSONArray(r.uncertain))
        put("scannedBy", r.scannedBy)
        put("ownerEmail", r.ownerEmail)
        put("visibility", r.visibility)
        put("cloudDirty", r.cloudDirty)
        put("imagesUploaded", r.imagesUploaded)
        put("duplicateOf", r.duplicateOf)
        put("eventId", r.eventId)
        put("eventName", r.eventName)
        put("subGroupIds", JSONArray(r.subGroupIds))
        put("subGroupNames", JSONArray(r.subGroupNames))
        put("subGroupsSuggested", r.subGroupsSuggested)
        put("qrTexts", JSONArray(r.qrTexts))
        put("webNote", r.webNote)
        put("webNoteUrl", r.webNoteUrl)
        put("webCheckedAt", r.webCheckedAt)
        put("frontInGallery", r.frontInGallery)
        put("backInGallery", r.backInGallery)
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
            readBy = o.optString("readBy").ifBlank { CardRecord.READ_BY_PHONE },
            uncertain = strings(o.optJSONArray("uncertain")),
            scannedBy = o.optString("scannedBy"),
            ownerEmail = o.optString("ownerEmail"),
            visibility = o.optString("visibility").ifBlank { CardRecord.VISIBILITY_COMPANY },
            cloudDirty = o.optBoolean("cloudDirty"),
            imagesUploaded = o.optBoolean("imagesUploaded"),
            duplicateOf = o.optString("duplicateOf"),
            eventId = o.optString("eventId"),
            eventName = o.optString("eventName"),
            subGroupIds = strings(o.optJSONArray("subGroupIds")),
            subGroupNames = strings(o.optJSONArray("subGroupNames")),
            subGroupsSuggested = o.optBoolean("subGroupsSuggested"),
            qrTexts = strings(o.optJSONArray("qrTexts")),
            webNote = o.optString("webNote"),
            webNoteUrl = o.optString("webNoteUrl"),
            webCheckedAt = o.optLong("webCheckedAt"),
            frontInGallery = o.optBoolean("frontInGallery"),
            backInGallery = o.optBoolean("backInGallery"),
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
        put("business", f.business)
        put("keywords", JSONArray(f.keywords))
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
        business = o.optString("business"),
        keywords = strings(o.optJSONArray("keywords")),
        extraInfo = o.optString("extraInfo"),
        notes = o.optString("notes"),
    )

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }

    private fun nullableString(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
}
