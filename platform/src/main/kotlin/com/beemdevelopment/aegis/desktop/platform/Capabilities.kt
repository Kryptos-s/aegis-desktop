package com.beemdevelopment.aegis.desktop.platform

import java.time.Duration

/** A fingerprint, face or account-password check. It gates an action, deriving no key material. */
interface UserPresence {
    val isAvailable: Boolean

    /** What will be used, for the settings UI. */
    val name: String

    /** Blocks until the user authenticates, cancels, or it times out. */
    fun authenticate(reason: String): Boolean
}

object UnavailableUserPresence : UserPresence {
    override val isAvailable: Boolean get() = false
    override val name: String get() = "unavailable"
    override fun authenticate(reason: String): Boolean = false
}

/**
 * The system clipboard, for one-time codes. The "do not record" hint is advisory, and the contents
 * are readable by every process in the session meanwhile.
 */
interface SecureClipboard {
    /** Whether this platform's clipboard understands the hint at all. */
    val copySensitiveIsPrivate: Boolean

    /** [clearAfter] clears the text again if it is still ours; [Duration.ZERO] leaves it. */
    fun copySensitive(text: String, clearAfter: Duration)

    fun readText(): String?

    fun clearIfOurs()
}

/** Session events that should cause the vault to lock. */
interface SessionMonitor {
    val isAvailable: Boolean

    /** Safe to call more than once. */
    fun start(listener: Listener)

    fun stop()

    /** Null when the platform will not say, leaving the app its own last-interaction time. */
    fun systemIdleTime(): Duration?

    interface Listener {
        /** The session was locked, or the screensaver became active. */
        fun onSessionLocked() {}

        fun onSuspend() {}

        fun onResume() {}
    }
}

object UnavailableSessionMonitor : SessionMonitor {
    override val isAvailable: Boolean get() = false
    override fun start(listener: SessionMonitor.Listener) = Unit
    override fun stop() = Unit
    override fun systemIdleTime(): Duration? = null
}

interface Autostart {
    val isAvailable: Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}

object UnavailableAutostart : Autostart {
    override val isAvailable: Boolean get() = false
    override fun isEnabled(): Boolean = false
    override fun setEnabled(enabled: Boolean) = Unit
}
