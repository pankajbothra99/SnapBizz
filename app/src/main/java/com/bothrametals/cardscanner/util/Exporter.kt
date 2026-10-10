package com.bothrametals.cardscanner.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bothrametals.cardscanner.data.CardRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Excel-friendly CSV and vCard export of the scanned cards. */
object Exporter {

    private val dateFmt get() = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH)

    fun fileStamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ENGLISH).format(Date())

    fun csv(records: List<CardRecord>): ByteArray {
        val header = listOf(
            "Name", "Prefix", "Designation", "Company", "Mobile 1", "Mobile 2", "Office Phone 1", "Office Phone 2",
            "Fax", "Email 1", "Email 2", "Website", "Address", "GSTIN", "My Notes", "Other Text on Card",
            "Scanned On", "Saved to Contacts",
        )
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { esc(it) }).append("\r\n")
        for (r in records) {
            val f = r.fields
            val row = listOf(
                f.name, f.prefix, f.jobTitle, f.company,
                f.mobiles.getOrElse(0) { "" }, f.mobiles.drop(1).joinToString(" / "),
                f.phones.getOrElse(0) { "" }, f.phones.drop(1).joinToString(" / "),
                f.faxes.joinToString(" / "),
                f.emails.getOrElse(0) { "" }, f.emails.drop(1).joinToString(" / "),
                f.websites.joinToString(" / "), f.address, f.gstin, f.notes, f.extraInfo.replace("\n", " | "),
                dateFmt.format(Date(r.createdAt)), if (r.savedToContacts) "Yes" else "No",
            )
            sb.append(row.joinToString(",") { esc(it) }).append("\r\n")
        }
        // UTF-8 BOM so Excel shows ₹ / Hindi characters correctly
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun vcard(records: List<CardRecord>): ByteArray =
        records.joinToString("") { vcard(it) }.toByteArray(Charsets.UTF_8)

    fun vcard(r: CardRecord): String {
        val f = r.fields
        val words = f.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val last = if (words.size > 1) words.last() else ""
        val first = if (words.size > 1) words.dropLast(1).joinToString(" ") else words.firstOrNull() ?: ""
        val full = listOf(f.prefix, f.name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { f.company }
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        sb.append("N:${v(last)};${v(first)};;${v(f.prefix)};\r\n")
        sb.append("FN:${v(full)}\r\n")
        if (f.company.isNotBlank()) sb.append("ORG:${v(f.company)}\r\n")
        if (f.jobTitle.isNotBlank()) sb.append("TITLE:${v(f.jobTitle)}\r\n")
        f.mobiles.forEach { sb.append("TEL;TYPE=CELL:${v(it)}\r\n") }
        f.phones.forEach { sb.append("TEL;TYPE=WORK,VOICE:${v(it)}\r\n") }
        f.faxes.forEach { sb.append("TEL;TYPE=WORK,FAX:${v(it)}\r\n") }
        f.emails.forEach { sb.append("EMAIL;TYPE=INTERNET,WORK:${v(it)}\r\n") }
        f.websites.forEach { sb.append("URL:${v(it)}\r\n") }
        if (f.address.isNotBlank()) sb.append("ADR;TYPE=WORK:;;${v(f.address)};;;;\r\n")
        val note = ContactSaver.buildNote(f, r.createdAt)
        if (note.isNotBlank()) sb.append("NOTE:${v(note)}\r\n")
        sb.append("END:VCARD\r\n")
        return sb.toString()
    }

    fun writeTemp(context: Context, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    fun shareIntent(context: Context, file: File, mime: String, title: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun v(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\r", "").replace("\n", "\\n")
}
