package com.beemdevelopment.aegis.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.AccountNamePosition
import com.beemdevelopment.aegis.desktop.ViewMode
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.ui.OtpState
import com.beemdevelopment.aegis.desktop.ui.theme.CodeTextStyle
import com.beemdevelopment.aegis.desktop.ui.theme.CompactCodeTextStyle
import com.beemdevelopment.aegis.desktop.ui.theme.LocalAegisColors
import com.beemdevelopment.aegis.desktop.ui.theme.SmallCodeTextStyle
import com.beemdevelopment.aegis.otp.HotpInfo
import com.beemdevelopment.aegis.vault.VaultEntry

/**
 * The clock is passed in rather than owned per row: one timer for the whole list, and every row
 * agrees on what time it is.
 */
@Composable
fun EntryRow(
    entry: VaultEntry,
    otp: OtpState,
    displayedCode: String,
    revealed: Boolean,
    selected: Boolean,
    showIcon: Boolean,
    viewMode: ViewMode,
    accountNamePosition: AccountNamePosition,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefreshHotp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAegisColors.current
    val verticalPadding = when (viewMode) {
        ViewMode.NORMAL -> 10.dp
        ViewMode.COMPACT -> 7.dp
        ViewMode.SMALL, ViewMode.TILES -> 5.dp
    }
    val codeStyle = when (viewMode) {
        ViewMode.NORMAL -> CodeTextStyle
        ViewMode.COMPACT -> CompactCodeTextStyle
        ViewMode.SMALL, ViewMode.TILES -> SmallCodeTextStyle
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = verticalPadding),
    ) {
        if (showIcon && viewMode != ViewMode.SMALL) {
            EntryIcon(entry, size = if (viewMode == ViewMode.NORMAL) 40.dp else 32.dp)
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = entry.issuer.ifBlank { entry.name },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (accountNamePosition == AccountNamePosition.END && entry.name.isNotBlank() && entry.issuer.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            if (accountNamePosition == AccountNamePosition.BELOW && entry.name.isNotBlank()) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.size(1.dp))

            when {
                otp.error != null -> Text(
                    text = otp.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> Text(
                    text = displayedCode,
                    style = codeStyle.merge(
                        TextStyle(
                            color = if (revealed || displayedCode.isNotBlank()) {
                                codeColor(otp, colors.expiring, MaterialTheme.colorScheme.onSurface)
                            } else {
                                colors.onSurfaceDim
                            },
                        ),
                    ),
                )
            }

            if (otp.nextCode != null && revealed) {
                Text(
                    text = otp.nextCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceDim,
                )
            }
        }

        if (entry.isFavorite || selected) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = Strings["toggle_favorite"],
                    tint = if (entry.isFavorite) colors.favorite else colors.onSurfaceDim,
                )
            }
        }

        // HOTP has no clock: the counter only advances when the user asks, so it gets a button.
        if (entry.info is HotpInfo) {
            IconButton(onClick = onRefreshHotp) {
                Icon(Icons.Default.Refresh, contentDescription = Strings["refresh"])
            }
        } else if (otp.progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val remaining = otp.secondsRemaining
                if (remaining != null && remaining <= EXPIRING_SECONDS) {
                    Text(
                        text = remaining.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.expiring,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                CircularProgressIndicator(
                    progress = { otp.progress },
                    strokeWidth = 2.5.dp,
                    color = codeColor(otp, colors.expiring, MaterialTheme.colorScheme.primary),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun codeColor(otp: OtpState, expiring: Color, normal: Color): Color {
    val remaining = otp.secondsRemaining ?: return normal
    return if (remaining <= EXPIRING_SECONDS) expiring else normal
}

private const val EXPIRING_SECONDS = 5
