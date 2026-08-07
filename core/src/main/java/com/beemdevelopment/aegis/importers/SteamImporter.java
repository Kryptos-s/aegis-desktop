package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.SteamInfo;
import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.vault.VaultEntry;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SteamImporter extends DatabaseImporter {
    @Override
    public State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        try {
            byte[] bytes = IOUtils.readAll(stream);
            JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8));

            List<JSONObject> objs = new ArrayList<>();
            if (obj.has("accounts")) {
                JSONObject accounts = obj.getJSONObject("accounts");
                Iterator<String> keys = accounts.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    objs.add(accounts.getJSONObject(key));
                }
            } else {
                objs.add(obj);
            }
            return new State(objs);
        } catch (IOException | JSONException e) {
            throw new DatabaseImporterException(e);
        }
    }

    public static class State extends DatabaseImporter.State {
        private final List<JSONObject> _objs;

        private State(List<JSONObject> objs) {
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
                byte[] secret = Base64.decode(obj.getString("shared_secret"));
                SteamInfo info = new SteamInfo(secret);

                String account = obj.getString("account_name");
                return new VaultEntry(info, account, "Steam");
            } catch (JSONException | EncodingException | OtpInfoException e) {
                throw new DatabaseImporterEntryException(e, obj.toString());
            }
        }
    }
}
