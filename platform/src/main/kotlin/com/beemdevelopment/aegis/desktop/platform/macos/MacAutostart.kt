package com.beemdevelopment.aegis.desktop.platform.macos

import com.beemdevelopment.aegis.desktop.platform.Autostart
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Autostart via a LaunchAgent in `~/Library/LaunchAgents`, starting the app in the tray and locked. */
class MacAutostart : Autostart {
    private val plistFile: Path by lazy {
        Paths.get(System.getProperty("user.home"), "Library", "LaunchAgents", "$LABEL.plist")
    }

    override val isAvailable: Boolean
        get() = executablePath() != null

    override fun isEnabled(): Boolean = Files.isRegularFile(plistFile)

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            Files.deleteIfExists(plistFile)
            return
        }

        val executable = executablePath() ?: return
        Files.createDirectories(plistFile.parent)
        Files.writeString(plistFile, plist(executable))
    }

    private fun plist(executable: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>$LABEL</string>
            <key>ProgramArguments</key>
            <array>
                <string>${escapeXml(executable)}</string>
                <string>--tray</string>
            </array>
            <key>RunAtLoad</key>
            <true/>
            <key>KeepAlive</key>
            <false/>
        </dict>
        </plist>
    """.trimIndent() + "\n"

    /** jpackage sets `jpackage.app-path` for the installed launcher; a development run has none. */
    private fun executablePath(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { Files.isExecutable(Paths.get(it)) }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private companion object {
        const val LABEL = "com.beemdevelopment.aegis"
    }
}
