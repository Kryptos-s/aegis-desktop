package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.ui.components.ConfirmDialog
import com.beemdevelopment.aegis.vault.VaultGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(groupsOf(state)) }
    var renaming by remember { mutableStateOf<VaultGroup?>(null) }
    var deleting by remember { mutableStateOf<VaultGroup?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun persist(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                block()
                state.vaultManager.saveAndBackup()
            }
            groups = groupsOf(state)
            state.refreshEntries()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings["groups"]) },
                navigationIcon = {
                    IconButton(onClick = { state.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings["back"])
                    }
                },
                actions = {
                    TextButton(onClick = { adding = true }) { Text(Strings["add"]) }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        if (groups.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(Strings["no_groups_found"], style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(groups, key = { it.uuid }) { group ->
                    val count = state.entries.count { group.uuid in it.groups }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Strings.plural("entry_count", count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { renaming = group }) {
                            Icon(Icons.Default.Edit, contentDescription = Strings["rename"])
                        }
                        IconButton(onClick = { deleting = group }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = Strings["delete"],
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = Strings["new_group"],
            initial = "",
            onConfirm = { name ->
                adding = false
                if (name.isNotBlank()) {
                    persist { state.vaultManager.vault.addGroup(VaultGroup(name.trim())) }
                }
            },
            onDismiss = { adding = false },
        )
    }

    renaming?.let { group ->
        NameDialog(
            title = Strings["rename"],
            initial = group.name,
            onConfirm = { name ->
                renaming = null
                if (name.isNotBlank()) {
                    // Rename in place: entries reference the group by UUID, so replacing it with a
                    // new group would orphan every entry filed under it.
                    persist { group.name = name.trim() }
                }
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { group ->
        ConfirmDialog(
            title = Strings["delete"],
            message = Strings.format("confirm_delete_group", group.name),
            confirmLabel = Strings["delete"],
            destructive = true,
            onConfirm = {
                deleting = null
                persist { state.vaultManager.vault.removeGroup(group) }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(Strings["save"]) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings["cancel"]) } },
    )
}

private fun groupsOf(state: AppState): List<VaultGroup> =
    if (state.vaultManager.isUnlocked) state.vaultManager.vault.groups.toList() else emptyList()
