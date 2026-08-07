package com.beemdevelopment.aegis.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemdevelopment.aegis.icons.IconType
import com.beemdevelopment.aegis.vault.VaultEntry
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.imageio.ImageIO

@Composable
fun EntryIcon(entry: VaultEntry, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(entry.uuid, entry.icon?.hash?.contentHashCode()) {
        decodeIcon(entry)
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                painter = BitmapPainter(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            val label = entry.avatarLabel()
            Box(
                modifier = Modifier.size(size).background(avatarColor(entry.issuer)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = (size.value * 0.4f).sp,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun decodeIcon(entry: VaultEntry): ImageBitmap? {
    val icon = entry.icon ?: return null
    // SVG icons fall back to the letter avatar rather than running an SVG parser over icon-pack data.
    if (icon.type == IconType.SVG || icon.type == IconType.INVALID) {
        return null
    }

    return try {
        ByteArrayInputStream(icon.bytes).use { stream ->
            ImageIO.read(stream)?.toComposeImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

private fun VaultEntry.avatarLabel(): String {
    val source = issuer.ifBlank { name }.trim()
    if (source.isEmpty()) {
        return "?"
    }
    return source.take(1).uppercase(Locale.ROOT)
}

/** Stable per issuer. Saturation and lightness are fixed so white text stays legible on any hue. */
private fun avatarColor(issuer: String): Color {
    val hash = issuer.lowercase(Locale.ROOT).hashCode()
    val hue = ((hash % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.45f, 0.45f)
}
