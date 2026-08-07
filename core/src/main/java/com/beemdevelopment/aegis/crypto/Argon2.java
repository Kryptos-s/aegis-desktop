package com.beemdevelopment.aegis.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Argon2 for the Stratum and Proton Authenticator importers. Slow and memory-hungry by design, so
 * callers must stay off the UI thread.
 */
public final class Argon2 {
    private Argon2() {

    }

    public static SecretKey deriveKey(Params params) {
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(params.getArgon2Params());

        byte[] key = new byte[params.getKeySize()];
        try {
            gen.generateBytes(params.getPassword(), key);
            return new SecretKeySpec(key, 0, key.length, "AES");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static class Params {
        private final char[] _password;
        private final Argon2Parameters _argon2Params;
        private final int _keySize;

        public Params(char[] password, Argon2Parameters argon2Params, int keySize) {
            _password = password;
            _argon2Params = argon2Params;
            _keySize = keySize;
        }

        public char[] getPassword() {
            return _password;
        }

        public Argon2Parameters getArgon2Params() {
            return _argon2Params;
        }

        public int getKeySize() {
            return _keySize;
        }
    }
}
