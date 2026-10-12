package com.bothrametals.cardscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.bothrametals.cardscanner.R
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bothrametals.cardscanner.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onScanSetupQr: () -> Unit,
    onLaunch: (Intent) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var keyDraft by remember(vm.aiKey) { mutableStateOf(vm.aiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var modelsDraft by remember(vm.modelsText) { mutableStateOf(vm.modelsText) }
    var showAdvanced by remember { mutableStateOf(false) }
    val member = vm.member

    BusyAndToast(vm, snackbar)
    SignOutWarning(vm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------ account
            if (member != null) {
                Section("Account", Icons.Default.AccountCircle) {
                    Text(member.email, style = MaterialTheme.typography.bodyLarge)
                    Hint((if (member.isAdmin) "Admin" else "Team member") + " · " + member.teamName.ifBlank { "Company" })
                    vm.syncStatus?.let { Hint(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.syncNow() }, enabled = !vm.syncing) { Text("Back up now") }
                        OutlinedButton(onClick = { vm.syncNow(full = true) }, enabled = !vm.syncing) { Text("Re-download all") }
                    }
                    if (member.isAdmin) {
                        Button(onClick = { vm.openTeam() }) { Text("Team members") }
                    }
                    TextButton(onClick = { vm.requestSignOut() }) { Text("Sign out") }
                }
            }

            // ------------------------------------------------ your name
            Section("Your name", Icons.Default.Person) {
                OutlinedTextField(
                    value = vm.userName,
                    onValueChange = { vm.changeUserName(it) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint("Saved with each card you scan (\"Scanned by\" in Excel and in the contact notes), so the team knows who met whom.")
            }

            // ------------------------------------------------ gallery
            Section("Gallery", ImageVector.vectorResource(R.drawable.ic_gallery)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Save card photos to Gallery", style = MaterialTheme.typography.bodyMedium)
                        Hint("Front and back photos of every card you scan go into the album \"Card Scanner\".")
                    }
                    Switch(checked = vm.saveToGallery, onCheckedChange = { vm.changeSaveToGallery(it) })
                }
                OutlinedButton(onClick = { vm.saveAllToGallery() }) { Text("Save all card photos to Gallery now") }
            }

            // ------------------------------------------------ AI reading
            Section("AI reading (free)", ImageVector.vectorResource(R.drawable.ic_sparkle)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                vm.aiKey.isBlank() -> "Off: no key added yet"
                                vm.aiEnabled -> "On: cards are read by Google Gemini AI when online"
                                else -> "Switched off"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (vm.aiKey.isNotBlank()) {
                            Text("Cards read by AI today on this phone: ${vm.aiReadsToday}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = vm.aiEnabled && vm.aiKey.isNotBlank(), enabled = vm.aiKey.isNotBlank(), onCheckedChange = { vm.changeAiEnabled(it) })
                }

                if (vm.aiKeyFromTeam && member != null && !member.isAdmin) {
                    Hint("Your company's free AI key is used automatically. The admin manages it.")
                } else {
                if (member?.isAdmin == true) Hint("As admin, the key you save here is shared with your whole team automatically.")
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it.trim() },
                    label = { Text("Free Gemini API key") },
                    placeholder = { Text("Paste key here (starts with AIza…)") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveKey(keyDraft) }, enabled = keyDraft != vm.aiKey) { Text("Save key") }
                    OutlinedButton(onClick = { vm.testKey() }, enabled = vm.aiKey.isNotBlank()) { Text("Test") }
                }
                vm.testStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                Spacer(Modifier.height(4.dp))
                Text("How to get your free key (one time, 2 minutes):", style = MaterialTheme.typography.labelLarge)
                Hint(
                    "1. Open Google AI Studio (button below) and sign in with any Google account.\n" +
                        "2. Tap \"Create API key\" and copy it.\n" +
                        "3. Paste it above and tap Save key.\n\n" +
                        "Do NOT add billing or a card in Google. Without billing it stays on the free tier and can never be charged. " +
                        "If the free daily limit runs out, the app reads cards on the phone instead; the limit resets every day around 12:30–1:30 pm India time."
                )
                OutlinedButton(onClick = { onLaunch(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }) {
                    Text("Open Google AI Studio")
                }
                }
            }

            // ------------------------------------------------ company websites
            Section("Company websites", ImageVector.vectorResource(R.drawable.ic_web)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Read their website automatically", style = MaterialTheme.typography.bodyMedium)
                        Hint(
                            "After a card is scanned, the app opens the company's website (printed or from the QR code) " +
                                "and adds a 2–3 line note on what they do, plus product keywords. Uses a little internet and the free AI."
                        )
                    }
                    Switch(checked = vm.autoWebNotes, enabled = vm.aiReady, onCheckedChange = { vm.changeAutoWebNotes(it) })
                }
                if (!vm.aiReady) Hint("Needs AI reading (below) to be on.")
                val older = vm.olderCardsWithoutWebNote.size
                if (vm.aiReady && older > 0) {
                    OutlinedButton(onClick = { vm.readOlderWebsites() }) {
                        Text("Read websites for $older older card${if (older == 1) "" else "s"}")
                    }
                    Hint("Contacts already saved to the phone don't change. Open a card and tap Save to Contacts again to include the note.")
                }
            }

            // ------------------------------------------------ team phones (only needed before company login is set up)
            if (member == null) Section("Team phones", Icons.Default.Share) {
                Hint("Set up a colleague's phone in seconds: show the QR code on this phone, and on their phone tap \"Scan setup QR\".")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showQr = true }, enabled = vm.aiKey.isNotBlank()) { Text("Show setup QR") }
                    OutlinedButton(onClick = onScanSetupQr) { Text("Scan setup QR") }
                }
            }

            // ------------------------------------------------ advanced
            Section("Advanced", Icons.Default.Build) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide advanced" else "Show advanced") }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = modelsDraft,
                        onValueChange = { modelsDraft = it },
                        label = { Text("Free Gemini models (tried in order)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("Each model has its own free daily allowance. Only change this if Google retires a model name.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveModels(modelsDraft) }) { Text("Save") }
                        OutlinedButton(onClick = { vm.resetModels() }) { Text("Reset to default") }
                    }
                }
            }
        }
    }

    if (showQr) {
        val bmp = remember { ImageUtils.qrBitmap(vm.setupCode()) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Setup QR for a team phone") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Setup QR code", modifier = Modifier.size(240.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "On the other phone: Card Scanner → Settings → Scan setup QR.\n" +
                            "This code contains your AI key. Show it only to your own team. Don't post it or send it outside.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } },
        )
    }
}

@Composable
fun SignOutWarning(vm: AppViewModel) {
    if (!vm.signOutWarning) return
    AlertDialog(
        onDismissRequest = { vm.signOutWarning = false },
        title = { Text("Some cards aren't backed up yet") },
        text = {
            Text(
                "There's no internet right now, so a few recent cards haven't reached the cloud. " +
                    "If you sign out now, those cards will be lost from this phone. Connect to the internet and wait a moment first."
            )
        },
        confirmButton = { TextButton(onClick = { vm.signOutWarning = false }) { Text("Wait") } },
        dismissButton = { TextButton(onClick = { vm.doSignOut() }) { Text("Sign out anyway") } },
    )
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
