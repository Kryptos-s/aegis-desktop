package com.beemdevelopment.aegis.desktop.vault

import com.beemdevelopment.aegis.desktop.Preferences
import com.beemdevelopment.aegis.desktop.platform.AppPaths
import com.beemdevelopment.aegis.desktop.platform.Autostart
import com.beemdevelopment.aegis.desktop.platform.OperatingSystem
import com.beemdevelopment.aegis.desktop.platform.Platform
import com.beemdevelopment.aegis.desktop.platform.SecretStore
import com.beemdevelopment.aegis.desktop.platform.SecureClipboard
import com.beemdevelopment.aegis.desktop.platform.SessionMonitor
import com.beemdevelopment.aegis.desktop.platform.UnavailableAutostart
import com.beemdevelopment.aegis.desktop.platform.UnavailableSecretStore
import com.beemdevelopment.aegis.desktop.platform.UnavailableSessionMonitor
import com.beemdevelopment.aegis.desktop.platform.UnavailableUserPresence
import com.beemdevelopment.aegis.desktop.platform.UserPresence
import com.beemdevelopment.aegis.vault.VaultRepositoryException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Comparator

class VaultManagerRestoreTest {
    private lateinit var dir: Path
    private lateinit var manager: VaultManager
    private lateinit var paths: AppPaths

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("aegis-restore-test")
        paths = AppPaths(dir, dir, dir.resolve("cache"))
        paths.createDirectories()
        manager = VaultManager(
            com.beemdevelopment.aegis.vault.VaultStore(paths.vaultFile),
            Preferences(paths.preferencesFile),
            AuditLog(paths.auditLogFile),
            TestPlatform(paths),
        )
    }

    @After
    fun tearDown() {
        manager.shutdown()
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach {
            runCatching { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun restoresAnEncryptedVaultAndAsksForThePassword() {
        val source = copyVector("aegis_encrypted.json")

        val needsPassword = manager.restoreFrom(source)

        assertTrue("An encrypted vault must ask for a password", needsPassword)
        assertFalse("It must not be unlocked yet", manager.isUnlocked)
        assertTrue(Files.exists(paths.vaultFile))

        // The file is taken as it stands, so the original password still opens it.
        val vaultFile = manager.readVaultFile()
        assertTrue(vaultFile.isEncrypted)
        val repo = manager.unlockWithPassword(vaultFile, "test".toCharArray())
        assertTrue(repo.entries.isNotEmpty())
    }

    @Test
    fun restoresAPlaintextVaultAndOpensIt() {
        val source = copyVector("aegis_plain.json")

        val needsPassword = manager.restoreFrom(source)

        assertFalse("A plaintext vault has no password to ask for", needsPassword)
        assertTrue("It should already be open", manager.isUnlocked)
        assertTrue(manager.vault.entries.isNotEmpty())
    }

    @Test
    fun rejectsAFileThatIsNotAVault() {
        val junk = dir.resolve("not-a-vault.json")
        Files.writeString(junk, "{\"hello\":\"world\"}")

        try {
            manager.restoreFrom(junk)
            fail("Expected the restore to be rejected")
        } catch (e: VaultRepositoryException) {
            // Expected.
        }

        assertFalse("Nothing should have been written", Files.exists(paths.vaultFile))
    }

    @Test
    fun keepsACopyOfTheVaultItReplaces() {
        val first = copyVector("aegis_plain.json")
        manager.restoreFrom(first)
        val originalBytes = Files.readAllBytes(paths.vaultFile)

        manager.lock(userInitiated = true)

        val second = copyVector("aegis_encrypted.json")
        manager.restoreFrom(second)

        val replaced = paths.vaultFile.resolveSibling("aegis.json.replaced")
        assertTrue("The vault it replaced must still be recoverable", Files.exists(replaced))
        assertArrayEqualsBytes(originalBytes, Files.readAllBytes(replaced))
    }

    @Test
    fun restoredVaultIsWrittenOwnerOnly() {
        manager.restoreFrom(copyVector("aegis_encrypted.json"))

        if (Files.getFileStore(paths.vaultFile).supportsFileAttributeView("posix")) {
            assertEquals(
                "rw-------",
                java.nio.file.attribute.PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(paths.vaultFile),
                ),
            )
        }
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        assertTrue(expected.contentEquals(actual))
    }

    private fun copyVector(name: String): Path {
        val target = dir.resolve("source-$name")
        val stream = javaClass.getResourceAsStream("/com/beemdevelopment/aegis/importers/$name")
            ?: error("Missing test vector: $name")
        stream.use { Files.copy(it, target) }
        return target
    }

    /** A platform with nothing available. */
    private class TestPlatform(override val paths: AppPaths) : Platform {
        override val os: OperatingSystem = OperatingSystem.UNKNOWN
        override val secretStore: SecretStore = UnavailableSecretStore
        override val userPresence: UserPresence = UnavailableUserPresence
        override val sessionMonitor: SessionMonitor = UnavailableSessionMonitor
        override val autostart: Autostart = UnavailableAutostart
        override val clipboard: SecureClipboard = object : SecureClipboard {
            override val copySensitiveIsPrivate: Boolean = false
            override fun copySensitive(text: String, clearAfter: Duration) = Unit
            override fun readText(): String? = null
            override fun clearIfOurs() = Unit
        }
    }
}
