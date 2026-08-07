package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.Screen
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.io.FileChoosers
import com.beemdevelopment.aegis.desktop.io.ScreenCapture
import com.beemdevelopment.aegis.otp.GoogleAuthInfo
import com.beemdevelopment.aegis.util.QrCodes
import com.beemdevelopment.aegis.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files

@Composable
fun AddEntryMenu(state: AppState, expanded: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()

    fun openEditorWith(info: GoogleAuthInfo) {
        val entry = VaultEntry(info)
        state.navigate(Screen.EditEntry(null, entry))
    }

    fun handleUri(uri: String) {
        try {
            openEditorWith(GoogleAuthInfo.parseUri(uri.trim()))
        } catch (e: Exception) {
            state.showStatus(e.message ?: Strings["error_occurred"], isError = true)
        }
    }

    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(Strings["scan_qr_from_file"]) },
            onClick = {
                onDismiss()
                scope.launch {
                    val file = FileChoosers.openFile(state, Strings["scan_qr_from_file"]) ?: return@launch
                    try {
                        val result = withContext(Dispatchers.IO) {
                            Files.newInputStream(file).use { QrCodes.decodeFromStream(it) }
                        }
                        handleUri(result.text)
                    } catch (e: Exception) {
                        state.showStatus(Strings["qr_not_found"], isError = true)
                    }
                }
            },
        )

        DropdownMenuItem(
            text = { Text(Strings["scan_qr_from_screen"]) },
            onClick = {
                onDismiss()
                scope.launch {
                    val result = withContext(Dispatchers.IO) { ScreenCapture.findQrCodeOnScreen() }
                    when {
                        result == null -> state.showStatus(Strings["screen_capture_unavailable"], isError = true)
                        result.isEmpty() -> state.showStatus(Strings["qr_not_found"], isError = true)
                        else -> handleUri(result)
                    }
                }
            },
        )

        DropdownMenuItem(
            text = { Text(Strings["paste_uri"]) },
            onClick = {
                onDismiss()
                val text = state.platform.clipboard.readText()
                if (text.isNullOrBlank()) {
                    state.showStatus(Strings["qr_not_found"], isError = true)
                } else {
                    handleUri(text)
                }
            },
        )

        DropdownMenuItem(
            text = { Text(Strings["enter_manually"]) },
            onClick = {
                onDismiss()
                state.navigate(Screen.EditEntry(null))
            },
        )
    }
}
