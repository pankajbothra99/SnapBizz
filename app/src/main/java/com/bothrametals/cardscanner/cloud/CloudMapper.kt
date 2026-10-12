package com.bothrametals.cardscanner.cloud

import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.EventGroup
import com.bothrametals.cardscanner.data.SubGroup
import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.OcrLine

/**
 * Converts cards to/from the cloud (Firestore) format and decides how to merge
 * a card that changed in the cloud with the copy on this phone.
 * Pure Kotlin so it can be unit tested on a PC.
 */
object CloudMapper {

    /** Firestore collections / documents. */
    const val CARDS = "cards"
    const val CARD_IMAGES = "cardImages"
    const val MEMBERS = "members"
    const val CONFIG = "config"
    const val TEAM_DOC = "team"
    const val AI_DOC = "ai"
    const val EVENTS = "events"

    fun eventToMap(e: EventGroup): Map<String, Any?> = mapOf(
        "id" to e.id,
        "name" to e.name,
        "place" to e.place,
        "createdBy" to e.createdBy,
        "members" to e.members.map { normalizeEmail(it) }.distinct(),
        "closed" to e.closed,
        "createdAt" to e.createdAt,
        "updatedAt" to e.updatedAt,
        "subGroups" to e.subGroups.map { mapOf("id" to it.id, "name" to it.name, "at" to it.at) },
    )

    fun eventFromMap(m: Map<String, Any?>): EventGroup? {
        val id = m["id"] as? String ?: return null
        return EventGroup(
            id = id,
            name = m["name"] as? String ?: "Event",
            place = m["place"] as? String ?: "",
            createdBy = m["createdBy"] as? String ?: "",
            members = strList(m["members"]),
            closed = m["closed"] as? Boolean ?: false,
            createdAt = num(m["createdAt"]),
            updatedAt = num(m["updatedAt"]),
            subGroups = (m["subGroups"] as? List<*>).orEmpty().mapNotNull { g ->
                val gm = g as? Map<*, *> ?: return@mapNotNull null
                val gid = gm["id"] as? String ?: return@mapNotNull null
                SubGroup(gid, gm["name"] as? String ?: "", num(gm["at"]))
            },
        )
    }

    fun imageId(cardId: String, back: Boolean) = cardId + if (back) "_back" else "_front"

    /** Card → map for Firestore. The server timestamp ("syncAt") is added by the caller. */
    fun toMap(r: CardRecord): Map<String, Any?> = mapOf(
        "id" to r.id,
        "createdAt" to r.createdAt,
        "updatedAt" to r.updatedAt,
        "ownerEmail" to r.ownerEmail,
        "scannedBy" to r.scannedBy,
        "visibility" to r.visibility,
        "eventId" to r.eventId,
        "duplicateOf" to r.duplicateOf,
        "eventName" to r.eventName,
        "subGroupIds" to r.subGroupIds,
        "subGroupNames" to r.subGroupNames,
        "subGroupsSuggested" to r.subGroupsSuggested,
        "qrTexts" to r.qrTexts,
        "webNote" to r.webNote,
        "webNoteUrl" to r.webNoteUrl,
        "webCheckedAt" to r.webCheckedAt,
        "deleted" to false,
        "readBy" to r.readBy,
        "uncertain" to r.uncertain,
        "hasFront" to (r.frontImage != null),
        "hasBack" to (r.backImage != null),
        "rawLines" to r.rawLines.map { mapOf("t" to it.text, "h" to it.height.toLong(), "s" to it.side.toLong()) },
        "fields" to fieldsToMap(r.fields),
    )

    /** Marks a card as deleted in the cloud (kept as a "tombstone" so other phones remove it too). */
    fun tombstone(r: CardRecord, now: Long): Map<String, Any?> = mapOf(
        "id" to r.id,
        "ownerEmail" to r.ownerEmail,
        "visibility" to r.visibility,
        "deleted" to true,
        "updatedAt" to now,
        "createdAt" to r.createdAt,
    )

    data class Remote(val record: CardRecord, val deleted: Boolean, val hasFront: Boolean, val hasBack: Boolean)

    /** Firestore map → card. Image paths are filled in later from files on this phone. */
    fun fromMap(m: Map<String, Any?>): Remote? {
        val id = m["id"] as? String ?: return null
        val deleted = m["deleted"] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val lines = (m["rawLines"] as? List<Any?>).orEmpty().mapNotNull { l ->
            val lm = l as? Map<String, Any?> ?: return@mapNotNull null
            OcrLine(lm["t"] as? String ?: "", num(lm["h"]).toInt(), num(lm["s"]).toInt())
        }
        @Suppress("UNCHECKED_CAST")
        val fields = (m["fields"] as? Map<String, Any?>)?.let { fieldsFromMap(it) } ?: CardFields()
        val record = CardRecord(
            id = id,
            createdAt = num(m["createdAt"]),
            updatedAt = num(m["updatedAt"]),
            frontImage = null,
            backImage = null,
            rawLines = lines,
            fields = fields,
            readBy = m["readBy"] as? String ?: CardRecord.READ_BY_PHONE,
            uncertain = strList(m["uncertain"]),
            scannedBy = m["scannedBy"] as? String ?: "",
            ownerEmail = m["ownerEmail"] as? String ?: "",
            visibility = m["visibility"] as? String ?: CardRecord.VISIBILITY_COMPANY,
            eventId = m["eventId"] as? String ?: "",
            duplicateOf = m["duplicateOf"] as? String ?: "",
            eventName = m["eventName"] as? String ?: "",
            subGroupIds = strList(m["subGroupIds"]),
            subGroupNames = strList(m["subGroupNames"]),
            subGroupsSuggested = m["subGroupsSuggested"] as? Boolean ?: false,
            qrTexts = strList(m["qrTexts"]),
            webNote = m["webNote"] as? String ?: "",
            webNoteUrl = m["webNoteUrl"] as? String ?: "",
            webCheckedAt = num(m["webCheckedAt"]),
            cloudDirty = false,
            imagesUploaded = true,
        )
        return Remote(record, deleted, m["hasFront"] as? Boolean ?: false, m["hasBack"] as? Boolean ?: false)
    }

    enum class Action { KEEP_LOCAL, TAKE_REMOTE, DELETE_LOCAL }

    /**
     * What to do when the cloud has a version of a card.
     * - deleted in the cloud → remove from this phone (unless this phone has a newer unsent edit)
     * - this phone has an unsent edit that is newer → keep it (it will be uploaded)
     * - otherwise → take the cloud version
     */
    fun decide(local: CardRecord?, remote: Remote): Action = when {
        local == null -> if (remote.deleted) Action.KEEP_LOCAL else Action.TAKE_REMOTE
        local.cloudDirty && local.updatedAt > remote.record.updatedAt -> Action.KEEP_LOCAL
        remote.deleted -> Action.DELETE_LOCAL
        remote.record.updatedAt >= local.updatedAt || !local.cloudDirty -> Action.TAKE_REMOTE
        else -> Action.KEEP_LOCAL
    }

    /** Cloud version, but keep what only this phone knows (its photos, saved-to-contacts). */
    fun mergeRemoteIntoLocal(local: CardRecord?, remote: CardRecord): CardRecord = remote.copy(
        frontImage = local?.frontImage,
        backImage = local?.backImage,
        savedToContacts = local?.savedToContacts ?: false,
        frontInGallery = local?.frontInGallery ?: false,
        backInGallery = local?.backInGallery ?: false,
        imagesUploaded = true,
        cloudDirty = false,
    )

    /** Who may see / change a card (mirrors firestore.rules, used to show or hide buttons). */
    fun canSee(card: CardRecord, myEmail: String, isAdmin: Boolean): Boolean =
        isAdmin || card.visibility == CardRecord.VISIBILITY_COMPANY || sameEmail(card.ownerEmail, myEmail) || card.ownerEmail.isBlank()

    fun canEdit(card: CardRecord, myEmail: String, isAdmin: Boolean): Boolean =
        isAdmin || card.ownerEmail.isBlank() || sameEmail(card.ownerEmail, myEmail)

    fun normalizeEmail(e: String) = e.trim().lowercase()

    private fun sameEmail(a: String, b: String) = normalizeEmail(a) == normalizeEmail(b)

    private fun fieldsToMap(f: CardFields): Map<String, Any?> = mapOf(
        "prefix" to f.prefix,
        "name" to f.name,
        "jobTitle" to f.jobTitle,
        "company" to f.company,
        "mobiles" to f.mobiles,
        "phones" to f.phones,
        "faxes" to f.faxes,
        "emails" to f.emails,
        "websites" to f.websites,
        "address" to f.address,
        "gstin" to f.gstin,
        "business" to f.business,
        "keywords" to f.keywords,
        "extraInfo" to f.extraInfo,
        "notes" to f.notes,
    )

    private fun fieldsFromMap(m: Map<String, Any?>) = CardFields(
        prefix = m["prefix"] as? String ?: "",
        name = m["name"] as? String ?: "",
        jobTitle = m["jobTitle"] as? String ?: "",
        company = m["company"] as? String ?: "",
        mobiles = strList(m["mobiles"]),
        phones = strList(m["phones"]),
        faxes = strList(m["faxes"]),
        emails = strList(m["emails"]),
        websites = strList(m["websites"]),
        address = m["address"] as? String ?: "",
        gstin = m["gstin"] as? String ?: "",
        business = m["business"] as? String ?: "",
        keywords = strList(m["keywords"]),
        extraInfo = m["extraInfo"] as? String ?: "",
        notes = m["notes"] as? String ?: "",
    )

    private fun strList(v: Any?): List<String> = (v as? List<*>).orEmpty().mapNotNull { it as? String }.filter { it.isNotBlank() }

    /** Firestore returns whole numbers as Long; be tolerant of Int/Double too. */
    private fun num(v: Any?): Long = when (v) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        is Number -> v.toLong()
        else -> 0L
    }
}
