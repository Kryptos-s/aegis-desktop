package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.crypto.CryptoUtils;
import com.beemdevelopment.aegis.encoding.Base32;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.encoding.Hex;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.util.PreferenceParser;
import com.beemdevelopment.aegis.vault.VaultEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.stream.XMLStreamException;

public class TotpAuthenticatorImporter extends DatabaseImporter {
    // WARNING: DON'T DO THIS IN YOUR OWN CODE
    // this is a hardcoded password and nonce, used solely to decrypt TOTP Authenticator backups
    private static final char[] PASSWORD = "TotpAuthenticator".toCharArray();
    private static final byte[] IV = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private static final String PREF_KEY = "STATIC_TOTP_CODES_LIST";

    @Override
    public State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        try {
            if (isInternal) {
                String data = null;
                for (PreferenceParser.XmlEntry entry : PreferenceParser.parse(stream)) {
                    if (entry.Name.equals(PREF_KEY)) {
                        data = entry.Value;
                    }
                }

                if (data == null) {
                    throw new DatabaseImporterException(String.format("Key %s not found in shared preference file", PREF_KEY));
                }

                List<JSONObject> entries = parse(data);
                return new DecryptedState(entries);
            } else {
                byte[] base64 = IOUtils.readAll(stream);
                byte[] cipherText = Base64.decode(base64);
                return new EncryptedState(cipherText);
            }
        } catch (IOException | XMLStreamException | JSONException e) {
            throw new DatabaseImporterException(e);
        }
    }

    private static List<JSONObject> parse(String data) throws JSONException {
        JSONArray array = new JSONArray(data);

        List<JSONObject> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); ++i) {
            JSONObject obj = array.getJSONObject(i);
            entries.add(obj);
        }

        return entries;
    }

    public static class EncryptedState extends DatabaseImporter.State {
        private byte[] _data;

        public EncryptedState(byte[] data) {
            super(true);
            _data = data;
        }

        @Override
        public DecryptedState decrypt(char[] password) throws DatabaseImporterException {
            try {
                // WARNING: DON'T DO THIS IN YOUR OWN CODE
                // this is not a secure way to derive a key from a password
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                byte[] keyBytes = hash.digest(CryptoUtils.toBytes(password));
                SecretKey key = new SecretKeySpec(keyBytes, "AES");

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                IvParameterSpec spec = new IvParameterSpec(IV);
                cipher.init(Cipher.DECRYPT_MODE, key, spec);

                byte[] bytes = cipher.doFinal(_data);
                JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                JSONArray keys = obj.names();

                List<JSONObject> entries = new ArrayList<>();
                if (keys != null && keys.length() > 0) {
                    entries = parse((String) keys.get(0));
                }

                return new DecryptedState(entries);
            } catch (NoSuchAlgorithmException
                    | NoSuchPaddingException
                    | InvalidAlgorithmParameterException
                    | InvalidKeyException
                    | BadPaddingException
                    | IllegalBlockSizeException
                    | JSONException e) {
                throw new DatabaseImporterException(e);
            }
        }

        // the user has to tell us whether this backup is protected with a password of their own,
        // because one that isn't is encrypted with the hardcoded password at the top of this file
        // and looks exactly the same
        @Override
        public List<String> getDecryptionVariants() {
            return List.of("yes", "no");
        }

        @Override
        public DecryptedState decrypt(char[] password, int variant) throws DatabaseImporterException {
            switch (variant) {
                case 0:
                    return decrypt(password);
                case 1:
                    return decrypt(PASSWORD);
                default:
                    throw new IllegalArgumentException(String.format("Unknown decryption variant: %d", variant));
            }
        }
    }

    public static class DecryptedState extends DatabaseImporter.State {
        private List<JSONObject> _objs;

        private DecryptedState(List<JSONObject> objs) {
            super(false);
            _objs = objs;
        }

        @Override
        public Result convert() {
            Result result = new Result();

            for (JSONObject obj : _objs) {
                try {
                    VaultEntry entry = convertEntry(obj);
                    result.addEntry(entry);
                } catch (DatabaseImporterEntryException e) {
                    result.addError(e);
                }
            }

            return result;
        }

        private static VaultEntry convertEntry(JSONObject obj) throws DatabaseImporterEntryException {
            try {
                int base = obj.getInt("base");
                String secretString = obj.getString("key");

                byte[] secret;
                switch (base) {
                    case 16:
                        secret = Hex.decode(secretString);
                        break;
                    case 32:
                        secret = Base32.decode(secretString);
                        break;
                    case 64:
                        secret = Base64.decode(secretString);
                        break;
                    default:
                        throw new DatabaseImporterEntryException(String.format("Unsupported secret encoding: base %d", base), obj.toString());
                }

                TotpInfo info = new TotpInfo(secret);
                String name = obj.optString("name");
                String issuer = obj.optString("issuer");

                return new VaultEntry(info, name, issuer);
            } catch (JSONException | OtpInfoException | EncodingException e) {
                throw new DatabaseImporterEntryException(e, obj.toString());
            }
        }
    }
}
