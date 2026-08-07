package com.beemdevelopment.aegis.desktop.platform.linux

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/**
 * Minimal binding to libsecret, the freedesktop Secret Service client.
 *
 * Secrets cross as raw [Pointer]s rather than [String]s so their bytes live in memory this code can
 * zero; JNA-marshalled strings leave copies on both sides that cannot be.
 */
internal interface LibSecret : Library {

    /** Varargs are attribute name/value pairs terminated by a null. */
    fun secret_password_store_sync(
        schema: Pointer,
        collection: String?,
        label: String,
        password: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?,
    ): Int

    /** Returns null if there is no matching entry, otherwise a string to free with [secret_password_free]. */
    fun secret_password_lookup_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?,
    ): Pointer?

    fun secret_password_clear_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?,
    ): Int

    /** Frees a password returned by libsecret, wiping it first. */
    fun secret_password_free(password: Pointer?)

    /** Varargs are attribute name/type pairs terminated by a null. */
    fun secret_schema_new(name: String, flags: Int, vararg attributes: Any?): Pointer?

    fun secret_schema_unref(schema: Pointer?)

    companion object {
        const val SECRET_SCHEMA_NONE = 0
        const val SECRET_SCHEMA_ATTRIBUTE_STRING = 0

        /** The user's default collection, which is the login keyring on most systems. */
        const val COLLECTION_DEFAULT = "default"

        val instance: LibSecret? by lazy {
            try {
                Native.load("secret-1", LibSecret::class.java)
            } catch (e: UnsatisfiedLinkError) {
                null
            } catch (e: NoClassDefFoundError) {
                null
            }
        }
    }
}

/** Frees a `GError`. */
internal interface GLib : Library {
    fun g_error_free(error: Pointer)

    companion object {
        val instance: GLib? by lazy {
            try {
                Native.load("glib-2.0", GLib::class.java)
            } catch (e: UnsatisfiedLinkError) {
                null
            } catch (e: NoClassDefFoundError) {
                null
            }
        }
    }
}

/**
 * Reads the message out of a `GError`, frees it, and clears the reference. The message pointer sits
 * at offset 8: `GQuark domain; gint code; gchar *message;` with 8-byte pointer alignment.
 */
internal fun PointerByReference.takeErrorMessage(): String? {
    val error = this.value ?: return null
    val message = try {
        error.getPointer(8)?.getString(0)
    } catch (e: Throwable) {
        null
    }
    GLib.instance?.g_error_free(error)
    this.value = null
    return message
}

/** Reads a NUL-terminated string libsecret allocated, frees it, and returns the bytes. */
internal fun Pointer.readSecretBytesAndFree(lib: LibSecret): ByteArray {
    var length = 0L
    while (this.getByte(length) != 0.toByte()) {
        length++
    }
    val bytes = this.getByteArray(0, length.toInt())
    lib.secret_password_free(this)
    return bytes
}

/** Copies bytes into native memory that is zeroed before it is released. */
internal inline fun <T> withNativeBytes(bytes: ByteArray, block: (Memory) -> T): T {
    val memory = Memory((bytes.size + 1).toLong())
    return try {
        memory.write(0, bytes, 0, bytes.size)
        memory.setByte(bytes.size.toLong(), 0)
        block(memory)
    } finally {
        memory.clear()
        memory.close()
    }
}
