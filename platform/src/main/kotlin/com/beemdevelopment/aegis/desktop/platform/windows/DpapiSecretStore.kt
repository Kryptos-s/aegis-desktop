package com.beemdevelopment.aegis.desktop.platform.windows

import com.beemdevelopment.aegis.desktop.platform.SecretStore
import com.beemdevelopment.aegis.desktop.platform.SecretStoreException
import com.beemdevelopment.aegis.util.TempFiles
import com.sun.jna.platform.win32.Crypt32
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinCrypt
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Stores unlock keys as DPAPI-protected blobs, one file per id under [dir], since DPAPI has no
 * store of its own. A blob is bound to this user's logon credentials, so losing the DPAPI master
 * key — an administrator resetting a local account's password does this — makes every blob here
 * permanently unreadable.
 */
class DpapiSecretStore(private val dir: Path) : SecretStore {

    /** Whether `Crypt32.dll` resolved. The OS name says nothing about JNA loading it in-process. */
    override val isAvailable: Boolean by lazy {
        try {
            val library: Crypt32? = Crypt32.INSTANCE
            library != null
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: NoClassDefFoundError) {
            false
        } catch (e: ExceptionInInitializerError) {
            false
        }
    }

    override val name: String
        get() = "Windows Data Protection API"

    // CRYPTPROTECT_PROMPT_ON_UNPROTECT only asks for confirmation; it authenticates nobody.
    override val supportsUserPresence: Boolean
        get() = false

    override fun store(id: String, key: ByteArray, requireUserPresence: Boolean) {
        requireCrypt32()
        // A zero-length key makes JNA allocate a zero-byte DATA_BLOB, which fails obscurely.
        if (key.isEmpty()) {
            throw SecretStoreException("Refusing to store an empty key")
        }

        val blob = try {
            Crypt32Util.cryptProtectData(key, ENTROPY, FLAGS, DESCRIPTION, null)
        } catch (e: Win32Exception) {
            throw SecretStoreException("DPAPI was unable to protect the key", e)
        }
        if (blob == null || blob.isEmpty()) {
            throw SecretStoreException("DPAPI returned an empty blob")
        }

        writeAtomically(fileFor(id), blob)
    }

    /** A blob that will not unprotect throws rather than reading as absent. */
    override fun retrieve(id: String): ByteArray? {
        requireCrypt32()
        val file = fileFor(id)
        if (!Files.isRegularFile(file)) {
            return null
        }

        val blob = try {
            Files.readAllBytes(file)
        } catch (e: IOException) {
            throw SecretStoreException("Unable to read the protected key from $file", e)
        }
        if (blob.isEmpty()) {
            throw SecretStoreException("The protected key at $file is empty")
        }

        return try {
            Crypt32Util.cryptUnprotectData(blob, ENTROPY, FLAGS, null)
        } catch (e: Win32Exception) {
            throw SecretStoreException(
                "DPAPI was unable to unprotect the key at $file. It belongs to another user, was " +
                    "written by another application, or the user's master key is no longer available",
                e,
            )
        }
    }

    /** [TempFiles.shred] swallows its own failures, so the file is checked afterwards. */
    override fun delete(id: String) {
        val file = fileFor(id)
        TempFiles.shred(file)
        if (Files.exists(file)) {
            throw SecretStoreException("Unable to remove the protected key at $file")
        }
    }

    override fun contains(id: String): Boolean = Files.isRegularFile(fileFor(id))

    private fun requireCrypt32() {
        if (!isAvailable) {
            throw SecretStoreException("The Windows Data Protection API is not available in this process")
        }
    }

    /** Hashing sanitises the id: no id can escape [dir] or hit a reserved Windows device name. */
    private fun fileFor(id: String): Path =
        dir.resolve(HexFormat.of().formatHex(sha256(id)) + FILE_SUFFIX)

    /**
     * The force before the rename matters: the rename publishes the file, so data reaching the disk
     * after it would leave a truncated blob that no longer unprotects.
     */
    private fun writeAtomically(file: Path, blob: ByteArray) {
        try {
            Files.createDirectories(dir)
            TempFiles.restrictToOwner(dir)

            val temp = Files.createTempFile(dir, "key", ".tmp")
            try {
                TempFiles.restrictToOwner(temp)
                FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                    val buffer = ByteBuffer.wrap(blob)
                    while (buffer.hasRemaining()) {
                        channel.write(buffer)
                    }
                    channel.force(true)
                }

                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                TempFiles.shred(temp)
                throw e
            }
        } catch (e: IOException) {
            throw SecretStoreException("Unable to write the protected key to $file", e)
        }
    }

    private companion object {
        const val FILE_SUFFIX = ".dpapi"

        // CRYPTPROTECT_LOCAL_MACHINE is deliberately not set: it would key the blob to the machine
        // rather than to this user, letting any account on it unprotect the blob.
        val FLAGS: Int = WinCrypt.CRYPTPROTECT_UI_FORBIDDEN

        /** Recorded inside the blob by DPAPI and readable by anyone who can unprotect it. */
        const val DESCRIPTION = "Aegis vault unlock key"

        const val ENTROPY_LABEL = "com.beemdevelopment.aegis.dpapi.unlock-key.v1"

        /** Domain separation, not a secret: it ships in the binary. */
        val ENTROPY: ByteArray = sha256(ENTROPY_LABEL)

        fun sha256(value: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
    }
}
