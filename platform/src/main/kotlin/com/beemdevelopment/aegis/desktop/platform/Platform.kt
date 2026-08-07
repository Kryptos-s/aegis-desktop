package com.beemdevelopment.aegis.desktop.platform

import java.util.Locale

enum class OperatingSystem {
    LINUX,
    WINDOWS,
    MACOS,
    UNKNOWN;

    companion object {
        val current: OperatingSystem by lazy {
            val name = System.getProperty("os.name", "").lowercase(Locale.ROOT)
            when {
                name.contains("linux") || name.contains("bsd") -> LINUX
                name.contains("win") -> WINDOWS
                name.contains("mac") || name.contains("darwin") -> MACOS
                else -> UNKNOWN
            }
        }
    }
}

/** The desktop session type, on Linux. [OTHER] on every other OS. */
enum class SessionType {
    WAYLAND,
    X11,
    OTHER;

    companion object {
        val current: SessionType by lazy {
            when {
                OperatingSystem.current != OperatingSystem.LINUX -> OTHER
                !System.getenv("WAYLAND_DISPLAY").isNullOrBlank() -> WAYLAND
                System.getenv("XDG_SESSION_TYPE")?.lowercase(Locale.ROOT) == "wayland" -> WAYLAND
                !System.getenv("DISPLAY").isNullOrBlank() -> X11
                else -> OTHER
            }
        }
    }
}

/** Everything the app needs from the operating system. Each capability may report itself unavailable. */
interface Platform {
    val os: OperatingSystem
    val paths: AppPaths
    val secretStore: SecretStore
    val userPresence: UserPresence
    val clipboard: SecureClipboard
    val sessionMonitor: SessionMonitor
    val autostart: Autostart

    companion object {
        val current: Platform by lazy { createPlatform(OperatingSystem.current) }
    }
}
