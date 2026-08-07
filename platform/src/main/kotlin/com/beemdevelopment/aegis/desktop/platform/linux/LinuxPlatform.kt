package com.beemdevelopment.aegis.desktop.platform.linux

import com.beemdevelopment.aegis.desktop.platform.AppPaths
import com.beemdevelopment.aegis.desktop.platform.Autostart
import com.beemdevelopment.aegis.desktop.platform.OperatingSystem
import com.beemdevelopment.aegis.desktop.platform.Platform
import com.beemdevelopment.aegis.desktop.platform.SecretStore
import com.beemdevelopment.aegis.desktop.platform.SecureClipboard
import com.beemdevelopment.aegis.desktop.platform.SessionMonitor
import com.beemdevelopment.aegis.desktop.platform.UserPresence
import com.beemdevelopment.aegis.desktop.platform.awt.AwtClipboard

internal class LinuxPlatform : Platform {
    override val os: OperatingSystem = OperatingSystem.LINUX
    override val paths: AppPaths by lazy { AppPaths.forCurrentOs(OperatingSystem.LINUX) }
    override val secretStore: SecretStore by lazy { SecretServiceStore() }
    override val userPresence: UserPresence by lazy { PolkitUserPresence() }
    override val clipboard: SecureClipboard by lazy { AwtClipboard(supportsPrivateHint = true) }
    override val sessionMonitor: SessionMonitor by lazy { DBusSessionMonitor() }
    override val autostart: Autostart by lazy { XdgAutostart() }
}
