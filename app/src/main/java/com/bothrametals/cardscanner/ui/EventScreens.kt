package com.bothrametals.cardscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bothrametals.cardscanner.R
import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.EventGroup
import com.bothrametals.cardscanner.data.SubGroup

/**
 * The strip at the top of the home screen: which event you're in, and a menu to switch,
 * create a new event or open a past one.
 */
@Composable
fun EventBar(vm: AppViewModel, onManage: (EventGroup) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }
    val current = vm.currentEvent

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (current != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Default.DateRange,
                size = 38.dp,
                container = if (current != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                tint = if (current != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { menu = true },
            ) {
                Text(
                    if (current == null) "Event" else if (current.closed) "Past event" else "Scanning into",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current?.label ?: "All cards (no event)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Default.ArrowDropDown, "Choose event")
                }
                if (current != null) {
                    val n = vm.cardsIn(current.id).size
                    val people = vm.cardsIn(current.id).map { it.scannedBy }.filter { it.isNotBlank() }.distinct().size
                    Text(
                        "$n card${if (n == 1) "" else "s"}" + if (people > 1) " · scanned by $people people" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (current != null) {
                TextButton(onClick = { onManage(current) }) { Text("Manage") }
            } else {
                TextButton(onClick = { showNew = true }) { Text("+ New event") }
            }
            Box {
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("All cards (no event)") },
                        leadingIcon = { if (current == null) Icon(Icons.Default.Check, null) },
                        onClick = { menu = false; vm.selectEvent("") },
                    )
                    if (vm.openEvents.isNotEmpty()) HorizontalDivider()
                    vm.openEvents.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { if (current?.id == e.id) Icon(Icons.Default.Check, null) },
                            onClick = { menu = false; vm.selectEvent(e.id) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("+ New event…") }, onClick = { menu = false; showNew = true })
                    if (vm.pastEvents.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Past events (${vm.pastEvents.size})…") }, onClick = { menu = false; showPast = true })
                    }
                }
            }
        }
    }

    if (showNew) NewEventDialog(vm, onDismiss = { showNew = false })

    if (showPast) {
        AlertDialog(
            onDismissRequest = { showPast = false },
            title = { Text("Past events") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    vm.pastEvents.forEach { e ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectEvent(e.id); showPast = false }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(e.label, style = MaterialTheme.typography.bodyLarge)
                            Text("${vm.cardsIn(e.id).size} cards", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPast = false }) { Text("Close") } },
        )
    }
}

/** Team members to pick from (cloud mode), shown as checkboxes. */
@Composable
private fun MemberPicker(vm: AppViewModel, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    if (!vm.cloudOn) return
    LaunchedEffect(Unit) { vm.loadTeamForEvents() }
    Text("Who's at this event?", style = MaterialTheme.typography.labelLarge)
    val others = vm.members.filter { it.email != vm.myEmail }
    if (others.isEmpty()) {
        Text(
            "No other team members yet (an admin adds them in Settings → Team members).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    others.forEach { m ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(if (m.email in selected) selected - m.email else selected + m.email) },
        ) {
            Checkbox(checked = m.email in selected, onCheckedChange = { on -> onChange(if (on) selected + m.email else selected - m.email) })
            Column {
                Text(m.name.ifBlank { m.email.substringBefore('@') }, style = MaterialTheme.typography.bodyMedium)
                Text(m.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NewEventDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New event") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Event name") },
                    placeholder = { Text("e.g. Aluminium India 2026") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = place, onValueChange = { place = it }, label = { Text("Place (optional)") },
                    placeholder = { Text("e.g. Mumbai") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                )
                MemberPicker(vm, people) { people = it }
                Text(
                    "Every card scanned while this event is selected goes into it" +
                        (if (vm.cloudOn) ", for everyone you add." else "."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.createEvent(name, place, people.toList()); onDismiss() }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Manage an event: rename, add/remove people, close or reopen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(vm: AppViewModel, onBack: () -> Unit) {
    val event = vm.managingEvent
    val snackbar = remember { SnackbarHostState() }
    BusyAndToast(vm, snackbar)
    if (event == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val canManage = !vm.cloudOn || event.canManage(vm.myEmail, vm.isAdmin)
    var name by remember(event.id) { mutableStateOf(event.name) }
    var place by remember(event.id) { mutableStateOf(event.place) }
    var people by remember(event.id, event.members) { mutableStateOf(event.members.filter { it != vm.myEmail }.toSet()) }
    var confirmClose by remember { mutableStateOf(false) }
    val cards = vm.cardsIn(event.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (event.closed) "Past event" else "Event") },
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
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Event name") }, singleLine = true,
                        readOnly = !canManage, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = place, onValueChange = { place = it }, label = { Text("Place") }, singleLine = true,
                        readOnly = !canManage, modifier = Modifier.fillMaxWidth(),
                    )
                    if (canManage) {
                        MemberPicker(vm, people) { people = it }
                        Button(onClick = {
                            vm.updateEvent(event.copy(name = name.trim().ifBlank { event.name }, place = place.trim(),
                                members = if (vm.cloudOn) (listOf(vm.myEmail) + people).distinct() else event.members))
                            onBack()
                        }) { Text("Save") }
                    } else if (vm.cloudOn) {
                        Text("Created by ${event.createdBy}. Only they or an admin can change this event.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("People: " + event.members.joinToString(", ") { it.substringBefore('@') },
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SubGroupsSection(vm, event, cards)

            // Who scanned how many
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${cards.size} cards at this event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    cards.groupBy { it.scannedBy.ifBlank { "Unknown" } }.toList().sortedByDescending { it.second.size }.forEach { (who, list) ->
                        Row {
                            Text(who, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${list.size}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                    Text("Tip: with this event selected on the home screen, ⋮ → Save to Excel exports just these cards.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (canManage) {
                if (event.closed) {
                    OutlinedButton(onClick = { vm.reopenEvent(event); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Reopen event") }
                } else {
                    OutlinedButton(onClick = { confirmClose = true }, modifier = Modifier.fillMaxWidth()) { Text("Close event") }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close \"${event.name}\"?") },
            text = { Text("It moves to Past events. All its cards stay, and you can reopen it any time.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; vm.closeEvent(event); onBack() }) { Text("Close event") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancel") } },
        )
    }
}

/** Chip on the card details screen to move the card into another event. */
/** Sub-groups of an event: admins add, rename and delete them; anyone can sort their unsorted cards. */
@Composable
private fun SubGroupsSection(vm: AppViewModel, event: EventGroup, cards: List<CardRecord>) {
    val groups = vm.subGroupsOf(event)
    val canManage = vm.canManageSubGroups
    val unsorted = vm.unsortedCards(event).size
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SubGroup?>(null) }
    var deleting by remember { mutableStateOf<SubGroup?>(null) }

    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.AutoMirrored.Filled.List)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sub-groups", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Sort the people you meet, e.g. Scrap, Finished products. A card can be in several.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (groups.isEmpty()) {
                Text(
                    if (canManage) "No sub-groups yet. Add one for each kind of visitor." else "No sub-groups yet. Only an admin can add them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            groups.forEachIndexed { i, g ->
                val n = cards.count { g.id in it.subGroupIds }
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(subGroupColor(i)))
                    Spacer(Modifier.width(12.dp))
                    Text(g.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$n card${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (canManage) {
                        IconButton(onClick = { renaming = g }) { Icon(Icons.Default.Edit, "Rename ${g.name}", Modifier.size(20.dp)) }
                        IconButton(onClick = { deleting = g }) { Icon(Icons.Default.Delete, "Delete ${g.name}", Modifier.size(20.dp)) }
                    }
                }
            }
            if (canManage) {
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add sub-group")
                }
            }
            if (groups.isNotEmpty() && unsorted > 0) {
                FilledTonalButton(onClick = { vm.suggestSubGroupsFor(event) }) {
                    Icon(painterResource(R.drawable.ic_sparkle), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sort $unsorted unsorted card${if (unsorted == 1) "" else "s"}" + if (vm.aiReady) " with AI" else "")
                }
                Text(
                    "Suggests sub-groups from what each company does. Check them on each card.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (adding) {
        NameDialog(title = "New sub-group", initial = "", confirm = "Add", onDismiss = { adding = false }) { n ->
            vm.addSubGroup(event, n)
            adding = false
        }
    }
    renaming?.let { g ->
        NameDialog(title = "Rename sub-group", initial = g.name, confirm = "Save", onDismiss = { renaming = null }) { n ->
            vm.renameSubGroup(event, g.id, n)
            renaming = null
        }
    }
    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${g.name}\"?") },
            text = { Text("The cards stay in this event. They just lose this sub-group.") },
            confirmButton = { TextButton(onClick = { vm.deleteSubGroup(event, g.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text("Name, e.g. Scrap") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CardEventPicker(vm: AppViewModel, eventId: String, eventName: String, enabled: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { if (enabled) menu = true }, enabled = enabled || eventName.isNotBlank()) {
            Text(if (eventName.isBlank()) "No event" else "Event: $eventName", maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (enabled) Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("No event") }, leadingIcon = { if (eventId.isBlank()) Icon(Icons.Default.Check, null) },
                onClick = { menu = false; vm.setCardEvent("") })
            vm.openEvents.forEach { e ->
                DropdownMenuItem(
                    text = { Text(e.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { if (e.id == eventId) Icon(Icons.Default.Check, null) },
                    onClick = { menu = false; vm.setCardEvent(e.id) },
                )
            }
        }
    }
}

/** Shown right after scanning someone who's already in the company's cards. */
@Composable
fun DuplicateDialog(vm: AppViewModel) {
    val m = vm.duplicateMatch ?: return
    AlertDialog(
        onDismissRequest = { vm.duplicateKeepBoth() },
        title = { Text(if (m.sameEvent) "Already scanned at this event" else "Already scanned") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.describeDuplicate(m))
                Button(onClick = { vm.duplicateOpenExisting() }, modifier = Modifier.fillMaxWidth()) { Text("Open existing card") }
                OutlinedButton(onClick = { vm.duplicateKeepBoth() }, modifier = Modifier.fillMaxWidth()) { Text("Keep both") }
                TextButton(onClick = { vm.duplicateDiscard() }, modifier = Modifier.fillMaxWidth()) { Text("Discard this scan") }
            }
        },
        confirmButton = {},
    )
}
