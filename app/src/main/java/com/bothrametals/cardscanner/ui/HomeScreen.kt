package com.bothrametals.cardscanner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.bothrametals.cardscanner.R
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.EventGroup
import com.bothrametals.cardscanner.data.SubGroups
import com.bothrametals.cardscanner.util.DateGroups

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
    onOpenSettings: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val scope = vm.scopeCards
    val isEmpty = scope.isEmpty()

    BusyAndToast(vm, snackbar)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(Modifier.size(34.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Card Scanner", style = MaterialTheme.typography.titleLarge)
                            Text(
                                (vm.member?.teamName?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") +
                                    "${vm.cards.size} card${if (vm.cards.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = brandBar(),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    if (vm.cloudOn) {
                        IconButton(onClick = { vm.syncNow() }, enabled = !vm.syncing) { Icon(Icons.Default.Refresh, "Sync now") }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Import card photos from Gallery") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_gallery), null) },
                            onClick = { menuOpen = false; onPickFromGallery() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.currentEvent != null) "Save this event to Excel (CSV)…" else "Save all to Excel (CSV)…") },
                            enabled = vm.cards.isNotEmpty(),
                            onClick = { menuOpen = false; onSaveCsv() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share Excel (CSV)") },
                            enabled = vm.cards.isNotEmpty(),
                            onClick = { menuOpen = false; onShareCsv() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.currentEvent != null) "Share this event as vCard (.vcf)" else "Share all as vCard (.vcf)") },
                            enabled = vm.cards.isNotEmpty(),
                            onClick = { menuOpen = false; onShareVcf() },
                        )
                        DropdownMenuItem(
                            text = { Text("How to use") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; showAbout = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isEmpty) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = onPickFromGallery,
                        icon = { Icon(painterResource(R.drawable.ic_gallery), null, Modifier.size(20.dp)) },
                        text = { Text("Gallery") },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                        modifier = Modifier.height(44.dp),
                    )
                    ExtendedFloatingActionButton(
                        onClick = onScan,
                        icon = { Icon(painterResource(R.drawable.ic_camera), null) },
                        text = { Text("Scan card") },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EventBar(vm, onManage = { vm.openManageEvent(it) })
            if (vm.cloudOn) {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = !vm.showMineOnly,
                        onClick = { vm.showMineOnly = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(if (vm.isAdmin) "All cards" else "Company cards") }
                    SegmentedButton(
                        selected = vm.showMineOnly,
                        onClick = { vm.showMineOnly = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("My cards") }
                }
                vm.syncStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
            }
            if (isEmpty) {
                EmptyState(vm, onScan, onPickFromGallery, onOpenSettings)
            } else {
                CardList(vm, scope, onOpenSettings, onOpenUrl)
            }
        }
    }

    vm.batchSummary?.let { b ->
        AlertDialog(
            onDismissRequest = { vm.batchSummary = null },
            icon = { IconBadge(if (b.duplicates > 0) Icons.Default.Warning else Icons.Default.CheckCircle, size = 44.dp) },
            title = { Text(if (b.stopped) "Import stopped" else "Import finished") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${b.added} card${if (b.added == 1) "" else "s"} added" +
                        (vm.currentEvent?.let { " to \"${it.name}\"" } ?: ""))
                    if (b.duplicates > 0) Text("${b.duplicates} possible duplicate${if (b.duplicates == 1) "" else "s"}: please review",
                        color = MaterialTheme.colorScheme.secondary)
                    if (b.byPhone > 0) Text("${b.byPhone} read by the phone's basic reader" +
                        (if (b.note != null) " (${b.note})" else "") + ". You can read them again with AI later.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Fields marked in orange on each card need a quick check.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                if (b.duplicates > 0) {
                    TextButton(onClick = { vm.batchSummary = null; vm.showDuplicatesOnly = true }) { Text("Review duplicates") }
                } else {
                    TextButton(onClick = { vm.batchSummary = null }) { Text("OK") }
                }
            },
            dismissButton = if (b.duplicates > 0) {
                { TextButton(onClick = { vm.batchSummary = null }) { Text("Later") } }
            } else null,
        )
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
                        "Have many cards already photographed? Tap Gallery and pick them all at once.\n\n" +
                        "Tap the numbers at the top to see, for example, only the cards not yet in your contacts.\n\n" +
                        "Cards are backed up to your company account when you're signed in, and card photos are also saved in your Gallery album \"Card Scanner\". " +
                        "Use the menu to save all cards to Excel, for example into your OneDrive folder.\n\n" +
                        "Scanning works offline too. Cards back up when the internet is back."
                )
            },
        )
    }
}

@Composable
private fun CardList(vm: AppViewModel, scope: List<CardRecord>, onOpenSettings: () -> Unit, onOpenUrl: (Intent) -> Unit) {
    val list = vm.filteredCards
    val groups = DateGroups.group(list, { it.createdAt }, System.currentTimeMillis())
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 168.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "stats") { StatsRow(vm, scope) }
        val ev = vm.currentEvent
        if (ev != null && ev.subGroups.isNotEmpty()) {
            item(key = "subgroups") { SubGroupFilterRow(vm, ev, scope) }
        }
        item(key = "search") {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search name, company, product, city, number…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
        if (vm.aiKey.isBlank()) {
            item(key = "b_ai") {
                InfoBanner(
                    text = "Get much better accuracy: turn on free AI reading (2-minute setup).",
                    action = "Set up",
                    onClick = onOpenSettings,
                )
            }
        } else if (vm.aiReady && vm.offlineCards.isNotEmpty()) {
            item(key = "b_offline") {
                val n = vm.offlineCards.size
                InfoBanner(
                    text = "$n card${if (n == 1) " was" else "s were"} read offline by the phone. Read ${if (n == 1) "it" else "them"} again with AI for better accuracy.",
                    action = "Read with AI",
                    onClick = { vm.rereadAllOffline() },
                )
            }
        }
        if (vm.showDuplicatesOnly) {
            item(key = "b_dup") {
                InfoBanner(
                    text = "Showing possible duplicates. Open each one to keep it, or delete it.",
                    action = "Show all",
                    onClick = { vm.showDuplicatesOnly = false },
                )
            }
        }
        if (vm.subGroupFilter == SubGroups.NOT_SORTED) {
            item(key = "b_unsorted") {
                InfoBanner(
                    text = "Cards in no sub-group yet. Open each one to pick its sub-group" +
                        (if (ev != null && vm.unsortedCards(ev).isNotEmpty()) ", or let the app suggest them." else "."),
                    action = if (ev != null && vm.unsortedCards(ev).isNotEmpty()) "Suggest" else "Show all",
                    onClick = {
                        if (ev != null && vm.unsortedCards(ev).isNotEmpty()) {
                            vm.suggestSubGroupsFor(ev)
                        } else {
                            vm.subGroupFilter = ""
                        }
                    },
                )
            }
        }
        if (vm.showNotSavedOnly) {
            item(key = "b_notsaved") {
                InfoBanner(
                    text = "Showing cards not yet saved to this phone's contacts.",
                    action = "Show all",
                    onClick = { vm.showNotSavedOnly = false },
                )
            }
        }
        groups.forEach { (label, cardsInGroup) ->
            item(key = "h_$label") {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            items(cardsInGroup, key = { it.id }) { card ->
                CardRow(vm, card, showEvent = vm.currentEvent == null, onCall = onOpenUrl) { vm.open(card) }
            }
        }
        if (list.isEmpty()) {
            item(key = "none") {
                Text(
                    if (vm.query.isNotBlank()) "No cards match \"${vm.query}\"" else "Nothing to show here",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Three numbers at the top: all cards, this week, not yet in contacts (tap to filter). */
@Composable
private fun StatsRow(vm: AppViewModel, scope: List<CardRecord>) {
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val week = scope.count { it.createdAt >= weekAgo }
    val notSaved = scope.count { !it.savedToContacts }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            value = scope.size.toString(),
            label = if (vm.currentEvent != null) "In this event" else "Cards",
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            selected = !vm.showNotSavedOnly && !vm.showDuplicatesOnly,
            onClick = { vm.showNotSavedOnly = false; vm.showDuplicatesOnly = false },
        )
        StatTile(
            value = week.toString(),
            label = "Last 7 days",
            accent = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = notSaved.toString(),
            label = "Not in contacts",
            accent = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            selected = vm.showNotSavedOnly,
            onClick = { vm.showNotSavedOnly = !vm.showNotSavedOnly; vm.showDuplicatesOnly = false },
        )
    }
}

/** Chips to show one sub-group of the selected event: All · Scrap 12 · Finished products 8 · Not sorted 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubGroupFilterRow(vm: AppViewModel, ev: EventGroup, scope: List<CardRecord>) {
    val groups = vm.subGroupsOf(ev)
    val notSorted = scope.count { SubGroups.liveIds(it, ev).isEmpty() }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = vm.subGroupFilter.isBlank(),
            onClick = { vm.subGroupFilter = "" },
            label = { Text("All ${scope.size}") },
        )
        groups.forEachIndexed { i, g ->
            val n = scope.count { g.id in it.subGroupIds }
            FilterChip(
                selected = vm.subGroupFilter == g.id,
                onClick = { vm.subGroupFilter = if (vm.subGroupFilter == g.id) "" else g.id },
                label = { Text("${g.name} $n") },
                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(subGroupColor(i))) },
            )
        }
        if (notSorted > 0) {
            FilterChip(
                selected = vm.subGroupFilter == SubGroups.NOT_SORTED,
                onClick = { vm.subGroupFilter = if (vm.subGroupFilter == SubGroups.NOT_SORTED) "" else SubGroups.NOT_SORTED },
                label = { Text("Not sorted $notSorted") },
            )
        }
    }
}

@Composable
private fun InfoBanner(text: String, action: String, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun EmptyState(vm: AppViewModel, onScan: () -> Unit, onPickFromGallery: () -> Unit, onOpenSettings: () -> Unit) {
    val event = vm.currentEvent
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (vm.aiKey.isBlank()) {
            InfoBanner(
                text = "Tip: turn on free AI reading first for the best accuracy (2-minute setup).",
                action = "Set up",
                onClick = onOpenSettings,
            )
            Spacer(Modifier.height(8.dp))
        }
        ScanIllustration(Modifier.size(width = 260.dp, height = 200.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                event != null -> "No cards in \"${event.name}\" yet"
                vm.showMineOnly -> "You haven't scanned any cards yet"
                else -> "Scan your first business card"
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the front and back. The app reads the name, numbers, email, address, GSTIN and what they do, " +
                "and saves it all to your phone contacts.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_camera), null)
            Spacer(Modifier.width(10.dp))
            Text("Scan a card", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(painterResource(R.drawable.ic_gallery), null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Import many photos from Gallery")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardRow(vm: AppViewModel, card: CardRecord, showEvent: Boolean, onCall: (Intent) -> Unit, onClick: () -> Unit) {
    val f = card.fields
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val img = vm.imageFile(card.frontImage)
            if (img != null) {
                Box(
                    Modifier
                        .size(width = 72.dp, height = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            } else {
                Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    InitialsAvatar(f.name.ifBlank { f.company }, size = 44.dp)
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
                val groupNames = vm.subGroupNames(card)
                val tags = groupNames.isNotEmpty() || card.duplicateOf.isNotBlank() || card.savedToContacts || card.isPrivate ||
                    (showEvent && card.eventName.isNotBlank()) || (card.scannedBy.isNotBlank() && vm.cloudOn)
                if (tags) {
                    Spacer(Modifier.height(5.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val evGroups = vm.subGroupsOf(vm.eventOf(card))
                        groupNames.forEach { n ->
                            Tag(n, subGroupColor(evGroups.indexOfFirst { it.name == n }))
                        }
                        if (card.subGroupsSuggested && groupNames.isNotEmpty()) {
                            Tag("Confirm sub-group", MaterialTheme.colorScheme.onSurfaceVariant, ImageVector.vectorResource(R.drawable.ic_sparkle))
                        }
                        if (card.duplicateOf.isNotBlank()) Tag("Possible duplicate", MaterialTheme.colorScheme.secondary, Icons.Default.Warning)
                        if (card.savedToContacts) Tag("In contacts", MaterialTheme.colorScheme.tertiary, Icons.Default.CheckCircle)
                        if (card.isPrivate) Tag("Private", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Lock)
                        if (showEvent && card.eventName.isNotBlank()) Tag(card.eventName, MaterialTheme.colorScheme.primary, Icons.Default.DateRange)
                        if (card.scannedBy.isNotBlank() && vm.cloudOn) Tag("by ${card.scannedBy}", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val number = f.mobiles.firstOrNull() ?: f.phones.firstOrNull()
            if (number != null) {
                FilledTonalIconButton(
                    onClick = { onCall(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' }))) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) { Icon(Icons.Default.Call, "Call ${f.displayName}", Modifier.size(20.dp)) }
            }
        }
    }
}

/** Shows the "Reading card…" dialog and one-off snackbar messages. */
@Composable
fun BusyAndToast(vm: AppViewModel, snackbar: SnackbarHostState) {
    val msg = vm.toast
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.toast = null
        }
    }
    val busy = vm.busyMessage
    if (busy != null) {
        Dialog(onDismissRequest = {}) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(18.dp))
                        Text(busy, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (vm.busyCancelable) {
                        TextButton(onClick = { vm.stopBatch() }, modifier = Modifier.align(Alignment.End)) { Text("Stop") }
                    }
                }
            }
        }
    }
}
