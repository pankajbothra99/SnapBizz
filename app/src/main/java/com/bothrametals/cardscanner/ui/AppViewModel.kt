package com.bothrametals.cardscanner.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bothrametals.cardscanner.ai.AiException
import com.bothrametals.cardscanner.ai.AiPostProcess
import com.bothrametals.cardscanner.ai.CardImage
import com.bothrametals.cardscanner.ai.FieldKeys
import com.bothrametals.cardscanner.ai.GeminiClient
import com.bothrametals.cardscanner.ai.GeminiProtocol
import com.bothrametals.cardscanner.cloud.CloudException
import com.bothrametals.cardscanner.cloud.CloudMapper
import com.bothrametals.cardscanner.cloud.CloudService
import com.bothrametals.cardscanner.data.AppSettings
import com.bothrametals.cardscanner.data.BulkPairing
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.CardRepository
import com.bothrametals.cardscanner.data.Duplicates
import com.bothrametals.cardscanner.data.EventGroup
import com.bothrametals.cardscanner.data.EventStore
import com.bothrametals.cardscanner.data.SubGroup
import com.bothrametals.cardscanner.data.SubGroups
import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.CardParser
import com.bothrametals.cardscanner.parser.CrossCheck
import com.bothrametals.cardscanner.parser.KeywordExtractor
import com.bothrametals.cardscanner.parser.OcrLine
import com.bothrametals.cardscanner.parser.QrMerge
import com.bothrametals.cardscanner.parser.QrParser
import com.bothrametals.cardscanner.util.ContactSaver
import com.bothrametals.cardscanner.util.Exporter
import com.bothrametals.cardscanner.util.GalleryNames
import com.bothrametals.cardscanner.util.GallerySaver
import com.bothrametals.cardscanner.util.ImageUtils
import com.bothrametals.cardscanner.util.OcrEngine
import com.bothrametals.cardscanner.util.QrReader
import com.bothrametals.cardscanner.web.WebReader
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Review : Screen
    data object Settings : Screen
    data object Team : Screen
    data object Event : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CardRepository(app)
    private val ocr = OcrEngine()
    private val qrReader = QrReader()
    private val settings = AppSettings(app)
    private val cloud = CloudService(app)
    private val eventStore = EventStore(app)

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
    /** Why the open card was read on the phone instead of by AI (shown on the review screen). */
    var aiNote by mutableStateOf<String?>(null)
        private set
    /** One-off message shown as a snackbar. */
    var toast by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    /** Bumped when a card's details are replaced by a new reading, so text boxes refresh. */
    var refreshKey by mutableStateOf(0)
        private set

    // Settings mirrored as Compose state
    var aiKey by mutableStateOf(settings.apiKey)
        private set
    var aiEnabled by mutableStateOf(settings.aiEnabled)
        private set
    var userName by mutableStateOf(settings.userName)
        private set
    var modelsText by mutableStateOf(settings.models.joinToString(", "))
        private set
    var aiReadsToday by mutableStateOf(settings.aiReadsToday)
        private set
    var testStatus by mutableStateOf<String?>(null)
        private set

    val aiReady: Boolean get() = aiEnabled && aiKey.isNotBlank()

    // Login / team / backup state
    var auth by mutableStateOf(initialAuth())
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
    var authInfo by mutableStateOf<String?>(null)
    var syncStatus by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    /** Home screen tab: false = all cards, true = only cards I scanned. */
    var showMineOnly by mutableStateOf(false)
    var members by mutableStateOf<List<CloudService.Member>>(emptyList())
        private set
    var signOutWarning by mutableStateOf(false)
    var aiKeyFromTeam by mutableStateOf(settings.aiKeyFromTeam)
        private set
    var saveToGallery by mutableStateOf(settings.saveToGallery)
        private set
    // Events
    var events by mutableStateOf(eventStore.load())
        private set
    /** The event new scans go into, and whose cards the home screen shows ("" = all cards). */
    var currentEventId by mutableStateOf(settings.currentEventId)
        private set
    /** Set right after a scan when the same person was scanned before. */
    var duplicateMatch by mutableStateOf<Duplicates.Match?>(null)
        private set
    // Bulk import from the Gallery
    class BatchSummary(val added: Int, val duplicates: Int, val byPhone: Int, val note: String?, val stopped: Boolean)
    var batchSummary by mutableStateOf<BatchSummary?>(null)
    /** Home screen shows only cards flagged as possible duplicates. */
    var showDuplicatesOnly by mutableStateOf(false)
    /** Home screen shows only cards not yet saved to this phone's Contacts. */
    var showNotSavedOnly by mutableStateOf(false)
    /** Home screen sub-group filter inside the selected event ("" = all, SubGroups.NOT_SORTED = in none). */
    var subGroupFilter by mutableStateOf("")
    /** The welcome pages have been seen on this phone. */
    var onboardingDone by mutableStateOf(settings.onboardingDone)
        private set

    fun finishOnboarding() {
        settings.onboardingDone = true
        onboardingDone = true
    }
    /** The busy dialog shows a Stop button (bulk import). */
    var busyCancelable by mutableStateOf(false)
        private set
    private var stopRequested = false
    private var lastAiFailure: AiException.Kind? = null

    /** The event open on the "Manage event" screen. */
    var managingEventId by mutableStateOf("")
        private set

    val openEvents: List<EventGroup> get() = events.filter { !it.closed }.sortedByDescending { it.createdAt }
    val pastEvents: List<EventGroup> get() = events.filter { it.closed }.sortedByDescending { it.createdAt }
    val currentEvent: EventGroup? get() = events.firstOrNull { it.id == currentEventId }
    val managingEvent: EventGroup? get() = events.firstOrNull { it.id == managingEventId }
    fun cardsIn(eventId: String) = cards.filter { it.eventId == eventId }

    private var team: CloudService.Team? = null
    private var pushJob: Job? = null
    private var lastSyncRun = 0L

    val member: AuthState.Member? get() = auth as? AuthState.Member
    val cloudOn: Boolean get() = member != null
    val myEmail: String get() = member?.email.orEmpty()
    val isAdmin: Boolean get() = member?.isAdmin == true

    /** May the current user change this card? (Before login is set up, everything is editable.) */
    fun canEdit(r: CardRecord): Boolean = member == null || CloudMapper.canEdit(r, myEmail, isAdmin)

    init {
        viewModelScope.launch {
            cards = withContext(Dispatchers.IO) { repo.loadAll() }
            queueRecentWebNotes()
            if (cloud.isConfigured && cloud.user != null) refreshMembership()
        }
    }

    private fun initialAuth(): AuthState {
        if (!cloud.isConfigured) return AuthState.LocalOnly
        val email = cloud.email ?: return AuthState.SignedOut
        val parts = settings.cachedMembership.split('|')
        return if (parts.size >= 3 && parts[0] == email) {
            AuthState.Member(email, settings.userName.ifBlank { email.substringBefore('@') }, parts[1] == "true", parts[2])
        } else AuthState.Starting
    }

    /** Cards in the selected event and tab, before search and quick filters (used for the counts on Home). */
    val scopeCards: List<CardRecord>
        get() {
            val inEvent = if (currentEventId.isNotBlank()) cards.filter { it.eventId == currentEventId } else cards
            return if (showMineOnly && cloudOn) inEvent.filter { it.ownerEmail.isBlank() || it.ownerEmail == myEmail } else inEvent
        }

    val filteredCards: List<CardRecord>
        get() {
            val q = query.trim().lowercase()
            var base = scopeCards
            if (showDuplicatesOnly) base = base.filter { it.duplicateOf.isNotBlank() }
            if (showNotSavedOnly) base = base.filter { !it.savedToContacts }
            val ev = currentEvent
            if (ev != null && subGroupFilter.isNotBlank() &&
                (subGroupFilter == SubGroups.NOT_SORTED || ev.subGroups.any { it.id == subGroupFilter })
            ) base = base.filter { SubGroups.matches(it, subGroupFilter, ev) }
            if (q.isEmpty()) return base
            val digits = q.filter { it.isDigit() }
            return base.filter { r ->
                val text = r.fields.searchText() + " " + r.scannedBy.lowercase() + " " +
                    SubGroups.namesFor(r, eventOf(r)).joinToString(" ").lowercase() + " " + r.webNote.lowercase()
                text.contains(q) || (digits.length >= 4 &&
                    (r.fields.mobiles + r.fields.phones).any { it.filter { c -> c.isDigit() }.contains(digits) })
            }
        }

    /** Cards read offline (by the phone) that are not yet saved to Contacts. */
    val offlineCards: List<CardRecord>
        get() = cards.filter { it.readBy == CardRecord.READ_BY_PHONE && !it.savedToContacts && it.frontImage != null && canEdit(it) }

    // ------------------------------------------------------------------ reading

    /** [aiSubGroups] = sub-group names the AI picked (null = not read by AI). */
    private class ReadOutcome(
        val fields: CardFields,
        val uncertain: Set<String>,
        val readBy: String,
        val note: String?,
        val aiSubGroups: List<String>? = null,
    )

    /** Reads with AI if possible; otherwise uses the phone's reading. Never fails. */
    private suspend fun readCard(
        files: List<File>,
        lines: List<OcrLine>,
        notes: String,
        subGroups: List<String> = emptyList(),
        qrTexts: List<String> = emptyList(),
    ): ReadOutcome {
        val local = CardParser.parse(lines)
        var note: String? = null
        if (aiReady) {
            busyMessage = "Reading with AI…"
            val (ai, err) = tryAi(files, lines, notes, local, subGroups, qrTexts)
            if (ai != null) return ai
            note = err
        }
        val checked = CrossCheck.apply(local.copy(notes = notes))
        return withQr(ReadOutcome(checked.fields, checked.uncertain, CardRecord.READ_BY_PHONE, note), qrTexts)
    }

    /** Combines the reading with the card's QR code: the QR is exact, so it fixes misreads and adds what's missing. */
    private fun withQr(o: ReadOutcome, qrTexts: List<String>): ReadOutcome {
        if (qrTexts.isEmpty()) return o
        val r = QrMerge.merge(o.fields, QrParser.parse(qrTexts))
        return ReadOutcome(r.fields, o.uncertain + r.flagged, o.readBy, o.note, o.aiSubGroups)
    }

    /** A QR code that links to a contact file (.vcf): download it, so it counts like a contact QR. */
    private fun withVcfDownloads(qr: List<String>): List<String> {
        val links = QrParser.parse(qr).vcfLinks
        if (links.isEmpty() || !isOnline()) return qr
        val files = links.take(2).mapNotNull { link ->
            WebReader.download(link, maxBytes = 200_000, timeoutMs = 6_000)?.html?.trim()
                ?.takeIf { it.startsWith("BEGIN:VCARD", ignoreCase = true) }
        }
        return (qr + files).distinct()
    }

    /** AI only. Returns (result, null) or (null, reason it didn't work). */
    private suspend fun tryAi(
        files: List<File>,
        lines: List<OcrLine>,
        notes: String,
        local: CardFields,
        subGroups: List<String> = emptyList(),
        qrTexts: List<String> = emptyList(),
    ): Pair<ReadOutcome?, String?> {
        lastAiFailure = null
        if (aiKey.isBlank()) {
            lastAiFailure = AiException.Kind.AUTH
            return null to "AI reading is not set up yet. Add your free key in Settings."
        }
        if (!isOnline()) {
            lastAiFailure = AiException.Kind.OFFLINE
            return null to "No internet, so this card was read on the phone. Tap \"Read with AI\" when you're online."
        }
        return try {
            val result = withContext(Dispatchers.IO) {
                val images = files.mapIndexedNotNull { i, f ->
                    ImageUtils.base64Jpeg(f)?.let { CardImage(if (i == 0) "Front of card:" else "Back of card:", it) }
                }
                val hint = lines.joinToString("\n") { it.text } +
                    if (qrTexts.isEmpty()) "" else "\n\nQR code on the card contains:\n" + qrTexts.joinToString("\n").take(1500)
                GeminiClient(aiKey, settings.models).readCard(images, hint, subGroups)
            }
            settings.countAiRead()
            aiReadsToday = settings.aiReadsToday
            val processed = AiPostProcess.toFields(result.card)
            val (merged, filled) = fillFromPhone(processed.fields, local)
            val checked = CrossCheck.apply(merged.copy(notes = notes), processed.uncertain + filled)
            withQr(ReadOutcome(checked.fields, checked.uncertain, CardRecord.READ_BY_AI, null, result.card.subGroups), qrTexts) to null
        } catch (e: AiException) {
            lastAiFailure = e.kind
            null to (e.message ?: "The AI could not read this card")
        } catch (e: Exception) {
            lastAiFailure = AiException.Kind.OTHER
            null to "The AI could not read this card (${e.javaClass.simpleName})"
        }
    }

    /** If the AI left a field empty but the phone found something, use it and mark it "please check". */
    private fun fillFromPhone(ai: CardFields, phone: CardFields): Pair<CardFields, Set<String>> {
        val flags = HashSet<String>()
        fun s(a: String, p: String, key: String) = if (a.isBlank() && p.isNotBlank()) { flags += key; p } else a
        fun l(a: List<String>, p: List<String>, key: String) = if (a.isEmpty() && p.isNotEmpty()) { flags += key; p } else a
        val f = ai.copy(
            name = s(ai.name, phone.name, FieldKeys.NAME),
            company = s(ai.company, phone.company, FieldKeys.COMPANY),
            mobiles = l(ai.mobiles, phone.mobiles, FieldKeys.MOBILES),
            emails = l(ai.emails, phone.emails, FieldKeys.EMAILS),
            address = s(ai.address, phone.address, FieldKeys.ADDRESS),
            business = ai.business.ifBlank { phone.business },
            keywords = ai.keywords.ifEmpty { phone.keywords },
        )
        return f to flags
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ------------------------------------------------------------------ scanning

    /** New card from 1–2 images (first = front, second = back). */
    fun processNewScan(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyMessage = "Reading card…"
            try {
                val id = UUID.randomUUID().toString().take(13)
                val (files, lines, qr) = withContext(Dispatchers.IO) {
                    val files = uris.take(2).mapIndexed { i, uri ->
                        repo.importImage(uri, "${id}_${if (i == 0) "front" else "back"}.jpg")
                    }
                    val qr = ArrayList<String>()
                    val lines = files.mapIndexed { side, file ->
                        val bmp = ImageUtils.load(file) ?: throw IllegalStateException("Could not read image")
                        qr += qrReader.read(bmp)
                        ocr.read(bmp, side)
                    }.flatten()
                    Triple(files, lines, withVcfDownloads(qr.distinct()))
                }
                val outcome = readCard(files, lines, notes = "", subGroups = subGroupNamesOf(currentEvent), qrTexts = qr)
                val now = System.currentTimeMillis()
                val record = withSuggestedSubGroups(CardRecord(
                    id = id,
                    createdAt = now,
                    updatedAt = now,
                    frontImage = files.getOrNull(0)?.absolutePath,
                    backImage = files.getOrNull(1)?.absolutePath,
                    rawLines = lines,
                    fields = outcome.fields,
                    readBy = outcome.readBy,
                    uncertain = outcome.uncertain.toList(),
                    scannedBy = settings.userName.ifBlank { member?.name.orEmpty() },
                    ownerEmail = myEmail,
                    cloudDirty = cloudOn,
                    eventId = currentEvent?.id.orEmpty(),
                    eventName = currentEvent?.name.orEmpty(),
                    qrTexts = qr,
                ), outcome.aiSubGroups)
                val stored = withContext(Dispatchers.IO) { copyToGallery(record).also { repo.save(it) } }
                val dup = Duplicates.find(stored, cards)
                cards = listOf(stored) + cards
                duplicateMatch = dup
                if (dup == null) schedulePush() // otherwise wait for the user's choice
                duplicateWarning = null
                aiNote = outcome.note
                editing = stored
                screen = Screen.Review
                queueWebNotes(listOf(stored)) // reads their website while the card is being checked
                if (record.rawLines.isEmpty() && outcome.readBy == CardRecord.READ_BY_PHONE) {
                    toast = "No text found. Try again in better light, holding the phone steady."
                }
            } catch (e: Exception) {
                toast = "Could not read the card: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busyMessage = null
            }
        }
    }

    // ------------------------------------------------------------------ bulk import from the Gallery

    private class ImportedPhoto(val file: File, val lines: List<OcrLine>, val hasContact: Boolean, val takenAt: Long?, val qr: List<String>)

    fun stopBatch() {
        stopRequested = true
        busyMessage = "Stopping after this card…"
    }

    /**
     * Imports many card photos at once. Back sides (photos with no number or email) are joined to
     * the neighbouring card photo. With 1 card it opens the card like a normal scan; with more it
     * shows a summary. Possible duplicates get a badge instead of a question for each one.
     */
    fun importFromGallery(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            stopRequested = false
            busyCancelable = true
            busyMessage = "Opening photos…"
            val created = ArrayList<CardRecord>()
            var byPhone = 0
            var dupCount = 0
            var aiOff: String? = null
            var leftover: List<File> = emptyList()
            try {
                // 1. Order by the time the photos were taken, copy them in and read the text on the phone
                val ordered = withContext(Dispatchers.IO) { orderByDateTaken(uris) }
                val photos = ArrayList<ImportedPhoto>()
                for ((i, item) in ordered.withIndex()) {
                    if (stopRequested) break
                    busyMessage = "Reading photos… ${i + 1} of ${ordered.size}"
                    val photo = withContext(Dispatchers.IO) {
                        runCatching {
                            val file = repo.importImage(item.first, "imp_${UUID.randomUUID().toString().take(12)}.jpg")
                            val bmp = ImageUtils.load(file) ?: return@runCatching null
                            val lines = ocr.read(bmp, 0)
                            val qr = qrReader.read(bmp)
                            val f = CardParser.parse(lines)
                            ImportedPhoto(file, lines, (f.mobiles + f.phones + f.emails).isNotEmpty(), item.second, qr)
                        }.getOrNull()
                    } ?: continue
                    photos += photo
                }

                // 2. Work out which photos are the back of which card
                val groups = BulkPairing.pair(photos.map { BulkPairing.Photo(it.hasContact, it.takenAt) })
                val used = HashSet<Int>()

                // 3. Read each card (AI when possible) and save it
                var useAi = aiReady
                val sgNames = subGroupNamesOf(currentEvent)
                for ((gi, g) in groups.withIndex()) {
                    if (stopRequested) break
                    busyMessage = "Reading card ${gi + 1} of ${groups.size}" + if (useAi) " with AI…" else "…"
                    used += g
                    val id = UUID.randomUUID().toString().take(13)
                    val front = photos[g[0]]
                    val back = g.getOrNull(1)?.let { photos[it] }
                    val frontFile = withContext(Dispatchers.IO) { repo.renameImage(front.file, "${id}_front.jpg") }
                    val backFile = back?.let { b -> withContext(Dispatchers.IO) { repo.renameImage(b.file, "${id}_back.jpg") } }
                    val lines = front.lines + (back?.lines?.map { it.copy(side = 1) } ?: emptyList())
                    val files = listOfNotNull(frontFile, backFile)
                    val local = CardParser.parse(lines)
                    val qr = withContext(Dispatchers.IO) { withVcfDownloads((front.qr + (back?.qr ?: emptyList())).distinct()) }

                    var aiOutcome: ReadOutcome? = null
                    if (useAi) {
                        var result = tryAi(files, lines, "", local, sgNames, qr)
                        if (result.first == null && lastAiFailure == AiException.Kind.QUOTA) {
                            // Free per-minute limit: wait a little and try once more
                            busyMessage = "Free AI limit reached, waiting a moment…"
                            delay(20_000)
                            result = tryAi(files, lines, "", local, sgNames, qr)
                        }
                        aiOutcome = result.first
                        if (aiOutcome == null && lastAiFailure != AiException.Kind.OTHER) {
                            useAi = false // daily limit / no internet: read the rest on the phone
                            aiOff = result.second
                        }
                    }
                    val outcome: ReadOutcome = aiOutcome ?: run {
                        byPhone++
                        val checked = CrossCheck.apply(local)
                        withQr(ReadOutcome(checked.fields, checked.uncertain, CardRecord.READ_BY_PHONE, null), qr)
                    }

                    val now = System.currentTimeMillis()
                    var record = withSuggestedSubGroups(CardRecord(
                        id = id,
                        createdAt = now,
                        updatedAt = now,
                        frontImage = frontFile.absolutePath,
                        backImage = backFile?.absolutePath,
                        rawLines = lines,
                        fields = outcome.fields,
                        readBy = outcome.readBy,
                        uncertain = outcome.uncertain.toList(),
                        scannedBy = settings.userName.ifBlank { member?.name.orEmpty() },
                        ownerEmail = myEmail,
                        cloudDirty = cloudOn,
                        eventId = currentEvent?.id.orEmpty(),
                        eventName = currentEvent?.name.orEmpty(),
                        qrTexts = qr,
                    ), outcome.aiSubGroups)
                    val dup = Duplicates.find(record, cards)
                    if (dup != null && groups.size > 1) {
                        record = record.copy(duplicateOf = dup.card.id)
                        dupCount++
                    }
                    val stored = withContext(Dispatchers.IO) { copyToGallery(record).also { repo.save(it) } }
                    cards = listOf(stored) + cards
                    created += stored
                    if (groups.size == 1) duplicateMatch = dup
                }
                leftover = photos.indices.filter { it !in used }.map { photos[it].file }
            } catch (e: Exception) {
                toast = "Import problem: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busyMessage = null
                busyCancelable = false
                withContext(Dispatchers.IO) { leftover.forEach { it.delete() } }
            }
            if (created.isEmpty()) {
                if (toast == null) toast = "No cards were imported"
                return@launch
            }
            queueWebNotes(created)
            if (created.size == 1 && !stopRequested) {
                // Just one card: open it like a normal scan
                editing = created.first()
                aiNote = if (created.first().readBy == CardRecord.READ_BY_PHONE) aiOff else null
                screen = Screen.Review
                if (duplicateMatch == null) schedulePush()
            } else {
                duplicateMatch = null
                schedulePush()
                batchSummary = BatchSummary(created.size, dupCount, byPhone, aiOff, stopRequested)
            }
        }
    }

    /** Sorts picked photos by the time they were taken (keeps the picked order if that's unknown). */
    private fun orderByDateTaken(uris: List<Uri>): List<Pair<Uri, Long?>> {
        val resolver = getApplication<Application>().contentResolver
        val withTime = uris.map { uri ->
            val t = runCatching {
                resolver.query(uri, arrayOf("datetaken"), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
                }
            }.getOrNull()
            uri to t
        }
        return if (withTime.all { it.second != null }) withTime.sortedBy { it.second } else withTime
    }

    /** The card a flagged card may be a duplicate of. */
    fun duplicateOfCard(r: CardRecord): CardRecord? = r.duplicateOf.takeIf { it.isNotBlank() }?.let { id -> cards.firstOrNull { it.id == id } }

    fun clearDuplicateFlag() {
        val e = editing ?: return
        editing = e.copy(duplicateOf = "", updatedAt = System.currentTimeMillis())
        toast = "Marked as not a duplicate"
    }

    /** From a flagged card, jump to the card it may duplicate. */
    fun openOther(other: CardRecord) {
        closeReview()
        open(other)
    }

    /** Adds a back side to the card being edited. Only fills fields that are still empty. */
    fun addBackSide(uri: Uri) {
        val current = editing ?: return
        viewModelScope.launch {
            busyMessage = "Reading back side…"
            try {
                val (backFile, allLines, qr) = withContext(Dispatchers.IO) {
                    val file = repo.importImage(uri, "${current.id}_back.jpg")
                    val bmp = ImageUtils.load(file) ?: throw IllegalStateException("Could not read image")
                    val qr = withVcfDownloads((current.qrTexts + qrReader.read(bmp)).distinct())
                    Triple(file, current.rawLines.filter { it.side == 0 } + ocr.read(bmp, 1), qr)
                }
                val files = listOfNotNull(current.frontImage?.let { File(it) }, backFile)
                val outcome = readCard(files, allLines, current.fields.notes, subGroupNamesOf(eventOf(current)), qr)
                // Keep what's already on the card, add what the back side shows; a QR code (often on the back) still wins
                val withQrCode = QrMerge.merge(fillBlanks(current.fields, outcome.fields), QrParser.parse(qr))
                val updated = withSuggestedSubGroups(current.copy(
                    backImage = backFile.absolutePath,
                    rawLines = allLines,
                    qrTexts = qr,
                    fields = withQrCode.fields,
                    uncertain = (current.uncertain + outcome.uncertain + withQrCode.flagged).distinct(),
                    updatedAt = System.currentTimeMillis(),
                ), outcome.aiSubGroups)
                aiNote = outcome.note
                val withGallery = withContext(Dispatchers.IO) { copyToGallery(updated) }
                editing = withGallery
                refreshKey++
                persist(withGallery)
                queueWebNotes(listOf(withGallery))
            } catch (e: Exception) {
                toast = "Could not read the back side: ${e.message}"
            } finally {
                busyMessage = null
            }
        }
    }

    /** "Read with AI" / "Read again" on the review screen. Replaces the details; keeps the user's notes. */
    fun rereadWithAi() {
        val current = editing ?: return
        viewModelScope.launch {
            busyMessage = "Reading with AI…"
            try {
                val outcome = aiRead(current)
                if (outcome.first != null) {
                    editing = outcome.first
                    refreshKey++
                    persist(outcome.first!!)
                    aiNote = null
                    toast = "Read again with AI"
                } else {
                    toast = outcome.second
                }
            } finally {
                busyMessage = null
            }
        }
    }

    /** Re-reads every card that was read offline (and not yet saved to Contacts). */
    fun rereadAllOffline() {
        val targets = offlineCards
        if (targets.isEmpty()) return
        viewModelScope.launch {
            var done = 0
            var stopReason: String? = null
            try {
                for ((i, card) in targets.withIndex()) {
                    busyMessage = "Reading with AI… (${i + 1} of ${targets.size})"
                    val (updated, err) = aiRead(card)
                    if (updated != null) {
                        persist(updated)
                        done++
                    } else {
                        stopReason = err
                        break
                    }
                }
            } finally {
                busyMessage = null
            }
            toast = when {
                stopReason == null -> "$done cards read again with AI"
                done > 0 -> "$done cards read with AI. Stopped: $stopReason"
                else -> stopReason
            }
        }
    }

    private suspend fun aiRead(card: CardRecord): Pair<CardRecord?, String?> {
        val files = listOfNotNull(card.frontImage, card.backImage).map { File(it) }.filter { it.exists() }
        if (files.isEmpty()) return null to "The card photo is missing"
        val local = CardParser.parse(card.rawLines)
        val (outcome, err) = tryAi(files, card.rawLines, card.fields.notes, local, subGroupNamesOf(eventOf(card)), card.qrTexts)
        if (outcome == null) return null to err
        return withSuggestedSubGroups(card.copy(
            fields = outcome.fields,
            readBy = CardRecord.READ_BY_AI,
            uncertain = outcome.uncertain.toList(),
            updatedAt = System.currentTimeMillis(),
        ), outcome.aiSubGroups) to null
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
        business = old.business.ifBlank { new.business },
        keywords = (old.keywords + new.keywords).distinctBy { it.lowercase() },
        extraInfo = listOf(old.extraInfo, new.extraInfo).filter { it.isNotBlank() }.distinct().joinToString("\n"),
    )

    // ------------------------------------------------------------------ editing

    fun open(record: CardRecord) {
        editing = record
        duplicateWarning = null
        duplicateMatch = null
        aiNote = null
        screen = Screen.Review
    }

    fun updateFields(transform: (CardFields) -> CardFields) {
        val e = editing ?: return
        editing = e.copy(fields = transform(e.fields), updatedAt = System.currentTimeMillis())
    }

    /** Edit a field and remove its orange "please check" mark (the user has looked at it). */
    fun edit(key: String, transform: (CardFields) -> CardFields) {
        val e = editing ?: return
        editing = e.copy(
            fields = transform(e.fields),
            uncertain = e.uncertain - key,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun closeReview() {
        duplicateMatch = null // leaving without choosing = keep both
        editing?.let { e ->
            val cleaned = clean(e)
            val before = cards.firstOrNull { it.id == e.id }
            // Only back up again if something actually changed
            persist(cleaned, upload = before == null || before.copy(cloudDirty = false) != cleaned.copy(cloudDirty = false))
        }
        editing = null
        duplicateWarning = null
        aiNote = null
        screen = Screen.Home
    }

    fun deleteEditing() {
        val e = editing ?: return
        if (cloudOn && canEdit(e) && e.ownerEmail.isNotBlank()) cloud.pushDelete(e)
        viewModelScope.launch(Dispatchers.IO) { repo.delete(e) }
        cards = cards.filterNot { it.id == e.id }
        editing = null
        screen = Screen.Home
        toast = "Card deleted"
    }

    private fun clean(r: CardRecord) = r.copy(
        subGroupNames = SubGroups.namesFor(r, eventOf(r)),
        fields = r.fields.copy(
            mobiles = r.fields.mobiles.map { it.trim() }.filter { it.isNotBlank() },
            phones = r.fields.phones.map { it.trim() }.filter { it.isNotBlank() },
            faxes = r.fields.faxes.map { it.trim() }.filter { it.isNotBlank() },
            emails = r.fields.emails.map { it.trim() }.filter { it.isNotBlank() },
            websites = r.fields.websites.map { it.trim() }.filter { it.isNotBlank() },
        )
    )

    /** Saves a card on the phone and (when logged in and allowed) queues it for the cloud backup. */
    private fun persist(r: CardRecord, upload: Boolean = true) {
        val rec = if (upload && cloudOn && canEdit(r)) r.copy(cloudDirty = true) else r
        cards = cards.map { if (it.id == rec.id) rec else it }
        viewModelScope.launch(Dispatchers.IO) { repo.save(rec) }
        if (rec.cloudDirty) schedulePush()
    }

    /** "Share with company" on/off for the open card. */
    fun setPrivate(makePrivate: Boolean) {
        val e = editing ?: return
        editing = e.copy(
            visibility = if (makePrivate) CardRecord.VISIBILITY_PRIVATE else CardRecord.VISIBILITY_COMPANY,
            imagesUploaded = false, // photos carry the visibility too, so send them again
            updatedAt = System.currentTimeMillis(),
        )
    }

    // ------------------------------------------------------------------ contacts

    fun contactIntent(): Intent? {
        val e = editing ?: return null
        val cleaned = clean(e).copy(subGroupsSuggested = false)
        editing = cleaned
        persist(cleaned)
        return ContactSaver.buildInsertIntent(cleaned)
    }

    fun onContactScreenClosed(saved: Boolean) {
        val e = editing ?: return
        if (saved) {
            val updated = e.copy(savedToContacts = true)
            editing = updated
            persist(updated, upload = false)
            toast = "Saved to Contacts ✓"
        }
    }

    // ------------------------------------------------------------------ settings

    fun openSettings() {
        testStatus = null
        aiReadsToday = settings.aiReadsToday
        screen = Screen.Settings
    }

    fun closeSettings() {
        screen = if (editing != null) Screen.Review else Screen.Home
    }

    fun saveKey(key: String) {
        settings.apiKey = key
        aiKey = settings.apiKey
        settings.aiKeyFromTeam = false
        aiKeyFromTeam = false
        if (aiKey.isNotBlank()) {
            settings.aiEnabled = true
            aiEnabled = true
        }
        testStatus = null
        toast = if (aiKey.isBlank()) "AI key removed" else "AI key saved"
        if (isAdmin) {
            viewModelScope.launch {
                toast = try {
                    cloud.saveAiConfig(aiKey, settings.models)
                    if (aiKey.isBlank()) "AI key removed for the team" else "AI key saved and shared with your team ✓"
                } catch (e: CloudException) {
                    "Saved on this phone. Couldn't share with the team: ${e.message}"
                }
            }
        }
    }

    fun changeAiEnabled(on: Boolean) {
        settings.aiEnabled = on
        aiEnabled = on
    }

    fun changeUserName(name: String) {
        userName = name
        settings.userName = name
    }

    fun saveModels(text: String) {
        val list = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        settings.models = list.ifEmpty { GeminiProtocol.DEFAULT_MODELS }
        modelsText = settings.models.joinToString(", ")
        toast = "Models saved"
    }

    fun resetModels() {
        settings.models = GeminiProtocol.DEFAULT_MODELS
        modelsText = settings.models.joinToString(", ")
    }

    fun changeSaveToGallery(on: Boolean) {
        settings.saveToGallery = on
        saveToGallery = on
    }

    /** Copies the card's photos to the Gallery album if that's switched on and not done yet. Runs on a background thread. */
    private fun copyToGallery(r: CardRecord, force: Boolean = false): CardRecord {
        if (!settings.saveToGallery && !force) return r
        val ctx = getApplication<Application>()
        var out = r
        if (!r.frontInGallery && r.frontImage != null &&
            GallerySaver.save(ctx, File(r.frontImage), GalleryNames.fileName(r.fields, back = false, scannedAt = r.createdAt))
        ) out = out.copy(frontInGallery = true)
        if (!r.backInGallery && r.backImage != null &&
            GallerySaver.save(ctx, File(r.backImage), GalleryNames.fileName(r.fields, back = true, scannedAt = r.createdAt))
        ) out = out.copy(backInGallery = true)
        return out
    }

    /** Settings button: copy the photos of every card on this phone into the Gallery album. */
    fun saveAllToGallery() {
        viewModelScope.launch {
            busyMessage = "Saving photos to Gallery…"
            var count = 0
            val updated = withContext(Dispatchers.IO) {
                cards.map { r ->
                    val before = (if (r.frontInGallery) 1 else 0) + (if (r.backInGallery) 1 else 0)
                    val after = copyToGallery(r, force = true)
                    count += (if (after.frontInGallery) 1 else 0) + (if (after.backInGallery) 1 else 0) - before
                    if (after != r) repo.save(after)
                    after
                }
            }
            cards = updated
            busyMessage = null
            toast = if (count > 0) "$count photos saved to the \"${GalleryNames.ALBUM}\" album" else "All card photos are already in the Gallery"
        }
    }

    fun testKey() {
        viewModelScope.launch {
            testStatus = "Testing…"
            testStatus = try {
                if (!isOnline()) "No internet connection"
                else {
                    val model = withContext(Dispatchers.IO) { GeminiClient(aiKey, settings.models).test() }
                    "✓ Working (answered by $model)"
                }
            } catch (e: AiException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun setupCode(): String = settings.setupCode()

    fun applySetupCode(code: String) {
        if (settings.applySetupCode(code)) {
            aiKey = settings.apiKey
            aiEnabled = settings.aiEnabled
            modelsText = settings.models.joinToString(", ")
            toast = "Phone set up ✓ AI reading is on"
        } else {
            toast = "That QR code is not a Card Scanner setup code"
        }
    }

    // ------------------------------------------------------------------ duplicates

    /** Duplicate dialog: discard the new scan and open the card that was already there. */
    fun duplicateOpenExisting() {
        val existing = duplicateMatch?.card ?: return
        discardNewScan()
        open(cards.firstOrNull { it.id == existing.id } ?: existing)
    }

    fun duplicateKeepBoth() {
        duplicateMatch = null
        schedulePush()
    }

    fun duplicateDiscard() {
        discardNewScan()
        screen = Screen.Home
        toast = "Scan discarded"
    }

    private fun discardNewScan() {
        val e = editing ?: return
        duplicateMatch = null
        if (cloudOn && !e.cloudDirty && e.ownerEmail.isNotBlank()) cloud.pushDelete(e) // in case it was already sent
        viewModelScope.launch(Dispatchers.IO) { repo.delete(e) }
        cards = cards.filterNot { it.id == e.id }
        editing = null
    }

    fun describeDuplicate(m: Duplicates.Match): String {
        val c = m.card
        val who = when {
            cloudOn && c.ownerEmail == myEmail -> "you"
            c.scannedBy.isNotBlank() -> c.scannedBy
            else -> "you"
        }
        val time = java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(c.createdAt))
        val where = when {
            m.sameEvent -> " at this event"
            c.eventName.isNotBlank() -> " (${c.eventName})"
            else -> ""
        }
        return "${c.fields.displayName} was already scanned by $who on $time$where: ${m.reason}."
    }

    // ------------------------------------------------------------------ events

    fun selectEvent(id: String) {
        currentEventId = id
        settings.currentEventId = id
        subGroupFilter = ""
    }

    /** Anyone can create an event. [memberEmails] = teammates to add (you are always included). */
    fun createEvent(name: String, place: String, memberEmails: List<String>) {
        val n = name.trim()
        if (n.isEmpty()) {
            toast = "Please give the event a name"
            return
        }
        val now = System.currentTimeMillis()
        val e = EventGroup(
            id = "ev_" + UUID.randomUUID().toString().take(12),
            name = n,
            place = place.trim(),
            createdBy = myEmail,
            members = if (cloudOn) (listOf(myEmail) + memberEmails.map { CloudMapper.normalizeEmail(it) }).distinct() else emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        saveEvent(e)
        selectEvent(e.id)
        toast = "Event \"${e.name}\" created. New scans go into it."
    }

    fun updateEvent(e: EventGroup) {
        saveEvent(e.copy(updatedAt = System.currentTimeMillis(), members = e.members.map { CloudMapper.normalizeEmail(it) }.distinct()))
    }

    fun closeEvent(e: EventGroup) {
        updateEvent(e.copy(closed = true))
        if (currentEventId == e.id) selectEvent("")
        toast = "\"${e.name}\" moved to past events"
    }

    fun reopenEvent(e: EventGroup) {
        updateEvent(e.copy(closed = false))
        toast = "\"${e.name}\" reopened"
    }

    private fun saveEvent(e: EventGroup) {
        events = events.filterNot { it.id == e.id } + e
        val snapshot = events
        viewModelScope.launch(Dispatchers.IO) { eventStore.save(snapshot) }
        if (cloudOn) cloud.saveEvent(e, isAdmin) { err -> toast = "Couldn't save the event online: ${err.message}" }
    }

    fun openManageEvent(e: EventGroup) {
        managingEventId = e.id
        screen = Screen.Event
        if (cloudOn) loadMembers()
    }

    fun closeManageEvent() {
        screen = Screen.Home
    }

    /** Moves the open card to another event ("" = no event). */
    fun setCardEvent(eventId: String) {
        val e = editing ?: return
        val ev = events.firstOrNull { it.id == eventId }
        if (ev?.id.orEmpty() == e.eventId) return
        // Sub-groups belong to an event, so they start again (with a fresh suggestion) in the new one
        val moved = e.copy(
            eventId = ev?.id.orEmpty(), eventName = ev?.name.orEmpty(),
            subGroupIds = emptyList(), subGroupNames = emptyList(), subGroupsSuggested = false,
            updatedAt = System.currentTimeMillis(),
        )
        editing = withSuggestedSubGroups(moved, null)
    }

    /** Loads the team list so colleagues can be added to an event. */
    fun loadTeamForEvents() {
        if (cloudOn) loadMembers()
    }

    private suspend fun syncEvents(m: AuthState.Member) {
        val fromCloud = cloud.loadEvents(m.email, m.isAdmin)
        val cloudIds = fromCloud.map { it.id }.toSet()
        // Keep events I made that haven't reached the cloud yet; drop ones I no longer have access to
        val pending = events.filter { it.id !in cloudIds && (it.createdBy == m.email || it.createdBy.isBlank()) }
        pending.forEach { e ->
            val fixed = e.copy(createdBy = m.email, members = (e.members + m.email).distinct())
            cloud.saveEvent(fixed, m.isAdmin) { }
        }
        events = fromCloud + pending.map { it.copy(createdBy = m.email, members = (it.members + m.email).distinct()) }
        val snapshot = events
        withContext(Dispatchers.IO) { eventStore.save(snapshot) }
        if (currentEventId.isNotBlank() && events.none { it.id == currentEventId }) selectEvent("")
    }

    // ------------------------------------------------------------------ note from the company's website

    /** Cards whose website is being read right now ("Reading their website…"). */
    var webBusyIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var autoWebNotes by mutableStateOf(settings.autoWebNotes)
        private set
    private val webQueue = ArrayDeque<String>()
    private var webJob: Job? = null
    private var webPausedUntil = 0L

    fun changeAutoWebNotes(on: Boolean) {
        settings.autoWebNotes = on
        autoWebNotes = on
        if (on) queueRecentWebNotes()
    }

    /** The site to read for a card: the first website on it (printed or from its QR code). */
    fun websiteOf(c: CardRecord): String? = c.fields.websites.firstOrNull { it.isNotBlank() }

    private fun needsWebNote(c: CardRecord) = c.webCheckedAt == 0L && websiteOf(c) != null && canEdit(c)

    /** Cards from before this feature whose website hasn't been read (done only when asked, in Settings). */
    val olderCardsWithoutWebNote: List<CardRecord>
        get() {
            val since = settings.webNotesSince
            return cards.filter { it.createdAt < since && needsWebNote(it) }
        }

    private fun queueWebNotes(list: List<CardRecord>) {
        if (!aiReady || !autoWebNotes) return
        list.filter { needsWebNote(it) }.forEach { if (it.id !in webQueue) webQueue.addLast(it.id) }
        runWebQueue()
    }

    private fun queueRecentWebNotes() {
        val since = settings.webNotesSince
        queueWebNotes(cards.filter { it.createdAt >= since })
    }

    /** "Read website" / "Read again" on the open card. */
    fun readWebsiteNow() {
        val e = editing ?: return
        when {
            !aiReady -> toast = "Turn on AI reading in Settings first"
            websiteOf(e) == null -> toast = "This card has no website"
            !canEdit(e) -> toast = "Only the person who scanned this card can change it"
            else -> {
                webPausedUntil = 0L
                webQueue.remove(e.id)
                webQueue.addFirst(e.id)
                runWebQueue()
            }
        }
    }

    /** Settings → "Read websites for older cards". */
    fun readOlderWebsites() {
        if (!aiReady) {
            toast = "Turn on AI reading first"
            return
        }
        val list = olderCardsWithoutWebNote
        list.forEach { if (it.id !in webQueue) webQueue.addLast(it.id) }
        webPausedUntil = 0L
        runWebQueue()
        toast = "Reading ${list.size} website${if (list.size == 1) "" else "s"} in the background. Keep the app open."
    }

    /** Reads the queued websites one at a time, gently, so the free AI limits are never hit hard. */
    private fun runWebQueue() {
        if (webJob?.isActive == true) return
        webJob = viewModelScope.launch {
            while (webQueue.isNotEmpty()) {
                if (System.currentTimeMillis() < webPausedUntil || !aiReady || !isOnline()) break
                val id = webQueue.removeFirst()
                val card = (editing?.takeIf { it.id == id } ?: cards.firstOrNull { it.id == id }) ?: continue
                val site = websiteOf(card) ?: continue
                webBusyIds = webBusyIds + id
                val result = try {
                    readWebsite(card, site)
                } finally {
                    webBusyIds = webBusyIds - id
                }
                applyWebNote(id, result)
                if (result.stop) {
                    if (result.quota) webPausedUntil = System.currentTimeMillis() + 60 * 60_000L
                    break
                }
                delay(2_000)
            }
        }
    }

    /** [done] = finished (with or without a note); [stop] = stop the queue for now (offline, no key, free limit). */
    private class WebResult(
        val note: String?,
        val keywords: List<String>,
        val url: String,
        val done: Boolean,
        val stop: Boolean = false,
        val quota: Boolean = false,
    )

    private suspend fun readWebsite(card: CardRecord, site: String): WebResult = withContext(Dispatchers.IO) {
        val company = card.fields.company.ifBlank { card.fields.displayName }
        val known = card.fields.keywords
        val client = GeminiClient(aiKey, settings.models)
        val page = WebReader.read(site)
        val url = page?.url ?: if (site.startsWith("http", ignoreCase = true)) site else "https://$site"
        try {
            val note = if (page != null && page.text.length >= 300) client.webNote(company, page.url, page.text, known)
            else client.webNoteFromUrl(company, url, known) // site built by scripts, or the phone couldn't open it
            WebResult(note.note, note.keywords, url, done = true)
        } catch (e: AiException) {
            when (e.kind) {
                AiException.Kind.QUOTA -> WebResult(null, emptyList(), url, done = false, stop = true, quota = true)
                AiException.Kind.AUTH, AiException.Kind.OFFLINE -> WebResult(null, emptyList(), url, done = false, stop = true)
                AiException.Kind.OTHER -> WebResult(null, emptyList(), url, done = true)
            }
        } catch (e: Exception) {
            WebResult(null, emptyList(), url, done = true)
        }
    }

    private fun applyWebNote(id: String, r: WebResult) {
        if (!r.done) return
        val now = System.currentTimeMillis()
        val open = editing?.takeIf { it.id == id }
        val base = open ?: cards.firstOrNull { it.id == id } ?: return
        val updated = base.copy(
            webNote = r.note?.takeIf { it.isNotBlank() } ?: base.webNote,
            webNoteUrl = r.url,
            webCheckedAt = now,
            fields = if (r.keywords.isEmpty()) base.fields
            else base.fields.copy(keywords = KeywordExtractor.clean(base.fields.keywords + r.keywords, max = 30)),
            updatedAt = now,
        )
        persist(updated)
        if (open != null) {
            editing = updated
            refreshKey++
            if (!r.note.isNullOrBlank()) toast = "Note from their website added"
        }
    }

    /** The user edited the website note. */
    fun setWebNote(text: String) {
        val e = editing ?: return
        editing = e.copy(webNote = text, updatedAt = System.currentTimeMillis())
    }

    // ------------------------------------------------------------------ sub-groups (inside an event)

    /** The event a card belongs to, if this phone knows it. */
    fun eventOf(card: CardRecord): EventGroup? =
        card.eventId.takeIf { it.isNotBlank() }?.let { id -> events.firstOrNull { it.id == id } }

    /** The event's sub-groups in display order (oldest first). */
    fun subGroupsOf(e: EventGroup?): List<SubGroup> = e?.let { SubGroups.sorted(it.subGroups) }.orEmpty()

    private fun subGroupNamesOf(e: EventGroup?): List<String> = subGroupsOf(e).map { it.name }

    /** Sub-group names to show for a card. */
    fun subGroupNames(card: CardRecord): List<String> = SubGroups.namesFor(card, eventOf(card))

    /** Only admins add, rename or delete sub-groups (before login is set up: whoever uses the phone). */
    val canManageSubGroups: Boolean get() = !cloudOn || isAdmin

    /**
     * Puts the AI's choice (or, without AI, a keyword guess) on a card whose sub-groups nobody has
     * confirmed yet. A choice a person made is never overwritten.
     */
    private fun withSuggestedSubGroups(card: CardRecord, aiNames: List<String>?): CardRecord {
        val ev = eventOf(card) ?: return card
        if (ev.subGroups.isEmpty()) return card
        if (SubGroups.liveIds(card, ev).isNotEmpty() && !card.subGroupsSuggested) return card
        val ids = if (aiNames != null) SubGroups.idsFromNames(aiNames, ev.subGroups) else SubGroups.suggestOffline(card.fields, ev.subGroups)
        return SubGroups.withIds(card, ids, ev).copy(subGroupsSuggested = ids.isNotEmpty())
    }

    /** Tick / untick a sub-group on the open card. */
    fun toggleSubGroup(id: String) {
        val e = editing ?: return
        val ev = eventOf(e) ?: return
        editing = SubGroups.toggle(e, id, ev).copy(subGroupsSuggested = false, updatedAt = System.currentTimeMillis())
    }

    /** "Looks right": accept the suggested sub-groups. */
    fun confirmSubGroups() {
        val e = editing ?: return
        editing = e.copy(subGroupsSuggested = false, updatedAt = System.currentTimeMillis())
    }

    fun addSubGroup(e: EventGroup, name: String) {
        if (!canManageSubGroups) { toast = "Only an admin can add sub-groups"; return }
        val n = SubGroups.cleanName(name)
        if (n.isEmpty()) { toast = "Please type a name for the sub-group"; return }
        if (SubGroups.findByName(e.subGroups, n) != null) { toast = "\"$n\" is already there"; return }
        val g = SubGroup("sg_" + UUID.randomUUID().toString().take(10), n, System.currentTimeMillis())
        updateEvent(e.copy(subGroups = e.subGroups + g))
    }

    fun renameSubGroup(e: EventGroup, id: String, name: String) {
        if (!canManageSubGroups) { toast = "Only an admin can rename sub-groups"; return }
        val n = SubGroups.cleanName(name)
        if (n.isEmpty()) { toast = "Please type a name for the sub-group"; return }
        val clash = SubGroups.findByName(e.subGroups, n)
        if (clash != null && clash.id != id) { toast = "\"$n\" is already there"; return }
        updateEvent(e.copy(subGroups = e.subGroups.map { if (it.id == id) it.copy(name = n) else it }))
    }

    /** Removes a sub-group. The cards stay in the event; they just lose this label. */
    fun deleteSubGroup(e: EventGroup, id: String) {
        if (!canManageSubGroups) { toast = "Only an admin can delete sub-groups"; return }
        updateEvent(e.copy(subGroups = e.subGroups.filterNot { it.id == id }))
        if (subGroupFilter == id) subGroupFilter = ""
    }

    /** Cards in the event that are in no sub-group yet and that I may change. */
    fun unsortedCards(e: EventGroup): List<CardRecord> =
        cardsIn(e.id).filter { canEdit(it) && SubGroups.liveIds(it, e).isEmpty() }

    /**
     * Suggests sub-groups for cards scanned before the sub-groups existed. Uses the AI on the details
     * already read (no photos, up to 30 cards per request); without AI, a keyword guess.
     * Suggestions show on each card for a quick confirm.
     */
    fun suggestSubGroupsFor(e: EventGroup) {
        val ev = events.firstOrNull { it.id == e.id } ?: e
        val targets = unsortedCards(ev)
        if (ev.subGroups.isEmpty() || targets.isEmpty()) { toast = "No unsorted cards in this event"; return }
        viewModelScope.launch {
            busyMessage = "Sorting ${targets.size} card${if (targets.size == 1) "" else "s"}…"
            val names = subGroupNamesOf(ev)
            val fromAi = HashMap<String, List<String>>()
            var aiProblem: String? = null
            try {
                if (aiReady && isOnline()) {
                    withContext(Dispatchers.IO) {
                        val client = GeminiClient(aiKey, settings.models)
                        for (chunk in targets.chunked(30)) {
                            val items = chunk.mapIndexed { i, c -> GeminiProtocol.SortItem("c${i + 1}", sortText(c)) }
                            val answer = client.sortIntoSubGroups(names, items)
                            chunk.forEachIndexed { i, c -> answer["c${i + 1}"]?.let { fromAi[c.id] = SubGroups.idsFromNames(it, ev.subGroups) } }
                        }
                    }
                } else if (aiReady) {
                    aiProblem = "no internet"
                }
            } catch (ex: AiException) {
                aiProblem = if (ex.kind == AiException.Kind.QUOTA) "today's free AI limit is used up" else ex.message
            } catch (ex: Exception) {
                aiProblem = ex.message ?: ex.javaClass.simpleName
            }
            var sorted = 0
            val now = System.currentTimeMillis()
            for (c in targets) {
                val ids = fromAi[c.id] ?: SubGroups.suggestOffline(c.fields, ev.subGroups)
                if (ids.isEmpty()) continue
                persist(SubGroups.withIds(c, ids, ev).copy(subGroupsSuggested = true, updatedAt = now))
                sorted++
            }
            busyMessage = null
            val left = targets.size - sorted
            toast = buildString {
                append(if (sorted == 0) "No sub-group fitted these cards" else "$sorted card${if (sorted == 1) "" else "s"} sorted, please confirm on each card")
                if (left > 0 && sorted > 0) append(". $left didn't fit any sub-group")
                if (aiProblem != null) append(" (matched by keywords: $aiProblem)")
            }
        }
    }

    /** A card's details as one line of text for sorting. */
    private fun sortText(c: CardRecord): String {
        val f = c.fields
        return listOf(
            "Company: " + f.company,
            "Designation: " + f.jobTitle,
            "What they do: " + f.business,
            "Keywords: " + f.keywords.joinToString(", "),
            "Other: " + f.extraInfo,
        ).filter { !it.endsWith(": ") }.joinToString("; ")
    }

    // ------------------------------------------------------------------ login & team

    private fun authAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            authBusy = true
            authError = null
            authInfo = null
            try {
                block()
            } catch (e: CloudException) {
                authError = e.message
            } catch (e: Exception) {
                authError = e.message ?: e.javaClass.simpleName
            } finally {
                authBusy = false
            }
        }
    }

    fun signInWithGoogle(activity: Activity) = authAction {
        val token = cloud.googleIdToken(activity)
        cloud.signInWithGoogle(token)
        checkMembership()
    }

    fun signInWithEmail(email: String, password: String) = authAction {
        if (email.isBlank() || password.isBlank()) throw CloudException("Enter your email and password")
        cloud.signInWithEmail(email, password)
        checkMembership()
    }

    fun createAccount(email: String, password: String) = authAction {
        if (email.isBlank() || password.length < 6) throw CloudException("Enter your email and a password of at least 6 characters")
        cloud.createAccount(email, password)
        checkMembership()
    }

    fun forgotPassword(email: String) = authAction {
        if (email.isBlank()) throw CloudException("Type your email above first, then tap Forgot password")
        cloud.sendPasswordReset(email)
        authInfo = "We've emailed a password reset link to ${email.trim()}. Check spam too."
    }

    fun checkVerified() = authAction {
        if (cloud.refreshVerified()) checkMembership()
        else throw CloudException("Not verified yet. Open the link in the email first (check the spam folder too).")
    }

    fun resendVerification() = authAction {
        cloud.resendVerification()
        authInfo = "Verification email sent again"
    }

    fun createCompany(name: String) = authAction {
        val email = cloud.email ?: throw CloudException("Please sign in again")
        val display = settings.userName.ifBlank { cloud.user?.displayName.orEmpty() }.ifBlank { email.substringBefore('@') }
        cloud.createTeam(name.trim().ifBlank { "My company" }, email, display)
        checkMembership()
    }

    fun retryMembership() = authAction { checkMembership() }

    private fun refreshMembership() {
        viewModelScope.launch {
            try {
                checkMembership()
            } catch (e: Exception) {
                val msg = (e as? CloudException)?.message ?: "Could not reach the cloud (${e.message ?: e.javaClass.simpleName})"
                if (auth is AuthState.Member) syncStatus = msg
                else auth = AuthState.Problem(msg)
            }
        }
    }

    /** Works out whether the signed-in user is admin, staff or not yet added, and starts the backup. */
    private suspend fun checkMembership() {
        val email = cloud.email ?: run { auth = AuthState.SignedOut; return }
        if (!cloud.isVerified()) {
            auth = AuthState.VerifyEmail(email)
            return
        }
        val t = cloud.loadTeam()
        team = t
        if (!t.exists) {
            auth = AuthState.NoCompany(email)
            return
        }
        val admin = email in t.admins
        if (!admin && !cloud.isListedMember(email)) {
            settings.cachedMembership = ""
            auth = AuthState.NotMember(email)
            return
        }
        val name = settings.userName.ifBlank { cloud.user?.displayName.orEmpty() }.ifBlank { email.substringBefore('@') }
        if (settings.userName.isBlank()) changeUserName(name)
        val m = AuthState.Member(email, name, admin, t.name)
        auth = m
        settings.cachedMembership = "$email|$admin|${t.name}"
        onBecameMember(m)
    }

    private suspend fun onBecameMember(m: AuthState.Member) {
        // The company's shared AI key
        runCatching { cloud.loadAiConfig() }.getOrNull().let { cfg ->
            if (cfg != null) {
                if (!m.isAdmin || aiKey.isBlank()) {
                    settings.apiKey = cfg.first
                    if (cfg.second.isNotEmpty()) settings.models = cfg.second
                    settings.aiKeyFromTeam = !m.isAdmin
                    settings.aiEnabled = true
                }
            } else if (m.isAdmin && aiKey.isNotBlank()) {
                runCatching { cloud.saveAiConfig(aiKey, settings.models) } // share the admin's key with staff
            }
            aiKey = settings.apiKey
            aiEnabled = settings.aiEnabled
            aiKeyFromTeam = settings.aiKeyFromTeam
            modelsText = settings.models.joinToString(", ")
        }
        // Cards scanned on this phone before login now belong to this user
        if (settings.migratedFor != m.email) {
            val updated = withContext(Dispatchers.IO) {
                cards.map { r ->
                    if (r.ownerEmail.isBlank()) r.copy(ownerEmail = m.email, scannedBy = r.scannedBy.ifBlank { m.name }, cloudDirty = true)
                        .also { repo.save(it) } else r
                }
            }
            cards = updated
            // Events made on this phone before login become this user's events
            events.filter { it.createdBy.isBlank() }.forEach { e ->
                saveEvent(e.copy(createdBy = m.email, members = (e.members + m.email).distinct()))
            }
            settings.migratedFor = m.email
        }
        syncNow()
    }

    fun requestSignOut() {
        viewModelScope.launch {
            busyMessage = "Checking your backup…"
            val safe = cards.none { it.cloudDirty } && cloud.allChangesUploaded(6_000)
            busyMessage = null
            if (safe) doSignOut() else signOutWarning = true
        }
    }

    fun doSignOut() {
        signOutWarning = false
        // Only a team member's cards are in the cloud; before that, keep everything on the phone.
        val wasMember = auth is AuthState.Member
        viewModelScope.launch {
            cloud.signOut()
            if (wasMember) {
                withContext(Dispatchers.IO) {
                    repo.deleteAll()
                    eventStore.clear()
                }
                settings.clearCloud()
                cards = emptyList()
                events = emptyList()
                currentEventId = ""
            }
            aiKey = settings.apiKey
            aiKeyFromTeam = settings.aiKeyFromTeam
            members = emptyList()
            editing = null
            team = null
            syncStatus = null
            screen = Screen.Home
            auth = AuthState.SignedOut
        }
    }

    fun openTeam() {
        screen = Screen.Team
        loadMembers()
    }

    fun closeTeam() {
        screen = Screen.Settings
    }

    fun loadMembers() {
        viewModelScope.launch {
            try {
                val t = cloud.loadTeam()
                team = t
                members = cloud.listMembers(t)
            } catch (e: CloudException) {
                toast = e.message
            }
        }
    }

    fun addMember(email: String, name: String, admin: Boolean) {
        val e = CloudMapper.normalizeEmail(email)
        if (!e.contains('@') || !e.contains('.')) {
            toast = "Please enter a valid email address"
            return
        }
        viewModelScope.launch {
            try {
                cloud.addMember(e, name, admin, myEmail)
                toast = "$e added to the team ✓"
                loadMembers()
            } catch (ex: CloudException) {
                toast = ex.message
            }
        }
    }

    fun removeMember(email: String) {
        if (email == myEmail) {
            toast = "You can't remove yourself"
            return
        }
        viewModelScope.launch {
            try {
                cloud.removeMember(email)
                toast = "$email removed. Their cards stay with the company."
                loadMembers()
            } catch (ex: CloudException) {
                toast = ex.message
            }
        }
    }

    // ------------------------------------------------------------------ backup / sync

    private fun schedulePush() {
        if (!cloudOn) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(1_500)
            syncNow()
        }
    }

    /** Called when the app comes back to the front. */
    fun onAppResumed() {
        if (cloudOn && System.currentTimeMillis() - lastSyncRun > 2 * 60_000) syncNow()
        queueRecentWebNotes()
    }

    /** Sends this phone's changes and gets the team's changes. [full] = re-download everything. */
    fun syncNow(full: Boolean = false) {
        val m = member ?: return
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                syncStatus = "Backing up…"
                pushDirty(m)
                val since = if (full || cards.isEmpty() || settings.lastSyncSeconds == 0L) null
                else Timestamp(settings.lastSyncSeconds, settings.lastSyncNanos)
                val pull = cloud.pullCards(m.email, m.isAdmin, since)
                applyPull(pull, m)
                runCatching { syncEvents(m) }
                pull.newest?.let {
                    settings.lastSyncSeconds = it.seconds
                    settings.lastSyncNanos = it.nanoseconds
                }
                lastSyncRun = System.currentTimeMillis()
                syncStatus = if (cards.any { it.cloudDirty }) "Waiting to back up…" else "All cards backed up ✓"
            } catch (e: CloudException) {
                syncStatus = if (e.offline) "Offline: cards are safe on the phone and will back up later" else e.message
            } catch (e: Exception) {
                syncStatus = "Backup problem: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                syncing = false
            }
        }
    }

    private suspend fun pushDirty(m: AuthState.Member) {
        val dirty = cards.filter { it.cloudDirty }
        if (dirty.isEmpty()) return
        val sent = withContext(Dispatchers.IO) {
            dirty.map { r ->
                if (!canEdit(r)) return@map r.copy(cloudDirty = false).also { repo.save(it) }
                val owned = if (r.ownerEmail.isBlank()) r.copy(ownerEmail = m.email) else r
                cloud.pushCard(owned, { back ->
                    (if (back) owned.backImage else owned.frontImage)?.let { File(it) }?.takeIf { it.exists() }?.let { ImageUtils.cloudJpeg(it) }
                }) { err ->
                    // The cloud refused (e.g. access removed): keep it marked so nothing is lost
                    syncStatus = "Backup refused: ${err.message}"
                    cards = cards.map { c -> if (c.id == owned.id) c.copy(cloudDirty = true) else c }
                }
                owned.copy(cloudDirty = false, imagesUploaded = true).also { repo.save(it) }
            }
        }
        val byId = sent.associateBy { it.id }
        cards = cards.map { byId[it.id] ?: it }
        val open = editing
        if (open != null) {
            val sentCopy = byId[open.id]
            if (sentCopy != null) editing = sentCopy.copy(fields = open.fields)
        }
    }

    private suspend fun applyPull(pull: CloudService.Pull, m: AuthState.Member) {
        if (pull.items.isEmpty() && !pull.complete) return
        val openId = editing?.id
        val result = withContext(Dispatchers.IO) {
            val local = cards.associateBy { it.id }.toMutableMap()
            val total = pull.items.size
            for ((i, remote) in pull.items.withIndex()) {
                if (total > 15) syncStatus = "Restoring cards… ${i + 1} of $total"
                val id = remote.record.id
                if (id == openId) continue // don't change the card the user is editing right now
                when (CloudMapper.decide(local[id], remote)) {
                    CloudMapper.Action.KEEP_LOCAL -> Unit
                    CloudMapper.Action.DELETE_LOCAL -> local.remove(id)?.let { repo.delete(it) }
                    CloudMapper.Action.TAKE_REMOTE -> {
                        var merged = CloudMapper.mergeRemoteIntoLocal(local[id], remote.record)
                        if (merged.frontImage == null && remote.hasFront) {
                            merged = merged.copy(frontImage = runCatching { cloud.downloadImage(id, false) }.getOrNull()
                                ?.let { repo.saveImageBytes("${id}_front.jpg", it) })
                        }
                        if (merged.backImage == null && remote.hasBack) {
                            merged = merged.copy(backImage = runCatching { cloud.downloadImage(id, true) }.getOrNull()
                                ?.let { repo.saveImageBytes("${id}_back.jpg", it) })
                        }
                        repo.save(merged)
                        local[id] = merged
                    }
                }
            }
            if (pull.complete) {
                // Cards this user can no longer see (made private by a colleague, or access changed)
                val ids = pull.items.filter { !it.deleted }.map { it.record.id }.toSet()
                local.values
                    .filter { it.id !in ids && it.id != openId && !it.cloudDirty && it.ownerEmail.isNotBlank() && it.ownerEmail != m.email }
                    .forEach { local.remove(it.id); repo.delete(it) }
            }
            local.values.sortedByDescending { it.createdAt }
        }
        cards = result
    }

    // ------------------------------------------------------------------ export

    /** Cards to export: the selected event's cards, or all cards. */
    private val exportCards: List<CardRecord>
        get() = (if (currentEventId.isNotBlank()) cardsIn(currentEventId) else cards)
            .map { it.copy(subGroupNames = SubGroups.namesFor(it, eventOf(it))) }

    fun csvBytes(): ByteArray = Exporter.csv(exportCards)

    fun csvFileName(): String {
        val ev = currentEvent?.name?.let { GalleryNames.clean(it).replace(' ', '_') + "_" }.orEmpty()
        return "BusinessCards_$ev${Exporter.fileStamp()}.csv"
    }

    fun writeToUri(uri: Uri, bytes: ByteArray) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            toast = if (ok) "Saved ${exportCards.size} cards" else "Could not save the file"
        }
    }

    fun shareCsv(): Intent {
        val ctx = getApplication<Application>()
        val file = Exporter.writeTemp(ctx, csvFileName(), csvBytes())
        return Exporter.shareIntent(ctx, file, "text/csv", "Business cards (Excel)")
    }

    fun shareAllVcf(): Intent {
        val ctx = getApplication<Application>()
        val file = Exporter.writeTemp(ctx, csvFileName().removeSuffix(".csv") + ".vcf", Exporter.vcard(exportCards))
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
        qrReader.close()
        super.onCleared()
    }
}
