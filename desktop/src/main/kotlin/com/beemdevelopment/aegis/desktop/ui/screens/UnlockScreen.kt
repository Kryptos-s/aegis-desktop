package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.platform.UserPresenceRequiredException
import com.beemdevelopment.aegis.desktop.ui.components.AppIcon
import com.beemdevelopment.aegis.desktop.ui.components.CenteredPage
import com.beemdevelopment.aegis.desktop.ui.components.PageHeading
import com.beemdevelopment.aegis.desktop.ui.components.PasswordField
import com.beemdevelopment.aegis.desktop.ui.components.PasswordState
import com.beemdevelopment.aegis.desktop.ui.theme.Spacing
import com.beemdevelopment.aegis.vault.VaultFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

@Composable
fun UnlockScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val password = remember { PasswordState() }
    val focus = remember { FocusRequester() }

    var vaultFile by remember { mutableStateOf<VaultFile?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockedOutFor by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val file = withContext(Dispatchers.IO) { state.vaultManager.readVaultFile() }
            vaultFile = file
            if (!file.isEncrypted) {
                withContext(Dispatchers.IO) { state.vaultManager.unlockPlaintext(file) }
            }
        } catch (e: Exception) {
            readError = e.message ?: Strings["vault_unreadable"]
        }
    }

    LaunchedEffect(vaultFile) {
        if (vaultFile?.isEncrypted == true) {
            focus.requestFocus()
        }
    }

    // The backoff only slows someone at this keyboard; an attacker with the vault file guesses
    // offline, where scrypt is the only thing making it expensive.
    LaunchedEffect(lockedOutFor) {
        while (lockedOutFor > 0) {
            delay(1000)
            lockedOutFor -= 1
        }
    }

    fun unlock() {
        val file = vaultFile ?: return
        if (busy || lockedOutFor > 0 || password.isEmpty) {
            return
        }

        busy = true
        unlockError = null
        val chars = password.consume()

        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    state.vaultManager.unlockWithPassword(file, chars)
                }
                failedAttempts = 0
            } catch (e: Exception) {
                failedAttempts += 1
                if (failedAttempts >= ATTEMPTS_BEFORE_DELAY) {
                    lockedOutFor = minOf(MAX_DELAY_SECONDS, 1 shl (failedAttempts - ATTEMPTS_BEFORE_DELAY))
                }
                unlockError = e.message ?: Strings["invalid_password"]
                focus.requestFocus()
            } finally {
                Arrays.fill(chars, ' ')
                busy = false
            }
        }
    }

    fun unlockWithKeychain() {
        val file = vaultFile ?: return
        if (busy) {
            return
        }

        busy = true
        unlockError = null
        scope.launch {
            try {
                // The presence prompt is a modal OS window, so the idle timer sees no interaction
                // and would otherwise lock the vault out from under it.
                state.vaultManager.setAutoLockBlocked(true)
                withContext(Dispatchers.IO) { state.vaultManager.unlockWithKeychain(file) }
            } catch (e: UserPresenceRequiredException) {
                unlockError = Strings["authentication_cancelled"]
            } catch (e: Exception) {
                unlockError = e.message
            } finally {
                state.vaultManager.setAutoLockBlocked(false)
                busy = false
            }
        }
    }

    CenteredPage(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        AppIcon(size = 64.dp)

        val error = readError
        when {
            error != null -> {
                PageHeading(title = Strings["vault_unreadable"], subtitle = error)
                OutlinedButton(
                    onClick = { state.showStatus(Strings["restore_backup"]) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(Strings["restore_backup"])
                }
            }

            vaultFile == null -> CircularProgressIndicator()

            else -> {
                PageHeading(title = Strings["vault_locked"])

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PasswordField(
                        state = password,
                        label = Strings["password"],
                        enabled = !busy && lockedOutFor == 0,
                        isError = unlockError != null,
                        supportingText = when {
                            lockedOutFor > 0 -> Strings.format("too_many_attempts", lockedOutFor)
                            else -> unlockError
                        },
                        focusRequester = focus,
                        onSubmit = ::unlock,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            Strings["unlocking_vault"],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(
                            onClick = ::unlock,
                            enabled = !password.isEmpty && lockedOutFor == 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(Strings["unlock"])
                        }
                    }

                    val file = vaultFile
                    if (file != null && !busy && state.vaultManager.hasKeychainSlot(file)) {
                        OutlinedButton(
                            onClick = ::unlockWithKeychain,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(Strings.format("unlock_with", state.platform.secretStore.name))
                        }
                    }
                }
            }
        }
    }
}

private const val ATTEMPTS_BEFORE_DELAY = 3
private const val MAX_DELAY_SECONDS = 60
