package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.util.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FreeOtpPlusImporter extends DatabaseImporter {
    @Override
    public State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        State state;

        if (isInternal) {
            state = new FreeOtpImporter().read(stream);
        } else {
            try {
                String json = new String(IOUtils.readAll(stream), StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(json);
                JSONArray array = obj.getJSONArray("tokens");

                List<JSONObject> entries = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    entries.add(array.getJSONObject(i));
                }

                state = new FreeOtpImporter.DecryptedStateV1(entries);
            } catch (IOException | JSONException e) {
                throw new DatabaseImporterException(e);
            }
        }

        return state;
    }
}
