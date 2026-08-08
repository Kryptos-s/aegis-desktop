package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.beemdevelopment.aegis.desktop.ui.theme.CodeTextStyle
import com.beemdevelopment.aegis.encoding.Base32
import com.beemdevelopment.aegis.encoding.Hex
import com.beemdevelopment.aegis.otp.HotpInfo
import com.beemdevelopment.aegis.otp.MotpInfo
import com.beemdevelopment.aegis.otp.OtpInfo
import com.beemdevelopment.aegis.otp.SteamInfo
import com.beemdevelopment.aegis.otp.TotpInfo
import com.beemdevelopment.aegis.otp.YandexInfo
import com.beemdevelopment.aegis.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class OtpType(val label: String) {
    TOTP("TOTP"),
    HOTP("HOTP"),
    STEAM("Steam"),
    YANDEX("Yandex"),
    MOTP("MOTP"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(state: AppState, entryUuid: UUID?, prefill: VaultEntry?) {
    val scope = rememberCoroutineScope()
    val existing = remember(entryUuid) {
        entryUuid?.let { uuid ->
            if (state.vaultManager.isUnlocked && state.vaultManager.vault.hasEntryByUUID(uuid)) {
                state.vaultManager.vault.getEntryByUUID(uuid)
            } else {
                null
            }
        }
    }
    val source = existing ?: prefill

    var issuer by remember { mutableStateOf(source?.issuer ?: "") }
    var name by remember { mutableStateOf(source?.name ?: "") }
    var note by remember { mutableStateOf(source?.note ?: "") }
    var favorite by remember { mutableStateOf(source?.isFavorite ?: false) }
    var groups by remember { mutableStateOf(source?.groups?.toSet() ?: emptySet()) }

    val sourceInfo = source?.info
    var type by remember { mutableStateOf(typeOf(sourceInfo)) }
    var secret by remember {
        mutableStateOf(
            sourceInfo?.let {
                if (it is MotpInfo) Hex.encode(it.secret) else Base32.encode(it.secret)
            } ?: "",
        )
    }
    var algorithm by remember { mutableStateOf(sourceInfo?.getAlgorithm(false) ?: OtpInfo.DEFAULT_ALGORITHM) }
    var digits by remember { mutableStateOf((sourceInfo?.digits ?: OtpInfo.DEFAULT_DIGITS).toString()) }
    var period by remember {
        mutableStateOf(((sourceInfo as? TotpInfo)?.period ?: TotpInfo.DEFAULT_PERIOD).toString())
    }
    var counter by remember { mutableStateOf(((sourceInfo as? HotpInfo)?.counter ?: 0L).toString()) }
    var pin by remember {
        mutableStateOf(
            when (sourceInfo) {
                is MotpInfo -> sourceInfo.pin
                is YandexInfo -> sourceInfo.pin
                else -> ""
            } ?: "",
        )
    }

    var showAdvanced by remember { mutableStateOf(existing == null) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showAlgoMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Steam, MOTP and Yandex fix their digits and period; carrying the old values across a type
    // change would build an entry the constructor rejects.
    LaunchedEffect(type) {
        when (type) {
            OtpType.STEAM -> {
                digits = SteamInfo.DIGITS.toString()
                algorithm = "SHA1"
            }

            OtpType.MOTP -> {
                digits = MotpInfo.DIGITS.toString()
                period = MotpInfo.PERIOD.toString()
                algorithm = MotpInfo.ALGORITHM
            }

            OtpType.YANDEX -> {
                digits = YandexInfo.DIGITS.toString()
                algorithm = YandexInfo.DEFAULT_ALGORITHM
            }

            else -> Unit
        }
    }

    fun buildInfo(): Result<OtpInfo> = runCatching {
        val secretBytes = if (type == OtpType.MOTP) {
            Hex.decode(secret.trim())
        } else {
            com.beemdevelopment.aegis.otp.GoogleAuthInfo.parseSecret(secret)
        }

        val digitCount = digits.trim().toIntOrNull()
            ?: throw IllegalArgumentException(Strings["invalid_digits"])
        val periodValue = period.trim().toIntOrNull()
            ?: throw IllegalArgumentException(Strings["invalid_period"])

        when (type) {
            OtpType.TOTP -> TotpInfo(secretBytes, algorithm, digitCount, periodValue)
            OtpType.STEAM -> SteamInfo(secretBytes, algorithm, digitCount, periodValue)
            OtpType.YANDEX -> YandexInfo(secretBytes, pin).also { it.setPeriod(periodValue) }
            OtpType.MOTP -> MotpInfo(secretBytes, pin)
            OtpType.HOTP -> HotpInfo(
                secretBytes,
                algorithm,
                digitCount,
                counter.trim().toLongOrNull() ?: 0L,
            )
        }
    }

    val preview = remember(secret, type, algorithm, digits, period, counter, pin) {
        buildInfo().mapCatching { it.otp }.getOrNull()
    }

    fun save() {
        val info = buildInfo().getOrElse {
            error = it.message ?: Strings["invalid_secret"]
            return
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val vault = state.vaultManager.vault
                    if (existing != null) {
                        vault.editEntry(existing) { entry ->
                            entry.issuer = issuer.trim()
                            entry.name = name.trim()
                            entry.note = note
                            entry.setIsFavorite(favorite)
                            entry.groups = groups.toMutableSet()
                            entry.info = info
                        }
                    } else {
                        val entry = VaultEntry(info, name.trim(), issuer.trim())
                        entry.note = note
                        entry.setIsFavorite(favorite)
                        entry.groups = groups.toMutableSet()
                        source?.icon?.let { entry.icon = it }
                        vault.addEntry(entry)
                    }
                    state.vaultManager.saveAndBackup()
                }
                state.refreshEntries()
                state.showStatus(Strings["entry_saved"])
                state.back()
            } catch (e: Exception) {
                error = e.message ?: Strings["error_occurred"]
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) Strings["new_entry"] else Strings["edit"]) },
                navigationIcon = {
                    IconButton(onClick = { state.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings["cancel"])
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = Strings["delete"],
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(onClick = ::save) { Text(Strings["save"]) }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(Strings["issuer"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(Strings["account_name"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(Strings["note"]) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = favorite, onCheckedChange = { favorite = it })
                Text(Strings["toggle_favorite"])
            }

            if (state.vaultManager.isUnlocked) {
                val allGroups = state.vaultManager.vault.groups.toList()
                if (allGroups.isNotEmpty()) {
                    Text(Strings["groups"], style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allGroups.forEach { group ->
                            FilterChip(
                                selected = group.uuid in groups,
                                onClick = {
                                    groups = if (group.uuid in groups) {
                                        groups - group.uuid
                                    } else {
                                        groups + group.uuid
                                    }
                                },
                                label = { Text(group.name) },
                            )
                        }
                    }
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(Strings["advanced"])
            }

            if (showAdvanced) {
                Box {
                    OutlinedButton(onClick = { showTypeMenu = true }) {
                        Text("${Strings["type"]}: ${type.label}")
                    }
                    DropdownMenu(showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        OtpType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.label) },
                                onClick = { type = candidate; showTypeMenu = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; error = null },
                    label = { Text(Strings["secret"]) },
                    singleLine = true,
                    supportingText = {
                        Text(if (type == OtpType.MOTP) "hex" else "base32")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (type != OtpType.MOTP) {
                    Box {
                        OutlinedButton(
                            onClick = { showAlgoMenu = true },
                            enabled = type == OtpType.TOTP || type == OtpType.HOTP,
                        ) {
                            Text("${Strings["algorithm"]}: $algorithm")
                        }
                        DropdownMenu(showAlgoMenu, onDismissRequest = { showAlgoMenu = false }) {
                            listOf("SHA1", "SHA256", "SHA512").forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate) },
                                    onClick = { algorithm = candidate; showAlgoMenu = false },
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = digits,
                        onValueChange = { digits = it; error = null },
                        label = { Text(Strings["digits"]) },
                        singleLine = true,
                        enabled = type == OtpType.TOTP || type == OtpType.HOTP,
                        modifier = Modifier.width(120.dp),
                    )

                    if (type == OtpType.HOTP) {
                        OutlinedTextField(
                            value = counter,
                            onValueChange = { counter = it; error = null },
                            label = { Text(Strings["counter"]) },
                            singleLine = true,
                            modifier = Modifier.width(160.dp),
                        )
                    } else {
                        OutlinedTextField(
                            value = period,
                            onValueChange = { period = it; error = null },
                            label = { Text(Strings["period"]) },
                            singleLine = true,
                            enabled = type != OtpType.MOTP,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }

                if (type == OtpType.MOTP || type == OtpType.YANDEX) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; error = null },
                        label = { Text(Strings["pin"]) },
                        singleLine = true,
                        modifier = Modifier.width(200.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(Strings["code_preview"], style = MaterialTheme.typography.titleSmall)
            Text(
                text = preview ?: "——————",
                style = CodeTextStyle,
                color = if (preview == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = Strings["delete"],
            message = Strings.format("confirm_delete_entry", existing.issuer, existing.name),
            confirmLabel = Strings["delete"],
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        state.vaultManager.vault.removeEntry(existing)
                        state.vaultManager.saveAndBackup()
                    }
                    state.refreshEntries()
                    state.showStatus(Strings["entry_deleted"])
                    state.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun typeOf(info: OtpInfo?): OtpType = when (info) {
    is SteamInfo -> OtpType.STEAM
    is YandexInfo -> OtpType.YANDEX
    is MotpInfo -> OtpType.MOTP
    is HotpInfo -> OtpType.HOTP
    else -> OtpType.TOTP
}
