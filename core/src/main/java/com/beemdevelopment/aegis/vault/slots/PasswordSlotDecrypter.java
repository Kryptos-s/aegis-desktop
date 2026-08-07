package com.beemdevelopment.aegis.vault.slots;

import com.beemdevelopment.aegis.crypto.CryptoUtils;
import com.beemdevelopment.aegis.crypto.MasterKey;

import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * Recovers the vault master key from a password slot. Deriving the slot key runs scrypt with
 * N=2^15, on the order of a second, so callers must stay off the UI thread.
 */
public final class PasswordSlotDecrypter {
    private PasswordSlotDecrypter() {

    }

    /** Returns the result for the first slot the password opens, or null if it opens none. */
    public static Result decrypt(List<PasswordSlot> slots, char[] password) {
        for (PasswordSlot slot : slots) {
            try {
                return decryptPasswordSlot(slot, password);
            } catch (SlotException e) {
                throw new RuntimeException(e);
            } catch (SlotIntegrityException ignored) {
                // Wrong password for this slot.
            }
        }

        return null;
    }

    public static Result decryptPasswordSlot(PasswordSlot slot, char[] password)
            throws SlotIntegrityException, SlotException {
        MasterKey masterKey;
        SecretKey key = slot.deriveKey(password);
        byte[] oldPasswordBytes = CryptoUtils.toBytesOld(password);

        try {
            masterKey = decryptPasswordSlot(slot, key);
        } catch (SlotIntegrityException e) {
            // A bug introduced in afb9e59 caused passwords longer than 64 bytes to produce a
            // different key than before, so try again with the old password encoding.
            if (slot.isRepaired() || oldPasswordBytes.length <= 64) {
                throw e;
            }

            SecretKey oldKey = slot.deriveKey(oldPasswordBytes);
            try {
                masterKey = decryptPasswordSlot(slot, oldKey);
            } finally {
                Arrays.fill(oldPasswordBytes, (byte) 0);
            }
        }

        // If necessary, repair the slot by re-encrypting the master key with the correct key.
        // Slots with passwords smaller than 64 bytes also get this treatment, so that those end up
        // with 'repaired' set to true as well.
        boolean repaired = false;
        if (!slot.isRepaired()) {
            Cipher cipher = Slot.createEncryptCipher(key);
            slot.setKey(masterKey, cipher);
            repaired = true;
        }

        return new Result(masterKey, slot, repaired);
    }

    public static MasterKey decryptPasswordSlot(PasswordSlot slot, SecretKey key)
            throws SlotException, SlotIntegrityException {
        Cipher cipher = slot.createDecryptCipher(key);
        return slot.getKey(cipher);
    }

    public static class Result {
        private final MasterKey _key;
        private final PasswordSlot _slot;
        private final boolean _repaired;

        public Result(MasterKey key, PasswordSlot slot, boolean repaired) {
            _key = key;
            _slot = slot;
            _repaired = repaired;
        }

        public Result(MasterKey key, PasswordSlot slot) {
            this(key, slot, false);
        }

        public MasterKey getKey() {
            return _key;
        }

        public Slot getSlot() {
            return _slot;
        }

        public boolean isSlotRepaired() {
            return _repaired;
        }
    }
}
