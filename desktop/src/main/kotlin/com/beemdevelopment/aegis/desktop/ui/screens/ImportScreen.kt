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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.beemdevelopment.aegis.desktop.io.FileChoosers
import com.beemdevelopment.aegis.desktop.ui.components.PasswordField
import com.beemdevelopment.aegis.desktop.ui.components.PasswordState
import com.beemdevelopment.aegis.importers.DatabaseImporter
import com.beemdevelopment.aegis.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val definitions = remember { DatabaseImporter.getImporters() }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingState by remember { mutableStateOf<DatabaseImporter.State?>(null) }
    var found by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    var duplicates by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    var importErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var variants by remember { mutableStateOf<List<String>>(emptyList()) }

    fun presentResult(result: DatabaseImporter.Result) {
        val existing = if (state.vaultManager.isUnlocked) state.vaultManager.vault.entries else emptyList()
        val entries = result.entries.values.toList()

        found = entries
        duplicates = entries.filter { candidate ->
            existing.any { it.equivalates(candidate) || it.hasSameNameAndIssuer(candidate) }
        }.map { it.uuid }.toSet()
        selected = entries.map { it.uuid }.toSet() - duplicates
        importErrors = result.errors.map { it.toString() }
    }

    fun readFile(definition: DatabaseImporter.Definition, path: Path) {
        busy = true
        error = null
        scope.launch {
            try {
                val importState = withContext(Dispatchers.IO) {
                    val importer = DatabaseImporter.create(definition.type)
                    Files.newInputStream(path).use { importer.read(it) }
                }

                if (importState.isEncrypted) {
                    variants = importState.decryptionVariants
                    pendingState = importState
                } else {
                    val result = withContext(Dispatchers.Default) { importState.convert() }
                    presentResult(result)
                }
            } catch (e: Exception) {
                error = e.message ?: Strings["error_occurred"]
            } finally {
                busy = false
            }
        }
    }

    fun decrypt(password: CharArray, variant: Int) {
        val encrypted = pendingState ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val decrypted = if (variants.isEmpty()) {
                        encrypted.decrypt(password)
                    } else {
                        encrypted.decrypt(password, variant)
                    }
                    decrypted.convert()
                }
                pendingState = null
                presentResult(result)
            } catch (e: Exception) {
                error = e.message ?: Strings["error_occurred"]
            } finally {
                Arrays.fill(password, ' ')
                busy = false
            }
        }
    }

    fun commit() {
        busy = true
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val vault = state.vaultManager.vault
                    var added = 0
                    for (entry in found) {
                        if (entry.uuid in selected) {
                            vault.addEntry(entry)
                            added++
                        }
                    }
                    state.vaultManager.saveAndBackup()
                    added
                }
                state.refreshEntries()
                state.showStatus(Strings.format("import_succeeded", count))
                state.back()
            } catch (e: Exception) {
                error = e.message ?: Strings["error_occurred"]
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings["import_label"]) },
                navigationIcon = {
                    IconButton(onClick = { state.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings["back"])
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (found.isEmpty()) {
                Text(Strings["import_from"], style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f)) {
                    items(definitions) { definition ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(definition.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        Strings[definition.help],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            val path = FileChoosers.openFile(state, Strings["choose_file"])
                                            if (path != null) {
                                                readFile(definition, path)
                                            }
                                        }
                                    },
                                ) {
                                    Text(Strings["choose_file"])
                                }
                            }
                        }
                    }
                }
            } else {
                Text(Strings.format("import_found", found.size), style = MaterialTheme.typography.titleMedium)

                if (importErrors.isNotEmpty()) {
                    Text(
                        importErrors.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(Modifier.weight(1f)) {
                    items(found, key = { it.uuid }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = entry.uuid in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + entry.uuid else selected - entry.uuid
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${entry.issuer} ${entry.name}".trim())
                                if (entry.uuid in duplicates) {
                                    Text(
                                        Strings["import_duplicate"],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::commit, enabled = !busy && selected.isNotEmpty()) {
                        Text(Strings["import_selected"])
                    }
                    OutlinedButton(onClick = { state.back() }) { Text(Strings["cancel"]) }
                }
            }
        }
    }

    if (pendingState != null) {
        ImportPasswordDialog(
            variants = variants,
            onConfirm = { password, variant -> decrypt(password, variant) },
            onDismiss = { pendingState = null; variants = emptyList() },
        )
    }
}

@Composable
private fun ImportPasswordDialog(
    variants: List<String>,
    onConfirm: (CharArray, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val password = remember { PasswordState() }
    var variant by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = { password.clear(); onDismiss() },
        title = { Text(Strings["password"]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(
                    state = password,
                    label = Strings["password"],
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = { onConfirm(password.consume(), variant) },
                )

                if (variants.isNotEmpty()) {
                    Text(Strings["which_format"], style = MaterialTheme.typography.bodySmall)
                    variants.forEachIndexed { index, key ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = variant == index, onCheckedChange = { variant = index })
                            Text(Strings[key])
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.consume(), variant) }) { Text(Strings["ok"]) }
        },
        dismissButton = {
            TextButton(onClick = { password.clear(); onDismiss() }) { Text(Strings["cancel"]) }
        },
    )
}
