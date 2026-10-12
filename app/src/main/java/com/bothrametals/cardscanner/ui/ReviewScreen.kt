package com.bothrametals.cardscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.bothrametals.cardscanner.R
import com.bothrametals.cardscanner.ai.FieldKeys
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.CardParser
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onSaveToContacts: () -> Unit,
    onAddBackSide: () -> Unit,
    onShareVcf: () -> Unit,
    onLaunch: (Intent) -> Unit,
) {
    val record = vm.editing ?: return
    val f = record.fields
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var zoomImage by remember { mutableStateOf<File?>(null) }
    var assignLine by remember { mutableStateOf<String?>(null) }
    var confirmReread by remember { mutableStateOf(false) }
    var waitForWebsite by remember { mutableStateOf(false) }
    val flags = record.uncertain.toSet()
    val canEdit = vm.canEdit(record)

    BusyAndToast(vm, snackbar)
    DuplicateDialog(vm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check details") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onShareVcf) { Icon(Icons.Default.Share, "Share contact") }
                    if (canEdit) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete card") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    // If the website note is a few seconds away, offer to wait so it goes into the contact too
                    onClick = { if (record.id in vm.webBusyIds) waitForWebsite = true else onSaveToContacts() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(52.dp),
                ) {
                    Text(
                        if (record.savedToContacts) "Saved ✓  ·  Save to Contacts again" else "Save to Contacts",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        CompositionLocalProvider(LocalReadOnly provides !canEdit) {
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (!canEdit) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        "Scanned by ${record.scannedBy.ifBlank { record.ownerEmail }}. Only they or an admin can change this card. " +
                            "You can still call, WhatsApp, email or save it to your contacts.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            vm.duplicateWarning?.let { warn ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(warn, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (vm.cloudOn && canEdit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Share with company", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (record.isPrivate) "Private: only you and the admin can see this card"
                            else "The whole team can see this card",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = !record.isPrivate, onCheckedChange = { vm.setPrivate(!it) })
                }
            }

            if (canEdit) ReadByBanner(
                record = record,
                note = vm.aiNote,
                aiReady = vm.aiReady,
                onReadWithAi = { if (record.readBy == CardRecord.READ_BY_AI) confirmReread = true else vm.rereadWithAi() },
                onSetUp = { vm.openSettings() },
            )

            vm.duplicateOfCard(record)?.let { other ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Possible duplicate of ${other.fields.displayName}" +
                                (other.scannedBy.takeIf { it.isNotBlank() }?.let { ", scanned by $it" } ?: "") + ".",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row {
                            TextButton(onClick = { vm.openOther(other) }) { Text("Open other card") }
                            if (canEdit) TextButton(onClick = { vm.clearDuplicateFlag() }) { Text("Not a duplicate") }
                        }
                    }
                }
            }

            if (canEdit || record.eventName.isNotBlank()) {
                Row(Modifier.padding(top = 8.dp)) {
                    CardEventPicker(vm, record.eventId, record.eventName, enabled = canEdit)
                }
            }

            SubGroupPicker(vm, record, canEdit)

            // Card photos
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardImage(vm.imageFile(record.frontImage), "Front", Modifier.weight(1f)) { zoomImage = it }
                val back = vm.imageFile(record.backImage)
                if (back != null) {
                    CardImage(back, "Back", Modifier.weight(1f)) { zoomImage = it }
                } else if (canEdit) {
                    OutlinedButton(
                        onClick = onAddBackSide,
                        modifier = Modifier.weight(1f).aspectRatio(1.75f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add back side")
                    }
                }
            }

            // Quick actions
            QuickActions(f, onLaunch)

            SectionCard("Person", Icons.Default.Person) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Title", f.prefix, Modifier.width(88.dp)) { v -> vm.updateFields { it.copy(prefix = v) } }
                    Field("Name", f.name, Modifier.weight(1f), cap = KeyboardCapitalization.Words, flagged = FieldKeys.NAME in flags) { v ->
                        vm.edit(FieldKeys.NAME) { it.copy(name = v) }
                    }
                }
                Field("Designation", f.jobTitle, cap = KeyboardCapitalization.Words, flagged = FieldKeys.TITLE in flags) { v ->
                    vm.edit(FieldKeys.TITLE) { it.copy(jobTitle = v) }
                }
            }

            SectionCard("Company", ImageVector.vectorResource(R.drawable.ic_business)) {
                Field("Company", f.company, cap = KeyboardCapitalization.Words, flagged = FieldKeys.COMPANY in flags) { v ->
                    vm.edit(FieldKeys.COMPANY) { it.copy(company = v) }
                }
                Field("What they do (products / services)", f.business, singleLine = false, cap = KeyboardCapitalization.Sentences) { v ->
                    vm.updateFields { it.copy(business = v) }
                }
                var keywordsText by remember(record.id, vm.refreshKey) { mutableStateOf(f.keywords.joinToString(", ")) }
                Field("Search keywords (comma separated)", keywordsText, singleLine = false) { v ->
                    keywordsText = v
                    vm.updateFields { it.copy(keywords = v.split(',').map { k -> k.trim() }.filter { k -> k.isNotEmpty() }) }
                }
                Text(
                    "Saved in the contact's notes, so you can find this person later by searching a product or service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                WebNoteBlock(vm, record, canEdit)
            }

            SectionCard("Phone, email & website", Icons.Default.Call) {
                ListField("Mobile", f.mobiles, KeyboardType.Phone, FieldKeys.MOBILES in flags) { v -> vm.edit(FieldKeys.MOBILES) { it.copy(mobiles = v) } }
                ListField("Office phone", f.phones, KeyboardType.Phone, FieldKeys.PHONES in flags) { v -> vm.edit(FieldKeys.PHONES) { it.copy(phones = v) } }
                ListField("Fax", f.faxes, KeyboardType.Phone, FieldKeys.FAXES in flags) { v -> vm.edit(FieldKeys.FAXES) { it.copy(faxes = v) } }
                ListField("Email", f.emails, KeyboardType.Email, FieldKeys.EMAILS in flags) { v -> vm.edit(FieldKeys.EMAILS) { it.copy(emails = v) } }
                ListField("Website", f.websites, KeyboardType.Uri, FieldKeys.WEBSITES in flags) { v -> vm.edit(FieldKeys.WEBSITES) { it.copy(websites = v) } }
            }

            SectionCard("Address & GSTIN", Icons.Default.LocationOn) {
                Field("Address", f.address, singleLine = false, flagged = FieldKeys.ADDRESS in flags) { v ->
                    vm.edit(FieldKeys.ADDRESS) { it.copy(address = v) }
                }
                Field("GSTIN", f.gstin, cap = KeyboardCapitalization.Characters, flagged = FieldKeys.GSTIN in flags) { v ->
                    vm.edit(FieldKeys.GSTIN) { it.copy(gstin = v.uppercase()) }
                }
            }

            SectionCard("Notes", ImageVector.vectorResource(R.drawable.ic_notes)) {
                Field(
                    "My notes (where you met, requirement…)", f.notes, singleLine = false, cap = KeyboardCapitalization.Sentences,
                ) { v -> vm.updateFields { it.copy(notes = v) } }
                Field("Other text on card", f.extraInfo, singleLine = false) { v -> vm.updateFields { it.copy(extraInfo = v) } }
            }

            // Raw OCR lines: tap to move into a field
            if (record.rawLines.isNotEmpty()) {
                SectionCard("Text found on card", Icons.Default.Edit) {
                    Text(
                        "Something wrong or missing? Tap a line to put it in the right field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        record.rawLines.forEach { line ->
                            SuggestionChip(onClick = { if (canEdit) assignLine = line.text }, label = { Text(line.text, maxLines = 2) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }

    assignLine?.let { line ->
        AssignDialog(line, onDismiss = { assignLine = null }) { target ->
            vm.edit(target.key) { assign(it, target, line) }
            assignLine = null
        }
    }

    if (waitForWebsite) {
        AlertDialog(
            onDismissRequest = { waitForWebsite = false },
            icon = { Icon(painterResource(R.drawable.ic_web), null) },
            title = { Text("Still reading their website") },
            text = { Text("The note about what this company does will be ready in a few seconds, and it then goes into the contact's notes too.") },
            confirmButton = { TextButton(onClick = { waitForWebsite = false }) { Text("Wait") } },
            dismissButton = { TextButton(onClick = { waitForWebsite = false; onSaveToContacts() }) { Text("Save now") } },
        )
    }

    if (confirmReread) {
        AlertDialog(
            onDismissRequest = { confirmReread = false },
            title = { Text("Read this card again?") },
            text = { Text("The AI reads the card photos again and replaces the details. Your notes are kept.") },
            confirmButton = { TextButton(onClick = { confirmReread = false; vm.rereadWithAi() }) { Text("Read again") } },
            dismissButton = { TextButton(onClick = { confirmReread = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("The scan is removed from this app. A contact already saved to your phone is not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteEditing() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    zoomImage?.let { file ->
        Dialog(onDismissRequest = { zoomImage = null }) {
            Box {
                AsyncImage(
                    model = file,
                    contentDescription = "Card photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
                )
                IconButton(onClick = { zoomImage = null }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
        }
    }
}

/** Sub-groups of the card's event as tick-chips; shows the AI's suggestion for a quick confirm. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SubGroupPicker(vm: AppViewModel, record: CardRecord, canEdit: Boolean) {
    val ev = vm.eventOf(record)
    val groups = vm.subGroupsOf(ev)
    if (ev == null || groups.isEmpty()) {
        // A colleague's card from an event I'm not in: just show the names
        if (record.subGroupNames.isNotEmpty()) {
            Text(
                "Sub-group: " + record.subGroupNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }
    SectionCard("Sub-group", Icons.AutoMirrored.Filled.List, Modifier.padding(top = 4.dp)) {
        if (record.subGroupsSuggested && record.subGroupIds.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_sparkle), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Suggested from the card. Tap to change, or confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) TextButton(onClick = { vm.confirmSubGroups() }) { Text("Looks right") }
            }
        } else if (record.subGroupIds.none { id -> groups.any { it.id == id } }) {
            Text(
                if (canEdit) "Which kind of contact is this? Pick one or more." else "Not in a sub-group yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEachIndexed { i, g ->
                val on = g.id in record.subGroupIds
                FilterChip(
                    selected = on,
                    onClick = { if (canEdit) vm.toggleSubGroup(g.id) },
                    label = { Text(g.name) },
                    leadingIcon = {
                        if (on) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        else Box(Modifier.size(10.dp).clip(CircleShape).background(subGroupColor(i)))
                    },
                )
            }
        }
    }
}

@Composable
private fun CardImage(file: File?, label: String, modifier: Modifier, onClick: (File) -> Unit) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.75f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = file != null) { file?.let(onClick) },
        ) {
            if (file != null) {
                AsyncImage(model = file, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActions(f: CardFields, onLaunch: (Intent) -> Unit) {
    val actions = ArrayList<QuickAction>()
    (f.mobiles.firstOrNull() ?: f.phones.firstOrNull())?.let { n ->
        actions += QuickAction("Call", Icons.Default.Call, MaterialTheme.colorScheme.tertiary,
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialable(n))))
    }
    f.mobiles.firstOrNull()?.let { m ->
        actions += QuickAction("WhatsApp", Icons.AutoMirrored.Filled.Send, Color(0xFF1FA855),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + whatsappNumber(m))))
    }
    f.emails.firstOrNull()?.let { e ->
        actions += QuickAction("Email", Icons.Default.Email, MaterialTheme.colorScheme.primary,
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e")))
    }
    if (f.address.isNotBlank()) {
        actions += QuickAction("Map", Icons.Default.LocationOn, MaterialTheme.colorScheme.secondary,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(f.address))))
    }
    if (actions.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEach { a ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onLaunch(a.intent) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(a.color), contentAlignment = Alignment.Center) {
                    Icon(a.icon, null, Modifier.size(22.dp), tint = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Text(a.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private class QuickAction(val label: String, val icon: ImageVector, val color: Color, val intent: Intent)

private fun dialable(n: String) = n.filter { it.isDigit() || it == '+' }

private fun whatsappNumber(n: String): String {
    val d = n.filter { it.isDigit() }
    return if (d.length == 10) "91$d" else d
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    cap: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboard: KeyboardType = KeyboardType.Text,
    flagged: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        readOnly = LocalReadOnly.current,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = cap, keyboardType = keyboard),
        colors = fieldColors(flagged),
        supportingText = if (flagged) { { Text("Please check", color = CheckOrange) } } else null,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/** true when viewing a colleague's card: fields can be read and copied but not changed. */
private val LocalReadOnly = compositionLocalOf { false }

/** Orange = "the reader wasn't fully sure, please compare with the card". */
private val CheckOrange = Color(0xFFE07B00)

@Composable
private fun fieldColors(flagged: Boolean) =
    if (flagged) OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CheckOrange,
        unfocusedBorderColor = CheckOrange,
        focusedLabelColor = CheckOrange,
        unfocusedLabelColor = CheckOrange,
    ) else OutlinedTextFieldDefaults.colors()

@Composable
private fun ReadByBanner(
    record: CardRecord,
    note: String?,
    aiReady: Boolean,
    onReadWithAi: () -> Unit,
    onSetUp: () -> Unit,
) {
    val byAi = record.readBy == CardRecord.READ_BY_AI
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (byAi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (byAi) "✓ Read by AI" else "Read by the phone's basic reader",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                when {
                    aiReady -> TextButton(onClick = onReadWithAi) { Text(if (byAi) "Read again" else "Read with AI") }
                    !byAi -> TextButton(onClick = onSetUp) { Text("Set up AI") }
                }
            }
            val message = when {
                note != null -> note
                !byAi && !aiReady -> "Turn on free AI reading in Settings for much better accuracy."
                else -> null
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (record.qrTexts.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(painterResource(R.drawable.ic_qr), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("QR code read: its details were added and used to check the card",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (record.uncertain.isNotEmpty()) {
                Text(
                    "Fields marked in orange need a quick check against the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CheckOrange,
                )
            }
        }
    }
}

/** "From their website" note: being read / the note / a button to read it. */
@Composable
private fun WebNoteBlock(vm: AppViewModel, record: CardRecord, canEdit: Boolean) {
    val site = vm.websiteOf(record)
    if (site == null && record.webNote.isBlank()) return
    val host = (record.webNoteUrl.ifBlank { site.orEmpty() })
        .removePrefix("https://").removePrefix("http://").substringBefore('/')
    when {
        record.id in vm.webBusyIds -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text("Reading their website ($host)…", style = MaterialTheme.typography.bodySmall)
        }
        record.webNote.isNotBlank() -> {
            Field("From their website", record.webNote, singleLine = false, cap = KeyboardCapitalization.Sentences) { v -> vm.setWebNote(v) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_web), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("From $host · saved in the contact's notes", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (canEdit && vm.aiReady && site != null) TextButton(onClick = { vm.readWebsiteNow() }) { Text("Read again") }
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_web), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    !vm.aiReady -> "Turn on AI reading to get a short note from their website."
                    record.webCheckedAt > 0 -> "Couldn't get a note from their website."
                    else -> "Get a short note on what they do from their website."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (canEdit && vm.aiReady) TextButton(onClick = { vm.readWebsiteNow() }) {
                Text(if (record.webCheckedAt > 0) "Try again" else "Read website")
            }
        }
    }
}

/** Editable list such as several mobile numbers. */
@Composable
private fun ListField(label: String, values: List<String>, keyboard: KeyboardType, flagged: Boolean, onChange: (List<String>) -> Unit) {
    val readOnly = LocalReadOnly.current
    Column(Modifier.fillMaxWidth()) {
        values.forEachIndexed { i, v ->
            OutlinedTextField(
                value = v,
                onValueChange = { nv -> onChange(values.toMutableList().also { it[i] = nv }) },
                readOnly = readOnly,
                label = { Text(if (values.size > 1) "$label ${i + 1}" else label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                colors = fieldColors(flagged),
                supportingText = if (flagged && i == values.lastIndex) { { Text("Please check", color = CheckOrange) } } else null,
                trailingIcon = if (readOnly) null else {
                    {
                        IconButton(onClick = { onChange(values.toMutableList().also { it.removeAt(i) }) }) {
                            Icon(Icons.Default.Close, "Remove")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        if (!readOnly) TextButton(onClick = { onChange(values + "") }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add ${label.lowercase()}")
        }
    }
}

private enum class Target(val label: String, val key: String) {
    NAME("Name", FieldKeys.NAME), TITLE("Designation", FieldKeys.TITLE), COMPANY("Company", FieldKeys.COMPANY),
    MOBILE("Mobile", FieldKeys.MOBILES), PHONE("Office phone", FieldKeys.PHONES), FAX("Fax", FieldKeys.FAXES),
    EMAIL("Email", FieldKeys.EMAILS), WEBSITE("Website", FieldKeys.WEBSITES), ADDRESS("Address (add to)", FieldKeys.ADDRESS),
    GSTIN("GSTIN", FieldKeys.GSTIN), NOTES("My notes", "notes"), OTHER("Other text", "extraInfo"),
}

@Composable
private fun AssignDialog(line: String, onDismiss: () -> Unit, onPick: (Target) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use this text as…") },
        text = {
            Column {
                Text("\"$line\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Target.entries.forEach { t ->
                    Text(
                        t.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(t) }
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun assign(f: CardFields, target: Target, line: String): CardFields {
    val text = line.trim()
    fun numbers(): List<String> = CardParser.extractPhones(text).first.map { it.first }.ifEmpty { listOf(text) }
    fun append(old: String, sep: String) = if (old.isBlank()) text else old + sep + text
    return when (target) {
        Target.NAME -> f.copy(name = CardParser.smartCase(text))
        Target.TITLE -> f.copy(jobTitle = CardParser.smartCase(text))
        Target.COMPANY -> f.copy(company = text)
        Target.MOBILE -> f.copy(mobiles = (f.mobiles + numbers()).distinct())
        Target.PHONE -> f.copy(phones = (f.phones + numbers()).distinct())
        Target.FAX -> f.copy(faxes = (f.faxes + numbers()).distinct())
        Target.EMAIL -> f.copy(emails = (f.emails + text.replace(" ", "").lowercase()).distinct())
        Target.WEBSITE -> f.copy(websites = (f.websites + text.replace(" ", "").lowercase()).distinct())
        Target.ADDRESS -> f.copy(address = append(f.address, ", "))
        Target.GSTIN -> f.copy(gstin = text.uppercase().filter { it.isLetterOrDigit() })
        Target.NOTES -> f.copy(notes = append(f.notes, "\n"))
        Target.OTHER -> f.copy(extraInfo = append(f.extraInfo, "\n"))
    }
}
