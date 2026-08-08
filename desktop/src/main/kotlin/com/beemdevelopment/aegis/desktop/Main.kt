package com.beemdevelopment.aegis.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.platform.Platform
import com.beemdevelopment.aegis.desktop.platform.SingleInstance
import com.beemdevelopment.aegis.desktop.ui.App
import com.beemdevelopment.aegis.desktop.ui.theme.AegisTheme
import com.beemdevelopment.aegis.desktop.ui.theme.Sizes
import java.util.Locale
import kotlin.system.exitProcess

private const val FLAG_TRAY = "--tray"

/** Schemes the desktop entry registers Aegis as a handler for. */
private val URI_SCHEMES = listOf("otpauth:", "otpauth-migration:")

private fun Array<String>.findUri(): String? =
    firstOrNull { arg -> URI_SCHEMES.any { arg.startsWith(it, ignoreCase = true) } }

fun main(args: Array<String>) {
    Hardening.apply()

    val platform = Platform.current
    val paths = platform.paths
    paths.createDirectories()

    // Two processes writing the same vault would race each other's saves, so a second launch
    // raises the existing window instead.
    val launchUri = args.findUri()

    val instance = SingleInstance(paths.lockFile)
    if (!instance.acquire()) {
        // Hand the link to the running instance rather than opening a second vault.
        instance.signalExistingInstance(launchUri)
        exitProcess(0)
    }
    Runtime.getRuntime().addShutdownHook(Thread { instance.release() })

    val prefs = Preferences(paths.preferencesFile)
    prefs.language?.let { Strings.setLocale(Locale.forLanguageTag(it)) }

    val state = AppState(paths, prefs, platform)
    launchUri?.let { state.offerUri(it) }
    val startMinimized = args.contains(FLAG_TRAY)

    application {
        val windowState = rememberWindowState(
            size = DpSize(1000.dp, 720.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
        )

        val icon = remember { loadAppIcon() }

        Window(
            onCloseRequest = {
                state.shutdown()
                instance.release()
                exitApplication()
            },
            state = windowState,
            title = Strings["app_name_desktop"],
            icon = icon,
            visible = !startMinimized,
        ) {
            LaunchedEffect(window) {
                window.minimumSize = java.awt.Dimension(
                    Sizes.minWindowWidth.value.toInt(),
                    Sizes.minWindowHeight.value.toInt(),
                )
            }

            LaunchedEffect(instance) {
                instance.onActivationRequested { uri ->
                    windowState.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                    uri?.let { state.offerUri(it) }
                }
            }

            LaunchedEffect(windowState.isMinimized) {
                if (windowState.isMinimized) {
                    state.vaultManager.onMinimized()
                }
            }

            AegisTheme(prefs.theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(state)
                }
            }
        }
    }
}

private fun loadAppIcon(): androidx.compose.ui.graphics.painter.Painter? =
    Main::class.java.getResourceAsStream("/icons/aegis.png")?.use { stream ->
        androidx.compose.ui.graphics.painter.BitmapPainter(
            androidx.compose.ui.res.loadImageBitmap(stream),
        )
    }

private object Main
