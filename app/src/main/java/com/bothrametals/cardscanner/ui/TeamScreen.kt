package com.bothrametals.cardscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Admin only: add or remove staff. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(vm: AppViewModel, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    BusyAndToast(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Team") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { vm.loadMembers() }) { Icon(Icons.Default.Refresh, "Refresh") } },
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
                    Text("Add a team member", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Enter the email they will sign in with (their Gmail, or the email they use for \"Sign in with email\"). " +
                            "They install the app, sign in, and get access straight away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = admin, onCheckedChange = { admin = it })
                        Text("Admin (can see private cards and manage the team)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = {
                        vm.addMember(email, name, admin)
                        email = ""; name = ""; admin = false
                    }, enabled = email.contains('@')) { Text("Add to team") }
                }
            }

            Text("Members (${vm.members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (vm.members.isEmpty()) {
                        Text("Loading…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vm.members.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name.ifBlank { m.email.substringBefore('@') }, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    m.email + if (m.isAdmin) "  ·  Admin" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (m.email != vm.myEmail) {
                                IconButton(onClick = { confirmRemove = m.email }) { Icon(Icons.Default.Delete, "Remove") }
                            } else {
                                Text("You", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                            }
                        }
                    }
                }
            }
            Text(
                "When someone leaves, remove them here. They lose access straight away, and all the cards they scanned stay with the company.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    confirmRemove?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove $e?") },
            text = { Text("They won't be able to see the company's cards any more. Cards they scanned stay with the company.") },
            confirmButton = { TextButton(onClick = { vm.removeMember(e); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}
