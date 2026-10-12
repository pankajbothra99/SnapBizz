package com.bothrametals.cardscanner.util

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.parser.AddressSplitter
import com.bothrametals.cardscanner.parser.CardFields
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opens the phone's own "New contact" screen with everything pre-filled.
 * The phone's Contacts app saves it to the user's default account (usually Google), so it syncs
 * across devices. No contacts permission is needed for this.
 */
object ContactSaver {

    fun buildInsertIntent(record: CardRecord): Intent {
        val f = record.fields
        val rows = ArrayList<ContentValues>()

        f.mobiles.forEach { rows += phone(it, Phone.TYPE_MOBILE) }
        f.phones.forEach { rows += phone(it, Phone.TYPE_WORK) }
        f.faxes.forEach { rows += phone(it, Phone.TYPE_FAX_WORK) }
        f.emails.forEach {
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                put(Email.ADDRESS, it)
                put(Email.TYPE, Email.TYPE_WORK)
            }
        }
        f.websites.forEach {
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                put(Website.URL, it)
                put(Website.TYPE, Website.TYPE_WORK)
            }
        }
        if (f.address.isNotBlank()) {
            // Many Contacts apps (Google, Samsung) only show an address given in separate parts,
            // so send street / city / state / PIN / country as well as the full line.
            val a = AddressSplitter.split(f.address)
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                put(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
                put(StructuredPostal.STREET, a.street.ifBlank { f.address })
                if (a.city.isNotBlank()) put(StructuredPostal.CITY, a.city)
                if (a.state.isNotBlank()) put(StructuredPostal.REGION, a.state)
                if (a.postcode.isNotBlank()) put(StructuredPostal.POSTCODE, a.postcode)
                if (a.country.isNotBlank()) put(StructuredPostal.COUNTRY, a.country)
                put(StructuredPostal.FORMATTED_ADDRESS, f.address)
            }
        }
        val note = buildNote(f, record.createdAt, record.scannedBy, record.eventName, record.subGroupNames, record.webNote)
        if (note.isNotBlank()) {
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                put(Note.NOTE, note)
            }
        }
        record.frontImage?.let { path -> ImageUtils.contactPhoto(File(path)) }?.let { bytes ->
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                put(Photo.PHOTO, bytes)
            }
        }

        // Company, designation and "what they do" (as the department, which every Contacts app can search)
        if (f.company.isNotBlank() || f.jobTitle.isNotBlank() || f.business.isNotBlank()) {
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                put(Organization.TYPE, Organization.TYPE_WORK)
                if (f.company.isNotBlank()) put(Organization.COMPANY, f.company)
                if (f.jobTitle.isNotBlank()) put(Organization.TITLE, f.jobTitle)
                if (f.business.isNotBlank()) put(Organization.DEPARTMENT, f.business.take(80))
            }
        }

        return Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            val fullName = listOf(f.prefix, f.name).filter { it.isNotBlank() }.joinToString(" ")
            if (fullName.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, fullName)
            putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
            // Return to our app straight after the user taps Save
            putExtra("finishActivityOnSaveCompleted", true)
        }
    }

    fun buildNote(
        f: CardFields,
        scannedAt: Long,
        scannedBy: String = "",
        eventName: String = "",
        subGroups: List<String> = emptyList(),
        webNote: String = "",
    ): String {
        val parts = ArrayList<String>()
        if (f.notes.isNotBlank()) parts += f.notes.trim()
        // These lines make the contact easy to find later by searching a product or service
        if (f.business.isNotBlank()) parts += "What they do: ${f.business.trim()}"
        if (webNote.isNotBlank()) parts += "From website: ${webNote.trim()}"
        if (f.keywords.isNotEmpty()) parts += "Keywords: " + f.keywords.joinToString(", ")
        if (eventName.isNotBlank()) parts += "Met at: $eventName"
        if (subGroups.isNotEmpty()) parts += "Sub-group: " + subGroups.joinToString(", ")
        if (f.gstin.isNotBlank()) parts += "GSTIN: ${f.gstin}"
        if (f.extraInfo.isNotBlank()) parts += f.extraInfo.trim()
        parts += "Card scanned on " + SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(scannedAt)) +
            (if (scannedBy.isNotBlank()) " by $scannedBy" else "")
        return parts.joinToString("\n")
    }

    private fun phone(number: String, type: Int) = ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
        put(Phone.NUMBER, number)
        put(Phone.TYPE, type)
    }
}
