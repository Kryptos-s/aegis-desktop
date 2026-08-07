package com.beemdevelopment.aegis.desktop.platform

import com.beemdevelopment.aegis.desktop.platform.awt.AwtClipboard
import com.beemdevelopment.aegis.desktop.platform.linux.LinuxPlatform
import com.beemdevelopment.aegis.desktop.platform.macos.KeychainStore
import com.beemdevelopment.aegis.desktop.platform.macos.MacUserPresence
import com.beemdevelopment.aegis.desktop.platform.macos.MacAutostart
import com.beemdevelopment.aegis.desktop.platform.windows.DpapiSecretStore
import com.beemdevelopment.aegis.desktop.platform.windows.WindowsAutostart

internal fun createPlatform(os: OperatingSystem): Platform = when (os) {
    OperatingSystem.LINUX -> LinuxPlatform()
    OperatingSystem.WINDOWS -> WindowsPlatform()
    OperatingSystem.MACOS -> MacPlatform()
    OperatingSystem.UNKNOWN -> UnsupportedPlatform()
}

internal class WindowsPlatform : Platform {
    override val os: OperatingSystem = OperatingSystem.WINDOWS
    override val paths: AppPaths by lazy { AppPaths.forCurrentOs(OperatingSystem.WINDOWS) }
    override val secretStore: SecretStore by lazy { DpapiSecretStore(paths.dataDir.resolve("keys")) }

    // Windows Hello is WinRT-only (KeyCredentialManager) and needs a native shim we do not have.
    override val userPresence: UserPresence = UnavailableUserPresence
    override val clipboard: SecureClipboard by lazy { AwtClipboard(supportsPrivateHint = false) }
    override val sessionMonitor: SessionMonitor = UnavailableSessionMonitor
    override val autostart: Autostart by lazy { WindowsAutostart() }
}

internal class MacPlatform : Platform {
    override val os: OperatingSystem = OperatingSystem.MACOS
    override val paths: AppPaths by lazy { AppPaths.forCurrentOs(OperatingSystem.MACOS) }
    override val secretStore: SecretStore by lazy { KeychainStore() }
    override val userPresence: UserPresence by lazy { MacUserPresence() }
    override val clipboard: SecureClipboard by lazy { AwtClipboard(supportsPrivateHint = false) }
    override val sessionMonitor: SessionMonitor = UnavailableSessionMonitor
    override val autostart: Autostart by lazy { MacAutostart() }
}

/** Master password and the app's own idle timer, nothing else. */
internal class UnsupportedPlatform : Platform {
    override val os: OperatingSystem = OperatingSystem.UNKNOWN
    override val paths: AppPaths by lazy { AppPaths.forCurrentOs(OperatingSystem.UNKNOWN) }
    override val secretStore: SecretStore = UnavailableSecretStore
    override val userPresence: UserPresence = UnavailableUserPresence
    override val clipboard: SecureClipboard by lazy { AwtClipboard(supportsPrivateHint = false) }
    override val sessionMonitor: SessionMonitor = UnavailableSessionMonitor
    override val autostart: Autostart = UnavailableAutostart
}
