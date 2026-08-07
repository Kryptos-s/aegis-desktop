package com.beemdevelopment.aegis.importers;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.jspecify.annotations.NonNull;

import com.beemdevelopment.aegis.encoding.Base32;
import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.otp.HotpInfo;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.vault.VaultEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public class DuoImporter extends DatabaseImporter {
    @Override
    protected @NonNull State read(
            @NonNull InputStream stream, boolean isInternal
    ) throws DatabaseImporterException {
        try {
            String contents = new String(IOUtils.readAll(stream), UTF_8);
            return new DecryptedState(new JSONArray(contents));
        } catch (JSONException | IOException e) {
            throw new DatabaseImporterException(e);
        }
    }

    public static class DecryptedState extends DatabaseImporter.State {
        private final JSONArray _array;

        public DecryptedState(@NonNull JSONArray array) {
            super(false);
            _array = array;
        }

        @Override
        public @NonNull Result convert() throws DatabaseImporterException {
            Result result = new Result();

            try {
                for (int i = 0; i < _array.length(); i++) {
                    JSONObject entry = _array.getJSONObject(i);
                    try {
                        result.addEntry(convertEntry(entry));
                    } catch (DatabaseImporterEntryException e) {
                        result.addError(e);
                    }
                }
            } catch (JSONException e) {
                throw new DatabaseImporterException(e);
            }

            return result;
        }

        private static @NonNull VaultEntry convertEntry(
                @NonNull JSONObject entry
        ) throws DatabaseImporterEntryException {
            try {
                String label = entry.optString("name");
                JSONObject otpData = entry.getJSONObject("otpGenerator");
                byte[] secret = Base32.decode(otpData.getString("otpSecret"));
                Long counter = otpData.has("counter") ? otpData.getLong("counter") : null;

                OtpInfo otp = counter == null
                        ? new TotpInfo(secret)
                        : new HotpInfo(secret, counter);

                return new VaultEntry(otp, label, "");
            } catch (JSONException | OtpInfoException | EncodingException e) {
                throw new DatabaseImporterEntryException(e, entry.toString());
            }
        }
    }
}
