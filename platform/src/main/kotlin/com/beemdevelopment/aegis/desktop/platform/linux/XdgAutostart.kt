package com.beemdevelopment.aegis.desktop.platform.linux

import com.beemdevelopment.aegis.desktop.platform.Autostart
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Autostart via an XDG desktop entry in `~/.config/autostart`, starting the app in the tray and locked. */
class XdgAutostart : Autostart {
    private val autostartFile: Path by lazy {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() && Paths.get(it).isAbsolute }
            ?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".config")
        configHome.resolve("autostart").resolve("$DESKTOP_ID.desktop")
    }

    override val isAvailable: Boolean
        get() = executablePath() != null

    override fun isEnabled(): Boolean = Files.isRegularFile(autostartFile)

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            Files.deleteIfExists(autostartFile)
            return
        }

        val executable = executablePath() ?: return
        Files.createDirectories(autostartFile.parent)
        Files.writeString(
            autostartFile,
            """
            [Desktop Entry]
            Type=Application
            Name=Aegis Authenticator
            Comment=Two-factor authentication codes
            Exec=$executable --tray
            Icon=$DESKTOP_ID
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n",
        )
    }

    /** jpackage sets `jpackage.app-path` for the installed launcher; a development run has none. */
    private fun executablePath(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { Files.isExecutable(Paths.get(it)) }

    private companion object {
        const val DESKTOP_ID = "com.beemdevelopment.aegis"
    }
}
