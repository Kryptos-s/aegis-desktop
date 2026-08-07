package com.beemdevelopment.aegis.desktop.platform.macos

import com.beemdevelopment.aegis.desktop.platform.UserPresence
import java.security.SecureRandom

/**
 * A Touch ID or account-password check, done by reading a Keychain item behind a user-presence
 * access control. `LAContext.evaluatePolicy` is Objective-C-only and its block callback cannot be
 * bound safely through JNA; the Keychain reaches the same prompt through a plain C API.
 *
 * The item is a dedicated sentinel, so a presence check does not depend on keychain unlock being on.
 */
class MacUserPresence : UserPresence {
    private val items = KeychainItems(SERVICE)

    override val isAvailable: Boolean
        get() = items.isAvailable && items.supportsAccessControl

    override val name: String
        get() = "Touch ID"

    override fun authenticate(reason: String): Boolean {
        if (!isAvailable) {
            return false
        }

        if (!ensureSentinelExists()) {
            return false
        }

        val result = items.copyData(ACCOUNT, reason, dataProtection = true)
        return when (result.status) {
            Security.errSecSuccess -> {
                result.bytes?.fill(0)
                true
            }

            // Cancelled, failed, no prompt allowed, item gone: all refusals.
            else -> false
        }
    }

    /** Contents are random and never used; what matters is that reading them requires authentication. */
    private fun ensureSentinelExists(): Boolean {
        if (items.exists(ACCOUNT, dataProtection = true) == Security.errSecSuccess) {
            return true
        }

        val sentinel = ByteArray(32)
        SecureRandom().nextBytes(sentinel)
        return try {
            val status = items.add(ACCOUNT, sentinel, requireUserPresence = true)
            status == Security.errSecSuccess || status == Security.errSecDuplicateItem
        } finally {
            sentinel.fill(0)
        }
    }

    private companion object {
        const val SERVICE = "com.beemdevelopment.aegis.presence"
        const val ACCOUNT = "user-presence-sentinel"
    }
}
