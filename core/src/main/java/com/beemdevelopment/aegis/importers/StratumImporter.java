package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.crypto.Argon2;
import com.beemdevelopment.aegis.crypto.PBKDF;
import com.beemdevelopment.aegis.encoding.Base32;
import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.otp.HotpInfo;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.SteamInfo;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.vault.VaultEntry;

import org.bouncycastle.crypto.params.Argon2Parameters;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

public class StratumImporter extends DatabaseImporter {
    private static final String HEADER = "AUTHENTICATORPRO";
    private static final String HEADER_LEGACY = "AuthenticatorPro";

    private enum Algorithm {
        SHA1,
        SHA256,
        SHA512
    }

    @Override
    protected State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        return isInternal ? readInternal(stream) : readExternal(stream);
    }

    private State readInternal(InputStream stream) throws DatabaseImporterException {
        List<SqlEntry> entries = new SqlImporterHelper().read(SqlEntry.class, stream, "authenticator");
        return new SqlState(entries);
    }

    private static State readExternal(InputStream stream) throws DatabaseImporterException {
        byte[] data;
        try {
            data = IOUtils.readAll(stream);
        } catch (IOException e) {
            throw new DatabaseImporterException(e);
        }

        try {
            return new JsonState(new JSONObject(new String(data, StandardCharsets.UTF_8)));
        } catch (JSONException e) {
            return readEncrypted(new DataInputStream(new ByteArrayInputStream(data)));
        }
    }

    private static State readEncrypted(DataInputStream stream) throws DatabaseImporterException {
        try {
            byte[] headerBytes = new byte[HEADER.getBytes(StandardCharsets.UTF_8).length];
            stream.readFully(headerBytes);
            String header = new String(headerBytes, StandardCharsets.UTF_8);
            switch (header) {
                case HEADER:
                    return EncryptedState.parseHeader(stream);
                case HEADER_LEGACY:
                    return LegacyEncryptedState.parseHeader(stream);
                default:
                    throw new DatabaseImporterException("Invalid file header");
            }
        } catch (UTFDataFormatException e) {
            throw new DatabaseImporterException("Invalid file header");
        } catch (IOException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new DatabaseImporterException(e);
        }
    }

    private static OtpInfo parseOtpInfo(int type, byte[] secret, Algorithm algo, int digits, int period, int counter)
            throws OtpInfoException, DatabaseImporterEntryException {
        switch (type) {
            case 1:
                return new HotpInfo(secret, algo.name(), digits, counter);
            case 2:
                return new TotpInfo(secret, algo.name(), digits, period);
            case 4:
                return new SteamInfo(secret, algo.name(), digits, period);
            default:
                throw new DatabaseImporterEntryException(String.format("Unsupported otp type: %d", type), null);
        }
    }

    static class EncryptedState extends State {
        private static final int KEY_SIZE = 32;
        private static final int MEMORY_COST = 16; // 2^16 KiB = 64 MiB
        private static final int PARALLELISM = 4;
        private static final int ITERATIONS = 3;
        private static final int SALT_SIZE = 16;
        private static final int IV_SIZE = 12;
        private static final int TAG_SIZE = 16;

        private final Cipher _cipher;
        private final byte[] _salt;
        private final byte[] _iv;
        private final byte[] _data;

        public EncryptedState(Cipher cipher, byte[] salt, byte[] iv, byte[] data) {
            super(true);
            _cipher = cipher;
            _salt = salt;
            _iv = iv;
            _data = data;
        }

        @Override
        public JsonState decrypt(char[] password) throws DatabaseImporterException {
            Argon2.Params params = getKeyDerivationParams(password);
            SecretKey key = Argon2.deriveKey(params);
            return decrypt(key);
        }

        public JsonState decrypt(SecretKey key) throws DatabaseImporterException {
            try {
                _cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE * Byte.SIZE, _iv));
                byte[] decrypted = _cipher.doFinal(_data);
                return new JsonState(new JSONObject(new String(decrypted, StandardCharsets.UTF_8)));
            } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException
                     | JSONException | InvalidKeyException | BadPaddingException e) {
                throw new DatabaseImporterException(e);
            }
        }

        private Argon2.Params getKeyDerivationParams(char[] password) {
            Argon2Parameters argon2Params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withIterations(ITERATIONS)
                    .withParallelism(PARALLELISM)
                    .withMemoryPowOfTwo(MEMORY_COST)
                    .withSalt(_salt)
                    .build();
            return new Argon2.Params(password, argon2Params, KEY_SIZE);
        }

        private static EncryptedState parseHeader(DataInputStream stream)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException {
            byte[] salt = new byte[SALT_SIZE];
            stream.readFully(salt);

            byte[] iv = new byte[IV_SIZE];
            stream.readFully(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            return new EncryptedState(cipher, salt, iv, IOUtils.readAll(stream));
        }
    }

    static class LegacyEncryptedState extends State {
        private static final int ITERATIONS = 64000;
        private static final int KEY_SIZE = 32 * Byte.SIZE;
        private static final int SALT_SIZE = 20;

        private final Cipher _cipher;
        private final byte[] _salt;
        private final byte[] _iv;
        private final byte[] _data;

        public LegacyEncryptedState(Cipher cipher, byte[] salt, byte[] iv, byte[] data) {
            super(true);
            _cipher = cipher;
            _salt = salt;
            _iv = iv;
            _data = data;
        }

        @Override
        public JsonState decrypt(char[] password) throws DatabaseImporterException {
            PBKDF.Params params = getKeyDerivationParams(password);
            SecretKey key = PBKDF.deriveKey(params);
            return decrypt(key);
        }

        public JsonState decrypt(SecretKey key) throws DatabaseImporterException {
            try {
                _cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(_iv));
                byte[] decrypted = _cipher.doFinal(_data);
                return new JsonState(new JSONObject(new String(decrypted, StandardCharsets.UTF_8)));
            } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException
                     | JSONException | InvalidKeyException | BadPaddingException e) {
                throw new DatabaseImporterException(e);
            }
        }

        private PBKDF.Params getKeyDerivationParams(char[] password) {
            return new PBKDF.Params("PBKDF2WithHmacSHA1", KEY_SIZE, password, _salt, ITERATIONS);
        }

        private static LegacyEncryptedState parseHeader(DataInputStream stream)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException {
            byte[] salt = new byte[SALT_SIZE];
            stream.readFully(salt);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            int ivSize = cipher.getBlockSize();
            byte[] iv = new byte[ivSize];
            stream.readFully(iv);
            return new LegacyEncryptedState(cipher, salt, iv, IOUtils.readAll(stream));
        }
    }

    private static class JsonState extends State {
        private final JSONObject _obj;

        public JsonState(JSONObject obj) {
            super(false);
            _obj = obj;
        }

        @Override
        public Result convert() throws DatabaseImporterException {
            Result res = new Result();

            try {
                JSONArray array = _obj.getJSONArray("Authenticators");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    try {
                        res.addEntry(convertEntry(obj));
                    } catch (DatabaseImporterEntryException e) {
                        res.addError(e);
                    }
                }
            } catch (JSONException e) {
                throw new DatabaseImporterException(e);
            }

            return res;
        }

        private static VaultEntry convertEntry(JSONObject obj) throws DatabaseImporterEntryException {
            try {
                int type = obj.getInt("Type");
                String issuer = obj.getString("Issuer");
                Object nullableUsername = obj.get("Username");
                String username = nullableUsername == JSONObject.NULL ? "" : nullableUsername.toString();
                byte[] secret = Base32.decode(obj.getString("Secret"));
                Algorithm algo = Algorithm.values()[obj.getInt("Algorithm")];
                int digits = obj.getInt("Digits");
                int period = obj.getInt("Period");
                int counter = obj.getInt("Counter");

                OtpInfo info = parseOtpInfo(type, secret, algo, digits, period, counter);
                return new VaultEntry(info, username, issuer);
            } catch (OtpInfoException | EncodingException | JSONException e) {
                throw new DatabaseImporterEntryException(e, null);
            }
        }
    }

    private static class SqlState extends State {
        private final List<SqlEntry> _entries;

        public SqlState(List<SqlEntry> entries) {
            super(false);
            _entries = entries;
        }

        @Override
        public Result convert() throws DatabaseImporterException {
            Result res = new Result();

            for (SqlEntry entry : _entries) {
                try {
                    res.addEntry(entry.convert());
                } catch (DatabaseImporterEntryException e) {
                    res.addError(e);
                }
            }

            return res;
        }
    }

    private static class SqlEntry extends SqlImporterHelper.Entry {
        private final int _type;
        private final String _issuer;
        private final String _username;
        private final String _secret;
        private final Algorithm _algo;
        private final int _digits;
        private final int _period;
        private final int _counter;

        public SqlEntry(SqlImporterHelper.Row row) {
            super(row);
            _type = SqlImporterHelper.getInt(row, "type");
            _issuer = SqlImporterHelper.getString(row, "issuer");
            _username = SqlImporterHelper.getString(row, "username");
            _secret = SqlImporterHelper.getString(row, "secret");
            _algo = Algorithm.values()[SqlImporterHelper.getInt(row, "algorithm")];
            _digits = SqlImporterHelper.getInt(row, "digits");
            _period = SqlImporterHelper.getInt(row, "period");
            _counter = SqlImporterHelper.getInt(row, "counter");
        }

        public VaultEntry convert() throws DatabaseImporterEntryException {
            try {
                byte[] secret = Base32.decode(_secret);
                OtpInfo info = parseOtpInfo(_type, secret, _algo, _digits, _period, _counter);
                return new VaultEntry(info, _username, _issuer);
            } catch (EncodingException | OtpInfoException e) {
                throw new DatabaseImporterEntryException(e, null);
            }
        }
    }
}
