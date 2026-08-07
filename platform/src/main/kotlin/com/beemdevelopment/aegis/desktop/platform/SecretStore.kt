package com.beemdevelopment.aegis.desktop.platform

/**
 * The operating system's secret storage, holding the key that unwraps a vault slot. Such slots
 * reuse the biometric slot type: Aegis for Android rejects a slot type it does not recognize.
 *
 * Machine-local convenience, not a second secret. Anything running as the user can ask the same
 * store for the same key, so the master password always stays a valid way in.
 */
interface SecretStore {
    val isAvailable: Boolean

    /** Name of the backing store, for the settings UI. */
    val name: String

    /** Whether the store itself can gate retrieval on a presence check, without [UserPresence]. */
    val supportsUserPresence: Boolean
        get() = false

    /** Replaces any existing key. The caller owns [key] and should wipe it afterwards. */
    fun store(id: String, key: ByteArray, requireUserPresence: Boolean = false)

    /**
     * Null if no key is stored under [id]; the caller wipes what it gets back. Throws
     * [UserPresenceRequiredException] if authentication was demanded and not completed.
     */
    fun retrieve(id: String): ByteArray?

    fun delete(id: String)

    /** Without unlocking or prompting. */
    fun contains(id: String): Boolean
}

class SecretStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UserPresenceRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)

object UnavailableSecretStore : SecretStore {
    override val isAvailable: Boolean get() = false
    override val name: String get() = "unavailable"

    override fun store(id: String, key: ByteArray, requireUserPresence: Boolean): Nothing =
        throw SecretStoreException("No system secret store is available")

    override fun retrieve(id: String): ByteArray? = null

    override fun delete(id: String) = Unit

    override fun contains(id: String): Boolean = false
}
