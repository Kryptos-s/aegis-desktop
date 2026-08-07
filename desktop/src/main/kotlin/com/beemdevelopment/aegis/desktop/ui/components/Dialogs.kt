package com.beemdevelopment.aegis.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.otp.GoogleAuthInfo
import com.beemdevelopment.aegis.util.QrCodes
import com.beemdevelopment.aegis.vault.VaultEntry

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings["cancel"]) }
        },
    )
}

@Composable
fun QrCodeDialog(entry: VaultEntry, onDismiss: () -> Unit) {
    val image = remember(entry.uuid) {
        runCatching {
            val uri = GoogleAuthInfo(entry.info, entry.name, entry.issuer).uri.toString()
            QrCodes.encodeToImage(uri, 320, 320, QrCodes.WHITE).toComposeImageBitmap()
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.issuer} ${entry.name}".trim()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Strings["qr_code_warning"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                if (image != null) {
                    Image(
                        painter = BitmapPainter(image),
                        contentDescription = null,
                        modifier = Modifier.size(320.dp),
                    )
                } else {
                    Text(Strings["error_occurred"])
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Strings["close"]) }
        },
    )
}
