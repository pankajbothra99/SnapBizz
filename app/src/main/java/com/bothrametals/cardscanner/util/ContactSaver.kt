package com.bothrametals.cardscanner.util

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import com.bothrametals.cardscanner.data.CardRecord
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
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                put(StructuredPostal.FORMATTED_ADDRESS, f.address)
                put(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
            }
        }
        val note = buildNote(f, record.createdAt)
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

        return Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            val fullName = listOf(f.prefix, f.name).filter { it.isNotBlank() }.joinToString(" ")
            if (fullName.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, fullName)
            if (f.company.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, f.company)
            if (f.jobTitle.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, f.jobTitle)
            putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
            // Return to our app straight after the user taps Save
            putExtra("finishActivityOnSaveCompleted", true)
        }
    }

    fun buildNote(f: CardFields, scannedAt: Long): String {
        val parts = ArrayList<String>()
        if (f.notes.isNotBlank()) parts += f.notes.trim()
        if (f.gstin.isNotBlank()) parts += "GSTIN: ${f.gstin}"
        if (f.extraInfo.isNotBlank()) parts += f.extraInfo.trim()
        parts += "Card scanned on " + SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(scannedAt))
        return parts.joinToString("\n")
    }

    private fun phone(number: String, type: Int) = ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
        put(Phone.NUMBER, number)
        put(Phone.TYPE, type)
    }
}
