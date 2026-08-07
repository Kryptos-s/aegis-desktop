package com.beemdevelopment.aegis.crypto;

import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * PBKDF2 for the third-party importers; the Aegis vault itself uses {@link CryptoUtils#deriveKey}.
 * Slow by design, so callers must stay off the UI thread.
 */
public final class PBKDF {
    private PBKDF() {

    }

    public static SecretKey deriveKey(Params params) {
        try {
            // SHA512 goes through BouncyCastle to stay bit-identical with the Android app, which
            // uses it because Android < 26 lacks PBKDF2withHmacSHA512.
            if (params.getAlgorithm().equals("PBKDF2withHmacSHA512")) {
                PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
                byte[] passwordBytes = CryptoUtils.toBytes(params.getPassword());
                try {
                    gen.init(passwordBytes, params.getSalt(), params.getIterations());
                    byte[] key = ((KeyParameter) gen.generateDerivedParameters(params.getKeySize())).getKey();
                    try {
                        return new SecretKeySpec(key, "AES");
                    } finally {
                        Arrays.fill(key, (byte) 0);
                    }
                } finally {
                    Arrays.fill(passwordBytes, (byte) 0);
                }
            }

            SecretKeyFactory factory = SecretKeyFactory.getInstance(params.getAlgorithm());
            KeySpec spec = new PBEKeySpec(params.getPassword(), params.getSalt(), params.getIterations(), params.getKeySize());
            SecretKey key = factory.generateSecret(spec);
            return new SecretKeySpec(key.getEncoded(), "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Params {
        private final String _algorithm;
        private final int _keySize;
        private final char[] _password;
        private final byte[] _salt;
        private final int _iterations;

        public Params(String algorithm, int keySize, char[] password, byte[] salt, int iterations) {
            _algorithm = algorithm;
            _keySize = keySize;
            _iterations = iterations;
            _password = password;
            _salt = salt;
        }

        public String getAlgorithm() {
            return _algorithm;
        }

        public int getKeySize() {
            return _keySize;
        }

        public char[] getPassword() {
            return _password;
        }

        public int getIterations() {
            return _iterations;
        }

        public byte[] getSalt() {
            return _salt;
        }
    }
}
