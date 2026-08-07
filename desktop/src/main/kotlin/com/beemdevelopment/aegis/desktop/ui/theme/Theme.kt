package com.beemdevelopment.aegis.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.beemdevelopment.aegis.desktop.Theme

// Taken verbatim from the Android app's colors.xml.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2B5BB5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF365CA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF001944),
    tertiary = Color(0xFF006491),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9E6FF),
    onTertiaryContainer = Color(0xFF001E2F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBF8FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFFB0C6FF),
    surfaceTint = Color(0xFF2B5BB5),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6F),
    primaryContainer = Color(0xFF00429C),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF002D6E),
    secondaryContainer = Color(0xFF18438F),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFF8ACEFF),
    onTertiary = Color(0xFF00344E),
    tertiaryContainer = Color(0xFF004C6E),
    onTertiaryContainer = Color(0xFFC9E6FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFC7C6CA),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFFE4E2E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    inversePrimary = Color(0xFF2B5BB5),
    surfaceTint = Color(0xFFB0C6FF),
    scrim = Color(0xFF000000),
)

/** True black, for OLED displays. */
private val AmoledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF0A0A0C),
    surfaceContainerHigh = Color(0xFF141417),
    surfaceContainerHighest = Color(0xFF1B1B1F),
)

/** Colours that Material 3 has no slot for but the app needs. */
data class AegisColors(
    val favorite: Color,
    val success: Color,
    val onSurfaceDim: Color,
    val expiring: Color,
)

private val LightExtras = AegisColors(
    favorite = Color(0xFFF9A825),
    success = Color(0xFF518242),
    onSurfaceDim = Color(0xFF9D9EA2),
    expiring = Color(0xFFBA1A1A),
)

private val DarkExtras = AegisColors(
    favorite = Color(0xFFF9A825),
    success = Color(0xFFD9E7CB),
    onSurfaceDim = Color(0xFF616371),
    expiring = Color(0xFFFFB4AB),
)

val LocalAegisColors = staticCompositionLocalOf { LightExtras }

@Composable
fun AegisTheme(theme: Theme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (theme) {
        Theme.LIGHT -> false
        Theme.DARK, Theme.AMOLED -> true
        Theme.SYSTEM, Theme.SYSTEM_AMOLED -> systemDark
    }
    val amoled = theme == Theme.AMOLED || (theme == Theme.SYSTEM_AMOLED && systemDark)

    val colors = when {
        amoled -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalAegisColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = colors,
            typography = AegisTypography,
            content = content,
        )
    }
}
