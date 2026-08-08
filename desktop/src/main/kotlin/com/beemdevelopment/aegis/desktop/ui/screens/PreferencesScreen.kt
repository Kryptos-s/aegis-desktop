package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.AccountNamePosition
import com.beemdevelopment.aegis.BackupsVersioningStrategy
import com.beemdevelopment.aegis.CopyBehavior
import com.beemdevelopment.aegis.EventType
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.Screen
import com.beemdevelopment.aegis.desktop.Theme
import com.beemdevelopment.aegis.desktop.ViewMode
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.io.FileChoosers
import com.beemdevelopment.aegis.desktop.ui.components.ConfirmDialog
import com.beemdevelopment.aegis.desktop.ui.components.DetailPage
import com.beemdevelopment.aegis.desktop.ui.components.PasswordField
import com.beemdevelopment.aegis.desktop.ui.components.PasswordState
import com.beemdevelopment.aegis.desktop.ui.theme.Spacing
import com.beemdevelopment.aegis.util.TempFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PreferencesScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val prefs = state.prefs
    val platform = state.platform

    var revision by remember { mutableStateOf(0) }
    fun changed() {
        revision++
    }

    var changingPassword by remember { mutableStateOf(false) }
    var confirmDisableEncryption by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmPlainExport by remember { mutableStateOf<ExportKind?>(null) }
    var showAuditLog by remember { mutableStateOf(false) }

    DetailPage(title = Strings["preferences"], onBack = { state.back() }) {
        SectionHeader(Strings["section_security"])

        SettingRow(
            title = Strings["change_password"],
            onClick = { changingPassword = true },
        )

        if (state.vaultManager.isUnlocked && state.vaultManager.vault.isEncryptionEnabled) {
            SettingRow(
                title = Strings["export_encrypted"],
                summary = Strings["no_encryption_summary"],
                trailing = {
                    Switch(
                        checked = true,
                        onCheckedChange = { confirmDisableEncryption = true },
                    )
                },
            )
        }

        SwitchRow(
            title = Strings["keychain_unlock"],
            summary = if (platform.secretStore.isAvailable) {
                Strings.format("keychain_unlock_summary", platform.secretStore.name)
            } else {
                Strings["keychain_unavailable"]
            },
            checked = prefs.keychainUnlockEnabled,
            enabled = platform.secretStore.isAvailable &&
                state.vaultManager.isUnlocked &&
                state.vaultManager.vault.isEncryptionEnabled,
            onChange = { enabled ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (enabled) {
                                state.vaultManager.enableKeychainUnlock()
                            } else {
                                state.vaultManager.disableKeychainUnlock()
                            }
                        }
                    } catch (e: Exception) {
                        state.showStatus(e.message ?: Strings["error_occurred"], isError = true)
                    }
                    changed()
                }
            },
        )

        SwitchRow(
            title = Strings["require_user_presence"],
            summary = if (platform.userPresence.isAvailable) {
                Strings["require_user_presence_summary"]
            } else {
                Strings["user_presence_unavailable"]
            },
            checked = prefs.keychainRequiresPresence,
            enabled = platform.userPresence.isAvailable,
            onChange = { prefs.keychainRequiresPresence = it; changed() },
        )

        SectionHeader(Strings["section_locking"])

        ChoiceRow(
            title = Strings["idle_lock_timeout"],
            current = durationLabel(prefs.idleLockTimeout),
            options = IDLE_TIMEOUTS.map { durationLabel(it) },
            onSelect = { index -> prefs.idleLockTimeout = IDLE_TIMEOUTS[index]; changed() },
        )

        SwitchRow(
            title = Strings["lock_on_session_lock"],
            summary = if (platform.sessionMonitor.isAvailable) null else Strings["session_monitor_unavailable"],
            checked = prefs.lockOnSessionLock,
            enabled = platform.sessionMonitor.isAvailable,
            onChange = { prefs.lockOnSessionLock = it; changed() },
        )

        SwitchRow(
            title = Strings["lock_on_suspend"],
            summary = if (platform.sessionMonitor.isAvailable) null else Strings["session_monitor_unavailable"],
            checked = prefs.lockOnSuspend,
            enabled = platform.sessionMonitor.isAvailable,
            onChange = { prefs.lockOnSuspend = it; changed() },
        )

        SwitchRow(
            title = Strings["lock_on_minimize"],
            checked = prefs.lockOnMinimize,
            onChange = { prefs.lockOnMinimize = it; changed() },
        )

        SwitchRow(
            title = Strings["start_at_login"],
            summary = if (platform.autostart.isAvailable) {
                Strings["start_at_login_summary"]
            } else {
                Strings["autostart_unavailable"]
            },
            checked = platform.autostart.isAvailable && platform.autostart.isEnabled(),
            enabled = platform.autostart.isAvailable,
            onChange = {
                runCatching { platform.autostart.setEnabled(it) }
                    .onFailure { e ->
                        state.showStatus(e.message ?: Strings["error_occurred"], isError = true)
                    }
                changed()
            },
        )

        SectionHeader(Strings["section_clipboard"])

        ChoiceRow(
            title = Strings["pref_copy_behavior_title"],
            current = copyBehaviorLabel(prefs.copyBehavior),
            options = CopyBehavior.entries.map { copyBehaviorLabel(it) },
            onSelect = { index -> prefs.copyBehavior = CopyBehavior.entries[index]; changed() },
        )

        ChoiceRow(
            title = Strings["clipboard_clear_delay"],
            current = durationLabel(prefs.clipboardClearDelay),
            options = CLIPBOARD_DELAYS.map { durationLabel(it) },
            onSelect = { index -> prefs.clipboardClearDelay = CLIPBOARD_DELAYS[index]; changed() },
        )

        Text(
            if (platform.clipboard.copySensitiveIsPrivate) {
                Strings["clipboard_private_yes"]
            } else {
                Strings["clipboard_private_no"]
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.small),
        )

        SectionHeader(Strings["section_appearance"])

        ChoiceRow(
            title = Strings["theme"],
            current = themeLabel(prefs.theme),
            options = Theme.entries.map { themeLabel(it) },
            onSelect = { index -> prefs.theme = Theme.entries[index]; changed() },
        )

        ChoiceRow(
            title = Strings["view_mode"],
            current = viewModeLabelFor(prefs.viewMode),
            options = ViewMode.entries.map { viewModeLabelFor(it) },
            onSelect = { index -> prefs.viewMode = ViewMode.entries[index]; changed() },
        )

        ChoiceRow(
            title = Strings["pref_account_name_position_title"],
            current = prefs.accountNamePosition.name,
            options = AccountNamePosition.entries.map { it.name },
            onSelect = { index ->
                prefs.accountNamePosition = AccountNamePosition.entries[index]
                changed()
            },
        )

        SwitchRow(
            title = Strings["pref_show_icons_title"],
            checked = prefs.showIcons,
            onChange = { prefs.showIcons = it; changed() },
        )

        SwitchRow(
            title = Strings["pref_show_next_code_title"],
            checked = prefs.showNextCode,
            onChange = { prefs.showNextCode = it; changed() },
        )

        SwitchRow(
            title = Strings["pref_tap_to_reveal_title"],
            checked = prefs.tapToReveal,
            onChange = { prefs.tapToReveal = it; changed() },
        )

        SectionHeader(Strings["section_backups"])

        SwitchRow(
            title = Strings["pref_backups_title"],
            checked = prefs.backupsEnabled,
            onChange = { prefs.backupsEnabled = it; changed() },
        )

        SettingRow(
            title = Strings["backup_location"],
            summary = prefs.backupsLocation?.toString(),
            onClick = {
                scope.launch {
                    FileChoosers.chooseDirectory(state, Strings["choose_folder"])?.let {
                        prefs.backupsLocation = it
                        changed()
                    }
                }
            },
        )

        ChoiceRow(
            title = Strings["pref_backups_versions_title"],
            current = prefs.backupVersioningStrategy.name,
            options = BackupsVersioningStrategy.entries.map { it.name },
            onSelect = { index ->
                prefs.backupVersioningStrategy = BackupsVersioningStrategy.entries[index]
                changed()
            },
        )

        state.vaultManager.lastBackupError?.let { error ->
            Text(
                Strings.format("last_backup_failed", error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
        }

        SettingRow(
            title = Strings["backup_now"],
            onClick = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { state.vaultManager.saveAndBackup() }
                        state.showStatus(Strings["backup_succeeded"])
                    } catch (e: Exception) {
                        state.showStatus(
                            Strings.format("backup_failed", e.message ?: ""),
                            isError = true,
                        )
                    }
                }
            },
        )

        SectionHeader(Strings["section_import_export"])

        SettingRow(title = Strings["import_label"], onClick = { state.navigate(Screen.Import) })

        SettingRow(
            title = Strings["export_vault_encrypted"],
            onClick = { export(state, scope, ExportKind.ENCRYPTED) },
        )
        SettingRow(
            title = Strings["export_plain"],
            onClick = { confirmPlainExport = ExportKind.PLAIN },
        )
        SettingRow(
            title = Strings["export_uris"],
            onClick = { confirmPlainExport = ExportKind.URIS },
        )
        SettingRow(
            title = Strings["export_html"],
            onClick = { confirmPlainExport = ExportKind.HTML },
        )

        SectionHeader(Strings["section_vault"])

        SettingRow(title = Strings["audit_log"], onClick = { showAuditLog = true })
        SettingRow(
            title = Strings["wipe_vault"],
            destructive = true,
            onClick = { confirmWipe = true },
        )

        Spacer(Modifier.height(Spacing.section))
    }

    if (changingPassword) {
        ChangePasswordDialog(
            onConfirm = { password ->
                changingPassword = false
                scope.launch {
                    try {
                        withContext(Dispatchers.Default) { state.vaultManager.setPassword(password) }
                        state.showStatus(Strings["password_changed"])
                    } catch (e: Exception) {
                        state.showStatus(e.message ?: Strings["error_occurred"], isError = true)
                    }
                }
            },
            onDismiss = { changingPassword = false },
        )
    }

    if (confirmDisableEncryption) {
        ConfirmDialog(
            title = Strings["export_encrypted"],
            message = Strings["no_encryption_summary"],
            confirmLabel = Strings["ok"],
            destructive = true,
            onConfirm = {
                confirmDisableEncryption = false
                scope.launch {
                    withContext(Dispatchers.IO) { state.vaultManager.disableEncryption() }
                    changed()
                }
            },
            onDismiss = { confirmDisableEncryption = false },
        )
    }

    confirmPlainExport?.let { kind ->
        ConfirmDialog(
            title = Strings["export_label"],
            message = Strings["export_plain_warning"],
            confirmLabel = Strings["ok"],
            destructive = true,
            onConfirm = {
                confirmPlainExport = null
                export(state, scope, kind)
            },
            onDismiss = { confirmPlainExport = null },
        )
    }

    if (confirmWipe) {
        TypedConfirmDialog(
            message = Strings["wipe_vault_warning"],
            requiredWord = "DELETE",
            onConfirm = {
                confirmWipe = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        state.vaultManager.vault.wipeContents()
                        state.vaultManager.save()
                    }
                    state.refreshEntries()
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }

    if (showAuditLog) {
        AuditLogDialog(state) { showAuditLog = false }
    }
}

private enum class ExportKind { ENCRYPTED, PLAIN, URIS, HTML }

/** Writes an export to a file the user picks. Every export is restricted to the owner. */
private fun export(state: AppState, scope: kotlinx.coroutines.CoroutineScope, kind: ExportKind) {
    scope.launch {
        val suggested = when (kind) {
            ExportKind.ENCRYPTED -> "aegis-export.json"
            ExportKind.PLAIN -> "aegis-export-plain.json"
            ExportKind.URIS -> "aegis-export-uri.txt"
            ExportKind.HTML -> "aegis-export.html"
        }

        val path = FileChoosers.saveFile(state, Strings["export_label"], suggested) ?: return@launch

        try {
            withContext(Dispatchers.IO) {
                val vault = state.vaultManager.vault
                Files.newOutputStream(path).use { out ->
                    when (kind) {
                        ExportKind.ENCRYPTED -> vault.export(out)
                        ExportKind.PLAIN -> vault.export(out, null)
                        ExportKind.URIS -> vault.exportGoogleUris(out, null)
                        ExportKind.HTML -> vault.exportHtml(out, null, Strings["export_html_title"])
                    }
                }
                TempFiles.restrictToOwner(path)
            }
            state.auditLog.record(EventType.VAULT_EXPORTED)
            state.showStatus(Strings.format("export_succeeded", path.toString()))
        } catch (e: Exception) {
            state.showStatus(e.message ?: Strings["error_occurred"], isError = true)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(Spacing.medium))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = Spacing.small))
}

@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    destructive: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        summary = summary,
        trailing = {
            Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
        },
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    current: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SettingRow(
        title = title,
        summary = current,
        trailing = {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(current) }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    options.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { expanded = false; onSelect(index) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ChangePasswordDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    val password = remember { PasswordState() }
    val confirm = remember { PasswordState() }

    val mismatch = remember(password.revision, confirm.revision) {
        !confirm.isEmpty && !password.peek().contentEquals(confirm.peek())
    }

    AlertDialog(
        onDismissRequest = { password.clear(); confirm.clear(); onDismiss() },
        title = { Text(Strings["change_password"]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(password, Strings["password"], Modifier.fillMaxWidth())
                PasswordField(confirm, Strings["confirm_password"], Modifier.fillMaxWidth())
                if (mismatch) {
                    Text(Strings["passwords_do_not_match"], color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !password.isEmpty && !mismatch && !confirm.isEmpty,
                onClick = {
                    val chars = password.consume()
                    confirm.clear()
                    onConfirm(chars)
                },
            ) {
                Text(Strings["save"])
            }
        },
        dismissButton = {
            TextButton(onClick = { password.clear(); confirm.clear(); onDismiss() }) {
                Text(Strings["cancel"])
            }
        },
    )
}

@Composable
private fun TypedConfirmDialog(
    message: String,
    requiredWord: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings["wipe_vault"]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = typed == requiredWord, onClick = onConfirm) {
                Text(Strings["delete"])
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings["cancel"]) } },
    )
}

@Composable
private fun AuditLogDialog(state: AppState, onDismiss: () -> Unit) {
    val events = remember { state.auditLog.all() }
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings["audit_log"]) },
        text = {
            if (events.isEmpty()) {
                Text(Strings["audit_log_empty"])
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.tight),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    events.forEach { event ->
                        Text(
                            "${formatter.format(event.at)}  ${Strings[EventType.getEventTitleRes(event.type)]}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings["close"]) } },
    )
}

private val IDLE_TIMEOUTS = listOf(
    Duration.ZERO,
    Duration.ofMinutes(1),
    Duration.ofMinutes(5),
    Duration.ofMinutes(15),
    Duration.ofMinutes(30),
    Duration.ofHours(1),
)

private val CLIPBOARD_DELAYS = listOf(
    Duration.ZERO,
    Duration.ofSeconds(15),
    Duration.ofSeconds(30),
    Duration.ofSeconds(60),
)

private fun durationLabel(duration: Duration): String = when {
    duration.isZero -> Strings["never"]
    duration.toMinutes() < 1 -> Strings.format("seconds_value", duration.seconds.toInt())
    else -> Strings.format("minutes_value", duration.toMinutes().toInt())
}

private fun themeLabel(theme: Theme): String = when (theme) {
    Theme.LIGHT -> Strings["light_theme_title"]
    Theme.DARK -> Strings["dark_theme_title"]
    Theme.AMOLED -> Strings["amoled_theme_title"]
    Theme.SYSTEM -> Strings["system_theme_title"]
    Theme.SYSTEM_AMOLED -> Strings["system_amoled_theme_title"]
}

private fun viewModeLabelFor(mode: ViewMode): String = when (mode) {
    ViewMode.NORMAL -> Strings["normal_viewmode_title"]
    ViewMode.COMPACT -> Strings["compact_mode_title"]
    ViewMode.SMALL -> Strings["small_mode_title"]
    ViewMode.TILES -> Strings["tiles_mode_title"]
}

private fun copyBehaviorLabel(behavior: CopyBehavior): String = when (behavior) {
    CopyBehavior.NEVER -> Strings["never"]
    CopyBehavior.SINGLETAP -> Strings["pref_copy_behavior_single_tap"]
    CopyBehavior.DOUBLETAP -> Strings["pref_copy_behavior_double_tap"]
}
