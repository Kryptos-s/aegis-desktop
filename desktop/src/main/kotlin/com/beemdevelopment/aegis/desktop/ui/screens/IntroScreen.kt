package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.crypto.CryptoUtils
import com.beemdevelopment.aegis.crypto.SCryptParameters
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.io.FileChoosers
import com.beemdevelopment.aegis.desktop.ui.components.AppIcon
import com.beemdevelopment.aegis.desktop.ui.components.CenteredPage
import com.beemdevelopment.aegis.desktop.ui.components.PageHeading
import com.beemdevelopment.aegis.desktop.ui.components.PasswordField
import com.beemdevelopment.aegis.desktop.ui.components.PasswordState
import com.beemdevelopment.aegis.desktop.ui.theme.Spacing
import com.beemdevelopment.aegis.vault.VaultFileCredentials
import com.beemdevelopment.aegis.vault.slots.PasswordSlot
import com.beemdevelopment.aegis.vault.slots.Slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

private enum class IntroStep { WELCOME, SECURITY, PASSWORD, WORKING }

@Composable
fun IntroScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(IntroStep.WELCOME) }
    var encrypt by remember { mutableStateOf(true) }
    val password = remember { PasswordState() }
    val confirm = remember { PasswordState() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Adopts an existing Aegis vault, which keeps its own password and slots.
    fun restoreExistingVault() {
        error = null
        scope.launch {
            val path = FileChoosers.openFile(
                state,
                Strings["import_existing_vault"],
                FileChoosers.Filter.VAULT,
            ) ?: return@launch

            busy = true
            try {
                val needsPassword = withContext(Dispatchers.IO) {
                    state.vaultManager.restoreFrom(path)
                }
                state.prefs.introDone = true
                if (needsPassword) {
                    state.goToUnlock()
                }
            } catch (e: Exception) {
                error = e.message ?: Strings["vault_unreadable"]
            } finally {
                busy = false
            }
        }
    }

    fun createVault() {
        step = IntroStep.WORKING
        busy = true
        error = null

        val chars = if (encrypt) password.consume() else CharArray(0)
        confirm.clear()

        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    state.vaultManager.initNew(if (encrypt) buildCredentials(chars) else null)
                }
                state.prefs.introDone = true
            } catch (e: Exception) {
                error = e.message ?: Strings["error_occurred"]
                step = if (encrypt) IntroStep.PASSWORD else IntroStep.SECURITY
            } finally {
                Arrays.fill(chars, ' ')
                busy = false
            }
        }
    }

    CenteredPage(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        when (step) {
            IntroStep.WELCOME -> {
                AppIcon(size = 72.dp)
                PageHeading(
                    title = Strings["intro_welcome_title"],
                    subtitle = Strings["intro_welcome_message"],
                )

                ErrorText(error)
                BusyBar(busy)

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        enabled = !busy,
                        onClick = { step = IntroStep.SECURITY },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(Strings["get_started"])
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = ::restoreExistingVault,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(Strings["import_existing_vault"])
                    }

                    Text(
                        Strings["import_existing_vault_summary"],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.tight),
                    )
                }
            }

            IntroStep.SECURITY -> {
                PageHeading(
                    title = Strings["intro_security_title"],
                    subtitle = Strings["intro_security_message"],
                )

                ErrorText(error)

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChoiceCard(
                        selected = encrypt,
                        title = Strings["use_a_password"],
                        summary = Strings["use_a_password_summary"],
                        onClick = { encrypt = true },
                    )
                    ChoiceCard(
                        selected = !encrypt,
                        title = Strings["no_encryption"],
                        summary = Strings["no_encryption_summary"],
                        warning = true,
                        onClick = { encrypt = false },
                    )
                }

                StepButtons(
                    onBack = { step = IntroStep.WELCOME },
                    onNext = { if (encrypt) step = IntroStep.PASSWORD else createVault() },
                    nextLabel = if (encrypt) Strings["next"] else Strings["finish"],
                )
            }

            IntroStep.PASSWORD -> {
                PageHeading(
                    title = Strings["intro_password_title"],
                    subtitle = Strings["intro_password_message"],
                )

                ErrorText(error)

                val mismatch = remember(password.revision, confirm.revision) {
                    !confirm.isEmpty && !password.peek().contentEquals(confirm.peek())
                }
                val ready = !busy && !password.isEmpty && !confirm.isEmpty && !mismatch

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PasswordField(
                        state = password,
                        label = Strings["password"],
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PasswordStrengthBar(password.length, password.revision)
                    PasswordField(
                        state = confirm,
                        label = Strings["confirm_password"],
                        enabled = !busy,
                        isError = mismatch,
                        supportingText = if (mismatch) Strings["passwords_do_not_match"] else null,
                        modifier = Modifier.fillMaxWidth(),
                        onSubmit = { if (ready) createVault() },
                    )
                }

                StepButtons(
                    onBack = { step = IntroStep.SECURITY },
                    onNext = ::createVault,
                    nextLabel = Strings["finish"],
                    nextEnabled = ready,
                )
            }

            IntroStep.WORKING -> {
                AppIcon(size = 72.dp)
                PageHeading(
                    title = Strings["intro_done_title"],
                    subtitle = Strings["encrypting_vault"],
                )
                BusyBar(true)
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    AnimatedVisibility(visible = error != null) {
        Text(
            text = error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BusyBar(visible: Boolean) {
    AnimatedVisibility(visible = visible) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepButtons(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    nextEnabled: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text(Strings["back"])
        }
        Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f)) {
            Text(nextLabel)
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    title: String,
    summary: String,
    onClick: () -> Unit,
    warning: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(Spacing.medium),
        ) {
            // null onClick: the card owns the click, so the radio is not separately focusable.
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.size(Spacing.small))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.tight))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Length-only, so it is a rough guide rather than a real strength estimate. */
@Composable
private fun PasswordStrengthBar(length: Int, revision: Int) {
    val fraction = remember(length, revision) { (length / 20f).coerceIn(0f, 1f) }
    val label = remember(length, revision) {
        when {
            length == 0 -> ""
            length < 8 -> Strings["password_strength_weak"]
            length < 12 -> Strings["password_strength_fair"]
            length < 16 -> Strings["password_strength_good"]
            else -> Strings["password_strength_strong"]
        }
    }
    val color = when {
        length < 8 -> MaterialTheme.colorScheme.error
        length < 12 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.tight),
            )
        }
    }
}

/** Runs scrypt, so call this off the UI thread. The password array stays the caller's to wipe. */
private fun buildCredentials(password: CharArray): VaultFileCredentials {
    val creds = VaultFileCredentials()
    val slot = PasswordSlot()
    val params = SCryptParameters(
        CryptoUtils.CRYPTO_SCRYPT_N,
        CryptoUtils.CRYPTO_SCRYPT_r,
        CryptoUtils.CRYPTO_SCRYPT_p,
        CryptoUtils.generateSalt(),
    )
    val key = slot.deriveKey(password, params)
    slot.setKey(creds.key, Slot.createEncryptCipher(key))
    creds.slots.add(slot)
    return creds
}
