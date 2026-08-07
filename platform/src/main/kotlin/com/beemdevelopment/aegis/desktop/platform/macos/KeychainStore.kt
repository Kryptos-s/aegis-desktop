package com.beemdevelopment.aegis.desktop.platform.macos

import com.beemdevelopment.aegis.desktop.platform.SecretStore
import com.beemdevelopment.aegis.desktop.platform.SecretStoreException
import com.beemdevelopment.aegis.desktop.platform.UserPresenceRequiredException

/**
 * Stores unlock keys in the macOS Keychain: the login keychain without a presence requirement, the
 * data protection keychain with one. An item is only visible to the keychain it was written to, so
 * lookups check both rather than remembering which was used.
 */
class KeychainStore : SecretStore {
    private val items = KeychainItems(SERVICE)

    override val isAvailable: Boolean
        get() = items.isAvailable

    override val name: String
        get() = "Keychain"

    override val supportsUserPresence: Boolean
        get() = items.supportsAccessControl

    override fun store(id: String, key: ByteArray, requireUserPresence: Boolean) {
        require(isAvailable) { "The Keychain is not available" }

        // Toggling the presence requirement must not leave a second, weaker copy in the other keychain.
        delete(id)

        val status = items.add(id, key, requireUserPresence)
        if (status == Security.errSecSuccess) {
            return
        }

        if (requireUserPresence && status == Security.errSecMissingEntitlement) {
            throw SecretStoreException(
                "macOS refused an authentication-protected keychain item, because this build is " +
                    "not signed with the keychain-access-groups entitlement. Either sign the app " +
                    "or turn off the user presence requirement.",
            )
        }

        throw SecretStoreException("The Keychain rejected the key (OSStatus $status)")
    }

    override fun retrieve(id: String): ByteArray? {
        if (!isAvailable) {
            return null
        }

        // A presence-protected item wins if one exists, and asking for it is what triggers the prompt.
        if (items.supportsAccessControl) {
            val protected = items.copyData(id, PROMPT, dataProtection = true)
            when (protected.status) {
                Security.errSecSuccess -> return protected.bytes

                Security.errSecUserCanceled, Security.errSecAuthFailed ->
                    throw UserPresenceRequiredException("Authentication was not completed")

                Security.errSecInteractionNotAllowed ->
                    throw UserPresenceRequiredException(
                        "macOS would not show an authentication prompt for this process",
                    )

                Security.errSecItemNotFound, Security.errSecMissingEntitlement -> {
                    // Nothing there, or this process may not use that keychain. Fall through.
                }

                else -> throw SecretStoreException(
                    "The Keychain refused to return the key (OSStatus ${protected.status})",
                )
            }
        }

        val login = items.copyData(id, null, dataProtection = false)
        return when (login.status) {
            Security.errSecSuccess -> login.bytes
            Security.errSecItemNotFound -> null
            Security.errSecUserCanceled, Security.errSecAuthFailed ->
                throw UserPresenceRequiredException("Authentication was not completed")

            else -> throw SecretStoreException(
                "The Keychain refused to return the key (OSStatus ${login.status})",
            )
        }
    }

    override fun delete(id: String) {
        if (!isAvailable) {
            return
        }
        if (items.supportsAccessControl) {
            items.delete(id, dataProtection = true)
        }
        items.delete(id, dataProtection = false)
    }

    override fun contains(id: String): Boolean {
        if (!isAvailable) {
            return false
        }
        if (items.supportsAccessControl &&
            items.exists(id, dataProtection = true) == Security.errSecSuccess
        ) {
            return true
        }
        return items.exists(id, dataProtection = false) == Security.errSecSuccess
    }

    private companion object {
        const val SERVICE = "com.beemdevelopment.aegis"
        const val PROMPT = "Unlock your Aegis vault"
    }
}
