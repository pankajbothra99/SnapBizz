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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
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

    BusyAndToast(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onShareVcf) { Icon(Icons.Default.Share, "Share contact") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete card") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onSaveToContacts,
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
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
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

            // Card photos
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardImage(vm.imageFile(record.frontImage), "Front", Modifier.weight(1f)) { zoomImage = it }
                val back = vm.imageFile(record.backImage)
                if (back != null) {
                    CardImage(back, "Back", Modifier.weight(1f)) { zoomImage = it }
                } else {
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Title", f.prefix, Modifier.width(88.dp)) { v -> vm.updateFields { it.copy(prefix = v) } }
                Field("Name", f.name, Modifier.weight(1f), cap = KeyboardCapitalization.Words) { v -> vm.updateFields { it.copy(name = v) } }
            }
            Field("Designation", f.jobTitle, cap = KeyboardCapitalization.Words) { v -> vm.updateFields { it.copy(jobTitle = v) } }
            Field("Company", f.company, cap = KeyboardCapitalization.Words) { v -> vm.updateFields { it.copy(company = v) } }

            ListField("Mobile", f.mobiles, KeyboardType.Phone) { v -> vm.updateFields { it.copy(mobiles = v) } }
            ListField("Office phone", f.phones, KeyboardType.Phone) { v -> vm.updateFields { it.copy(phones = v) } }
            ListField("Fax", f.faxes, KeyboardType.Phone) { v -> vm.updateFields { it.copy(faxes = v) } }
            ListField("Email", f.emails, KeyboardType.Email) { v -> vm.updateFields { it.copy(emails = v) } }
            ListField("Website", f.websites, KeyboardType.Uri) { v -> vm.updateFields { it.copy(websites = v) } }

            Field("Address", f.address, singleLine = false) { v -> vm.updateFields { it.copy(address = v) } }
            Field("GSTIN", f.gstin, cap = KeyboardCapitalization.Characters) { v -> vm.updateFields { it.copy(gstin = v.uppercase()) } }
            Field(
                "My notes (where you met, requirement…)", f.notes, singleLine = false, cap = KeyboardCapitalization.Sentences,
            ) { v -> vm.updateFields { it.copy(notes = v) } }
            Field("Other text on card", f.extraInfo, singleLine = false) { v -> vm.updateFields { it.copy(extraInfo = v) } }

            // Raw OCR lines: tap to move into a field
            if (record.rawLines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Text found on card", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Something wrong or missing? Tap a line to put it in the right field.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    record.rawLines.forEach { line ->
                        SuggestionChip(onClick = { assignLine = line.text }, label = { Text(line.text, maxLines = 2) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    assignLine?.let { line ->
        AssignDialog(line, onDismiss = { assignLine = null }) { target ->
            vm.updateFields { assign(it, target, line) }
            assignLine = null
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(f: CardFields, onLaunch: (Intent) -> Unit) {
    val mobile = f.mobiles.firstOrNull() ?: f.phones.firstOrNull()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mobile != null) {
            FilledTonalButton(onClick = { onLaunch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialable(mobile)))) }) {
                Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Call")
            }
        }
        f.mobiles.firstOrNull()?.let { m ->
            FilledTonalButton(onClick = {
                onLaunch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + whatsappNumber(m))))
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("WhatsApp")
            }
        }
        f.emails.firstOrNull()?.let { e ->
            FilledTonalButton(onClick = { onLaunch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) }) {
                Icon(Icons.Default.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Email")
            }
        }
        if (f.address.isNotBlank()) {
            FilledTonalButton(onClick = {
                onLaunch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(f.address))))
            }) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Map")
            }
        }
    }
}

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
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = cap, keyboardType = keyboard),
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/** Editable list such as several mobile numbers. */
@Composable
private fun ListField(label: String, values: List<String>, keyboard: KeyboardType, onChange: (List<String>) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        values.forEachIndexed { i, v ->
            OutlinedTextField(
                value = v,
                onValueChange = { nv -> onChange(values.toMutableList().also { it[i] = nv }) },
                label = { Text(if (values.size > 1) "$label ${i + 1}" else label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                trailingIcon = {
                    IconButton(onClick = { onChange(values.toMutableList().also { it.removeAt(i) }) }) {
                        Icon(Icons.Default.Close, "Remove")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        TextButton(onClick = { onChange(values + "") }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add ${label.lowercase()}")
        }
    }
}

private enum class Target(val label: String) {
    NAME("Name"), TITLE("Designation"), COMPANY("Company"), MOBILE("Mobile"), PHONE("Office phone"),
    FAX("Fax"), EMAIL("Email"), WEBSITE("Website"), ADDRESS("Address (add to)"), GSTIN("GSTIN"),
    NOTES("My notes"), OTHER("Other text"),
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
