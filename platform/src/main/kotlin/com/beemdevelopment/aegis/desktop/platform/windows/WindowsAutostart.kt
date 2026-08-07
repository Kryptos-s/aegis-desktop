package com.beemdevelopment.aegis.desktop.platform.windows

import com.beemdevelopment.aegis.desktop.platform.Autostart
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * Autostart via the `Run` key under `HKEY_CURRENT_USER`, starting the app in the tray and locked.
 * The machine-wide key would need administrator rights and would apply to every account.
 */
class WindowsAutostart : Autostart {

    override val isAvailable: Boolean
        get() = launcherPath() != null

    /** An unreadable `Run` key reads as "not enabled": this renders a checkbox. */
    override fun isEnabled(): Boolean =
        try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
        } catch (e: Win32Exception) {
            false
        }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            // registryDeleteValue fails if the value is not there.
            if (isEnabled()) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
            }
            return
        }

        val launcher = launcherPath() ?: return
        Advapi32Util.registrySetStringValue(
            WinReg.HKEY_CURRENT_USER,
            RUN_KEY,
            VALUE_NAME,
            "\"$launcher\" $TRAY_FLAG",
        )
    }

    /** jpackage sets `jpackage.app-path` for the installed launcher; a development run has none. */
    private fun launcherPath(): String? {
        val path = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return null
        // The value is quoted into a command line, and a Windows path cannot contain a quote, so one
        // here would let the rest of the string become extra arguments.
        if (path.contains('"')) {
            return null
        }

        return try {
            if (Files.isRegularFile(Paths.get(path))) path else null
        } catch (e: InvalidPathException) {
            null
        }
    }

    private companion object {
        const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE_NAME = "Aegis"
        const val TRAY_FLAG = "--tray"
    }
}
