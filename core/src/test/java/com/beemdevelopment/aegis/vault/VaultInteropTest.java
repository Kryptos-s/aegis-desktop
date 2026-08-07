package com.beemdevelopment.aegis.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.beemdevelopment.aegis.crypto.CryptoUtils;
import com.beemdevelopment.aegis.crypto.MasterKey;
import com.beemdevelopment.aegis.crypto.SCryptParameters;
import com.beemdevelopment.aegis.encoding.Base32;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.vault.slots.PasswordSlot;
import com.beemdevelopment.aegis.vault.slots.PasswordSlotDecrypter;
import com.beemdevelopment.aegis.vault.slots.Slot;
import com.beemdevelopment.aegis.vault.slots.SlotList;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/** Checks that vaults written here are byte-compatible with the ones the Android app writes. */
public class VaultInteropTest {
    /** The password for the checked-in encrypted vault vector. */
    private static final char[] PASSWORD = "test".toCharArray();

    @Test
    public void testReadEncryptedVaultWrittenByAndroid() throws Exception {
        VaultFile file = VaultFile.fromBytes(readVector("aegis_encrypted.json"));
        assertTrue(file.isEncrypted());

        VaultFileCredentials creds = unlock(file);
        Vault vault = Vault.fromJson(file.getContent(creds));
        assertFalse(vault.getEntries().getValues().isEmpty());
    }

    @Test
    public void testEncryptedVaultRoundTrip() throws Exception {
        VaultFile original = VaultFile.fromBytes(readVector("aegis_encrypted.json"));
        VaultFileCredentials creds = unlock(original);
        Vault vault = Vault.fromJson(original.getContent(creds));

        VaultFile written = new VaultFile();
        written.setContent(vault.toJson(), creds);
        byte[] bytes = written.toBytes();

        VaultFile reread = VaultFile.fromBytes(bytes);
        assertTrue(reread.isEncrypted());
        Vault rereadVault = Vault.fromJson(reread.getContent(unlock(reread)));

        assertEquals(vault.getEntries().getValues().size(), rereadVault.getEntries().getValues().size());
        assertEquals(vault.toJson().toString(), rereadVault.toJson().toString());
    }

    /** Aegis for Android rejects the file outright if any of these names or types drift. */
    @Test
    public void testEncryptedFileStructure() throws Exception {
        Vault vault = new Vault();
        vault.getEntries().add(new VaultEntry(
                new TotpInfo(Base32.decode("JBSWY3DPEHPK3PXP")), "alice", "Example"));

        VaultFileCredentials creds = newCredentials();
        VaultFile file = new VaultFile();
        file.setContent(vault.toJson(), creds);

        JSONObject obj = new JSONObject(new String(file.toBytes(), StandardCharsets.UTF_8));
        assertEquals(1, obj.getInt("version"));

        JSONObject header = obj.getJSONObject("header");
        JSONObject params = header.getJSONObject("params");
        assertEquals(24, params.getString("nonce").length());   // 12 bytes, hex
        assertEquals(32, params.getString("tag").length());     // 16 bytes, hex

        JSONObject slot = header.getJSONArray("slots").getJSONObject(0);
        assertEquals(Slot.TYPE_PASSWORD, slot.getInt("type"));
        assertEquals(CryptoUtils.CRYPTO_SCRYPT_N, slot.getInt("n"));
        assertEquals(CryptoUtils.CRYPTO_SCRYPT_r, slot.getInt("r"));
        assertEquals(CryptoUtils.CRYPTO_SCRYPT_p, slot.getInt("p"));
        assertEquals(64, slot.getString("salt").length());      // 32 bytes, hex
        assertTrue(slot.getBoolean("repaired"));
        assertFalse(slot.getBoolean("is_backup"));

        // The encrypted vault is a base64 string, not a nested object.
        assertTrue(obj.get("db") instanceof String);
    }

    @Test
    public void testPlainFileStructure() {
        Vault vault = new Vault();
        VaultFile file = new VaultFile();
        file.setContent(vault.toJson());

        assertFalse(file.isEncrypted());

        JSONObject obj = file.toJson();
        assertEquals(1, obj.getInt("version"));
        assertTrue(obj.getJSONObject("header").isNull("slots"));
        assertTrue(obj.getJSONObject("header").isNull("params"));
        // A plaintext vault stores the database as an object, not a string.
        assertTrue(obj.get("db") instanceof JSONObject);
        assertEquals(3, obj.getJSONObject("db").getInt("version"));
    }

    @Test
    public void testStoreWritesPrivateFileAtomically() throws Exception {
        Path dir = Files.createTempDirectory("aegis-store-test");
        try {
            VaultStore store = new VaultStore(dir.resolve("aegis.json"));
            assertFalse(store.exists());

            Vault vault = new Vault();
            vault.getEntries().add(new VaultEntry(
                    new TotpInfo(Base32.decode("JBSWY3DPEHPK3PXP")), "alice", "Example"));

            VaultRepository repo = new VaultRepository(store, vault, newCredentials());
            repo.save();

            assertTrue(store.exists());
            if (Files.getFileStore(store.getPath()).supportsFileAttributeView("posix")) {
                assertEquals("rw-------",
                        java.nio.file.attribute.PosixFilePermissions.toString(
                                Files.getPosixFilePermissions(store.getPath())));
            }

            try (var stream = Files.list(dir)) {
                assertEquals(1, stream.count());
            }

            VaultFile reread = VaultRepository.readVaultFile(store);
            assertTrue(reread.isEncrypted());
        } finally {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    public void testExportStripsBiometricSlot() throws Exception {
        VaultFileCredentials creds = newCredentials();

        // The desktop keychain slot reuses the biometric slot type: Aegis for Android rejects
        // unknown slot types, and strips this one on export just like its own.
        com.beemdevelopment.aegis.vault.slots.BiometricSlot keychainSlot =
                new com.beemdevelopment.aegis.vault.slots.BiometricSlot();
        SecretKey wrappingKey = CryptoUtils.generateKey();
        keychainSlot.setKey(creds.getKey(), Slot.createEncryptCipher(wrappingKey));
        creds.getSlots().add(keychainSlot);

        VaultFile file = new VaultFile();
        file.setContent(new Vault().toJson(), creds);
        assertEquals(2, file.getHeader().getSlots().getValues().size());

        VaultFile exportable = file.exportable();
        assertEquals(1, exportable.getHeader().getSlots().getValues().size());
        assertEquals(Slot.TYPE_PASSWORD,
                exportable.getHeader().getSlots().iterator().next().getType());
    }

    private static VaultFileCredentials newCredentials() throws Exception {
        VaultFileCredentials creds = new VaultFileCredentials();
        PasswordSlot slot = new PasswordSlot();
        SCryptParameters params = new SCryptParameters(
                CryptoUtils.CRYPTO_SCRYPT_N,
                CryptoUtils.CRYPTO_SCRYPT_r,
                CryptoUtils.CRYPTO_SCRYPT_p,
                CryptoUtils.generateSalt());
        SecretKey key = slot.deriveKey(PASSWORD, params);
        Cipher cipher = Slot.createEncryptCipher(key);
        slot.setKey(creds.getKey(), cipher);
        creds.getSlots().add(slot);
        return creds;
    }

    private static VaultFileCredentials unlock(VaultFile file) {
        SlotList slots = file.getHeader().getSlots();
        List<PasswordSlot> passwordSlots = slots.findAll(PasswordSlot.class);
        PasswordSlotDecrypter.Result result = PasswordSlotDecrypter.decrypt(passwordSlots, PASSWORD);
        assertNotNull("Password did not open any slot", result);

        MasterKey key = result.getKey();
        return new VaultFileCredentials(key, slots);
    }

    private static byte[] readVector(String name) throws Exception {
        try (InputStream stream = VaultInteropTest.class.getResourceAsStream(
                "/com/beemdevelopment/aegis/importers/" + name)) {
            assertNotNull("Missing test vector: " + name, stream);
            return IOUtils.readAll(stream);
        }
    }
}
