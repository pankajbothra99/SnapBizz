package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.OcrLine

/** One scanned business card as stored on the phone. */
data class CardRecord(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val frontImage: String?,
    val backImage: String?,
    val rawLines: List<OcrLine>,
    val fields: CardFields,
    /** Saved to Contacts on THIS phone (never synced: each person saves to their own phone). */
    val savedToContacts: Boolean = false,
    /** "ai" = read by Gemini AI, "phone" = read by the phone's built-in reader */
    val readBy: String = READ_BY_PHONE,
    /** Field keys the user should double-check (shown in orange). See FieldKeys. */
    val uncertain: List<String> = emptyList(),
    /** Name of the team member who scanned it. */
    val scannedBy: String = "",
    /** Login email of the team member who scanned it ("" = scanned before login existed). */
    val ownerEmail: String = "",
    /** "company" = the whole team can see it, "private" = only the owner and admins. */
    val visibility: String = VISIBILITY_COMPANY,
    /** Changed on this phone and not yet sent to the cloud. */
    val cloudDirty: Boolean = false,
    /** Card photos already copied to the cloud. */
    val imagesUploaded: Boolean = false,
    /** The event this card was scanned at ("" = none). */
    val eventId: String = "",
    /** Event name kept on the card, so it shows even for people not in that event. */
    val eventName: String = "",
    /** Sub-groups of the event this card is in (e.g. "Scrap", "Finished products"); several allowed. */
    val subGroupIds: List<String> = emptyList(),
    /** Their names when last saved, so they show even for people not in that event. */
    val subGroupNames: List<String> = emptyList(),
    /** The sub-groups were picked automatically (AI or keywords) and nobody has confirmed them yet. */
    val subGroupsSuggested: Boolean = false,
    /** What the QR code(s) on the card held (contact, website link…), as read. */
    val qrTexts: List<String> = emptyList(),
    /** Short note on what the company does, written from its website. */
    val webNote: String = "",
    /** The page the note was written from. */
    val webNoteUrl: String = "",
    /** When the website was read (0 = not yet; then the app reads it in the background). */
    val webCheckedAt: Long = 0L,
    /** Set by bulk import: id of an existing card that looks like the same person ("" = none). */
    val duplicateOf: String = "",
    /** Front / back photo already copied to this phone's Gallery album (phone-only). */
    val frontInGallery: Boolean = false,
    val backInGallery: Boolean = false,
) {
    val isPrivate: Boolean get() = visibility == VISIBILITY_PRIVATE

    companion object {
        const val READ_BY_AI = "ai"
        const val READ_BY_PHONE = "phone"
        const val VISIBILITY_COMPANY = "company"
        const val VISIBILITY_PRIVATE = "private"
    }
}
