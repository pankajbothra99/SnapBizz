package com.bothrametals.cardscanner.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bothrametals.cardscanner.data.CardRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onScan: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareCsv: () -> Unit,
    onShareVcf: () -> Unit,
    onOpenUrl: (Intent) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    BusyAndToast(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Card Scanner", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${vm.cards.size} cards scanned",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Pick card photos from gallery") }, onClick = { menuOpen = false; onPickFromGallery() })
                        DropdownMenuItem(
                            text = { Text("Save all to Excel (CSV)…") },
                            enabled = vm.cards.isNotEmpty(),
                            onClick = { menuOpen = false; onSaveCsv() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share Excel (CSV)") },
                            enabled = vm.cards.isNotEmpty(),
                            onClick = { menuOpen = false; onShareCsv() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share all as vCard (.vcf)") },
                            enabled = vm.cards.isNotEmpty(),
                            onClick = { menuOpen = false; onShareVcf() },
                        )
                        DropdownMenuItem(text = { Text("How to use") }, onClick = { menuOpen = false; showAbout = true })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Scan card") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (vm.cards.isEmpty()) {
                EmptyState(onScan, onPickFromGallery)
            } else {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search name, company, city, number…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
                val list = vm.filteredCards
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { card -> CardRow(vm, card) { vm.open(card) } }
                    if (list.isEmpty()) {
                        item {
                            Text(
                                "No cards match \"${vm.query}\"",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
            title = { Text("How to use") },
            text = {
                Text(
                    "1. Tap Scan card and hold the phone over the card. It's captured automatically.\n" +
                        "2. To add the back side, tap the + in the scanner and scan the back. Tap Done.\n" +
                        "3. Check the details. Tap any line under \"Text found on card\" to move it into the right field.\n" +
                        "4. Tap Save to Contacts, then Save on the next screen.\n\n" +
                        "All cards stay on this phone. Use the menu to save them all to Excel, for example into your OneDrive folder.\n\n" +
                        "Reading happens on the phone. No internet needed."
                )
            },
        )
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit, onPickFromGallery: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 180.dp, height = 104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text("Scan your first business card", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the front and back. The app reads the name, numbers, email, address and GSTIN, and saves them to your phone contacts.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPickFromGallery) { Text("Or pick photos from gallery") }
    }
}

@Composable
private fun CardRow(vm: AppViewModel, card: CardRecord, onClick: () -> Unit) {
    val f = card.fields
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val img = vm.imageFile(card.frontImage)
            Box(
                Modifier
                    .size(width = 84.dp, height = 50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (img != null) {
                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(f.jobTitle, f.company).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(f.mobiles.firstOrNull() ?: f.phones.firstOrNull(), dateLabel(card.createdAt)).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.savedToContacts) {
                Icon(Icons.Default.CheckCircle, "Saved to contacts", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun dateLabel(t: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(t))

/** Shows the "Reading card…" dialog and one-off snackbar messages. */
@Composable
fun BusyAndToast(vm: AppViewModel, snackbar: SnackbarHostState) {
    val msg = vm.toast
    androidx.compose.runtime.LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.toast = null
        }
    }
    val busy = vm.busyMessage
    if (busy != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Card {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(busy)
                }
            }
        }
    }
}
