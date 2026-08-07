package com.beemdevelopment.aegis.desktop.platform.linux

import com.beemdevelopment.aegis.desktop.platform.SecretStore
import com.beemdevelopment.aegis.desktop.platform.SecretStoreException
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.util.Base64

/**
 * Stores unlock keys in the freedesktop Secret Service: GNOME Keyring, KWallet, KeePassXC or any
 * other `org.freedesktop.secrets` implementation.
 *
 * Keys are base64-encoded because the Secret Service API is defined in terms of text.
 */
class SecretServiceStore : SecretStore {
    private val lib: LibSecret? = LibSecret.instance

    /** Built once and kept for the process lifetime; libsecret matches entries on the attributes. */
    private val schema: Pointer? by lazy {
        lib?.secret_schema_new(
            SCHEMA_NAME,
            LibSecret.SECRET_SCHEMA_NONE,
            ATTR_ID, LibSecret.SECRET_SCHEMA_ATTRIBUTE_STRING,
            ATTR_APPLICATION, LibSecret.SECRET_SCHEMA_ATTRIBUTE_STRING,
            null,
        )
    }

    override val isAvailable: Boolean
        get() = lib != null && schema != null

    override val name: String
        get() = "Secret Service"

    // The Secret Service cannot require a fresh authentication per retrieval; that is
    // [PolkitUserPresence]'s job.
    override val supportsUserPresence: Boolean
        get() = false

    override fun store(id: String, key: ByteArray, requireUserPresence: Boolean) {
        val lib = requireLib()
        val encoded = Base64.getEncoder().encode(key)
        try {
            withNativeBytes(encoded) { password ->
                val error = PointerByReference()
                val result = lib.secret_password_store_sync(
                    requireSchema(),
                    LibSecret.COLLECTION_DEFAULT,
                    label(id),
                    password,
                    null,
                    error,
                    ATTR_ID, id,
                    ATTR_APPLICATION, APPLICATION,
                    null,
                )
                val message = error.takeErrorMessage()
                if (result == 0) {
                    throw SecretStoreException(
                        "Unable to store the key in the Secret Service" +
                            (message?.let { ": $it" } ?: ""),
                    )
                }
            }
        } finally {
            encoded.fill(0)
        }
    }

    override fun retrieve(id: String): ByteArray? {
        val lib = requireLib()
        val error = PointerByReference()
        val result = lib.secret_password_lookup_sync(
            requireSchema(),
            null,
            error,
            ATTR_ID, id,
            ATTR_APPLICATION, APPLICATION,
            null,
        )

        val message = error.takeErrorMessage()
        if (result == null) {
            if (message != null) {
                throw SecretStoreException("Unable to read the key from the Secret Service: $message")
            }
            return null
        }

        val encoded = result.readSecretBytesAndFree(lib)
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw SecretStoreException("The stored key is corrupt", e)
        } finally {
            encoded.fill(0)
        }
    }

    override fun delete(id: String) {
        val lib = requireLib()
        val error = PointerByReference()
        lib.secret_password_clear_sync(
            requireSchema(),
            null,
            error,
            ATTR_ID, id,
            ATTR_APPLICATION, APPLICATION,
            null,
        )
        error.takeErrorMessage()
    }

    /** Reads the secret and wipes it: the Secret Service has no existence query that does not. */
    override fun contains(id: String): Boolean {
        val key = try {
            retrieve(id)
        } catch (e: SecretStoreException) {
            return false
        } ?: return false
        key.fill(0)
        return true
    }

    private fun requireLib(): LibSecret =
        lib ?: throw SecretStoreException("libsecret is not available on this system")

    private fun requireSchema(): Pointer =
        schema ?: throw SecretStoreException("Unable to create a libsecret schema")

    private fun label(id: String) = "Aegis vault unlock key ($id)"

    private companion object {
        const val SCHEMA_NAME = "com.beemdevelopment.aegis.UnlockKey"
        const val ATTR_ID = "slot"
        const val ATTR_APPLICATION = "application"
        const val APPLICATION = "aegis"
    }
}
