package com.bothrametals.cardscanner.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.CardRepository
import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.CardParser
import com.bothrametals.cardscanner.util.ContactSaver
import com.bothrametals.cardscanner.util.Exporter
import com.bothrametals.cardscanner.util.ImageUtils
import com.bothrametals.cardscanner.util.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Review : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CardRepository(app)
    private val ocr = OcrEngine()

    var cards by mutableStateOf<List<CardRecord>>(emptyList())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    /** The card open on the review screen (edits live here until saved). */
    var editing by mutableStateOf<CardRecord?>(null)
        private set
    var busyMessage by mutableStateOf<String?>(null)
        private set
    var duplicateWarning by mutableStateOf<String?>(null)
        private set
    /** One-off message shown as a snackbar. */
    var toast by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    init {
        viewModelScope.launch {
            cards = withContext(Dispatchers.IO) { repo.loadAll() }
        }
    }

    val filteredCards: List<CardRecord>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return cards
            val digits = q.filter { it.isDigit() }
            return cards.filter { r ->
                val text = r.fields.searchText()
                text.contains(q) || (digits.length >= 4 &&
                    (r.fields.mobiles + r.fields.phones).any { it.filter { c -> c.isDigit() }.contains(digits) })
            }
        }

    // ------------------------------------------------------------------ scanning

    /** New card from 1–2 images (first = front, second = back). */
    fun processNewScan(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyMessage = "Reading card…"
            try {
                val record = withContext(Dispatchers.IO) {
                    val id = UUID.randomUUID().toString().take(13)
                    val files = uris.take(2).mapIndexed { i, uri ->
                        repo.importImage(uri, "${id}_${if (i == 0) "front" else "back"}.jpg")
                    }
                    val lines = files.mapIndexed { side, file ->
                        val bmp = ImageUtils.load(file) ?: throw IllegalStateException("Could not read image")
                        ocr.read(bmp, side)
                    }.flatten()
                    val now = System.currentTimeMillis()
                    CardRecord(
                        id = id,
                        createdAt = now,
                        updatedAt = now,
                        frontImage = files.getOrNull(0)?.absolutePath,
                        backImage = files.getOrNull(1)?.absolutePath,
                        rawLines = lines,
                        fields = CardParser.parse(lines),
                    ).also { repo.save(it) }
                }
                cards = listOf(record) + cards
                duplicateWarning = findDuplicate(record)
                editing = record
                screen = Screen.Review
                if (record.rawLines.isEmpty()) toast = "No text found. Try again in better light, holding the phone steady."
            } catch (e: Exception) {
                toast = "Could not read the card: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busyMessage = null
            }
        }
    }

    /** Adds a back side to the card being edited. Only fills fields that are still empty. */
    fun addBackSide(uri: Uri) {
        val current = editing ?: return
        viewModelScope.launch {
            busyMessage = "Reading back side…"
            try {
                val updated = withContext(Dispatchers.IO) {
                    val file = repo.importImage(uri, "${current.id}_back.jpg")
                    val bmp = ImageUtils.load(file) ?: throw IllegalStateException("Could not read image")
                    val backLines = ocr.read(bmp, 1)
                    val allLines = current.rawLines.filter { it.side == 0 } + backLines
                    val reparsed = CardParser.parse(allLines)
                    current.copy(
                        backImage = file.absolutePath,
                        rawLines = allLines,
                        fields = fillBlanks(current.fields, reparsed),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                editing = updated
                persist(updated)
            } catch (e: Exception) {
                toast = "Could not read the back side: ${e.message}"
            } finally {
                busyMessage = null
            }
        }
    }

    private fun fillBlanks(old: CardFields, new: CardFields) = old.copy(
        prefix = old.prefix.ifBlank { new.prefix },
        name = old.name.ifBlank { new.name },
        jobTitle = old.jobTitle.ifBlank { new.jobTitle },
        company = old.company.ifBlank { new.company },
        mobiles = (old.mobiles + new.mobiles).distinct(),
        phones = (old.phones + new.phones).distinct(),
        faxes = (old.faxes + new.faxes).distinct(),
        emails = (old.emails + new.emails).distinct(),
        websites = (old.websites + new.websites).distinct(),
        address = old.address.ifBlank { new.address },
        gstin = old.gstin.ifBlank { new.gstin },
        extraInfo = listOf(old.extraInfo, new.extraInfo).filter { it.isNotBlank() }.distinct().joinToString("\n"),
    )

    private fun findDuplicate(r: CardRecord): String? {
        val digits = r.fields.mobiles.map { it.filter(Char::isDigit).takeLast(10) }.toSet()
        val mails = r.fields.emails.map { it.lowercase() }.toSet()
        val match = cards.firstOrNull { other ->
            other.id != r.id && (
                other.fields.mobiles.any { it.filter(Char::isDigit).takeLast(10) in digits } ||
                    other.fields.emails.any { it.lowercase() in mails })
        } ?: return null
        return "You already scanned ${match.fields.displayName} with the same mobile/email."
    }

    // ------------------------------------------------------------------ editing

    fun open(record: CardRecord) {
        editing = record
        duplicateWarning = null
        screen = Screen.Review
    }

    fun updateFields(transform: (CardFields) -> CardFields) {
        val e = editing ?: return
        editing = e.copy(fields = transform(e.fields), updatedAt = System.currentTimeMillis())
    }

    fun closeReview() {
        editing?.let { persist(clean(it)) }
        editing = null
        duplicateWarning = null
        screen = Screen.Home
    }

    fun deleteEditing() {
        val e = editing ?: return
        viewModelScope.launch(Dispatchers.IO) { repo.delete(e) }
        cards = cards.filterNot { it.id == e.id }
        editing = null
        screen = Screen.Home
        toast = "Card deleted"
    }

    private fun clean(r: CardRecord) = r.copy(
        fields = r.fields.copy(
            mobiles = r.fields.mobiles.map { it.trim() }.filter { it.isNotBlank() },
            phones = r.fields.phones.map { it.trim() }.filter { it.isNotBlank() },
            faxes = r.fields.faxes.map { it.trim() }.filter { it.isNotBlank() },
            emails = r.fields.emails.map { it.trim() }.filter { it.isNotBlank() },
            websites = r.fields.websites.map { it.trim() }.filter { it.isNotBlank() },
        )
    )

    private fun persist(r: CardRecord) {
        cards = cards.map { if (it.id == r.id) r else it }
        viewModelScope.launch(Dispatchers.IO) { repo.save(r) }
    }

    // ------------------------------------------------------------------ contacts

    fun contactIntent(): Intent? {
        val e = editing ?: return null
        val cleaned = clean(e)
        editing = cleaned
        persist(cleaned)
        return ContactSaver.buildInsertIntent(cleaned)
    }

    fun onContactScreenClosed(saved: Boolean) {
        val e = editing ?: return
        if (saved) {
            val updated = e.copy(savedToContacts = true)
            editing = updated
            persist(updated)
            toast = "Saved to Contacts ✓"
        }
    }

    // ------------------------------------------------------------------ export

    fun csvBytes(): ByteArray = Exporter.csv(cards)

    fun csvFileName() = "BusinessCards_${Exporter.fileStamp()}.csv"

    fun writeToUri(uri: Uri, bytes: ByteArray) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            toast = if (ok) "Saved ${cards.size} cards" else "Could not save the file"
        }
    }

    fun shareCsv(): Intent {
        val ctx = getApplication<Application>()
        val file = Exporter.writeTemp(ctx, csvFileName(), csvBytes())
        return Exporter.shareIntent(ctx, file, "text/csv", "Business cards (Excel)")
    }

    fun shareAllVcf(): Intent {
        val ctx = getApplication<Application>()
        val file = Exporter.writeTemp(ctx, "BusinessCards_${Exporter.fileStamp()}.vcf", Exporter.vcard(cards))
        return Exporter.shareIntent(ctx, file, "text/x-vcard", "Business cards (vCard)")
    }

    fun shareEditingVcf(): Intent? {
        val e = editing ?: return null
        val ctx = getApplication<Application>()
        val safe = e.fields.displayName.replace(Regex("[^A-Za-z0-9]+"), "_").take(40)
        val file = Exporter.writeTemp(ctx, "$safe.vcf", Exporter.vcard(clean(e)).toByteArray())
        return Exporter.shareIntent(ctx, file, "text/x-vcard", "Share contact")
    }

    fun imageFile(path: String?): File? = path?.let { File(it) }?.takeIf { it.exists() }

    override fun onCleared() {
        ocr.close()
        super.onCleared()
    }
}
