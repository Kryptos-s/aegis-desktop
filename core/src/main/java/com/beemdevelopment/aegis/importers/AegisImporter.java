package com.beemdevelopment.aegis.importers;

import org.jspecify.annotations.Nullable;

import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultEntryException;
import com.beemdevelopment.aegis.vault.VaultFile;
import com.beemdevelopment.aegis.vault.VaultFileCredentials;
import com.beemdevelopment.aegis.vault.VaultFileException;
import com.beemdevelopment.aegis.vault.VaultGroup;
import com.beemdevelopment.aegis.vault.slots.PasswordSlot;
import com.beemdevelopment.aegis.vault.slots.PasswordSlotDecrypter;
import com.beemdevelopment.aegis.vault.slots.SlotList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class AegisImporter extends DatabaseImporter {

    @Override
    public State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        try {
            byte[] bytes = IOUtils.readAll(stream);
            VaultFile file = VaultFile.fromBytes(bytes);
            if (file.isEncrypted()) {
                return new EncryptedState(file);
            }
            return new DecryptedState(file.getContent());
        } catch (VaultFileException | IOException e) {
            throw new DatabaseImporterException(e);
        }
    }

    public static class EncryptedState extends State {
        private VaultFile _file;

        private EncryptedState(VaultFile file) {
            super(true);
            _file = file;
        }

        public SlotList getSlots() {
            return _file.getHeader().getSlots();
        }

        public State decrypt(VaultFileCredentials creds) throws DatabaseImporterException {
            JSONObject obj;
            try {
                obj = _file.getContent(creds);
            } catch (VaultFileException e) {
                throw new DatabaseImporterException(e);
            }

            return new DecryptedState(obj, creds);
        }

        @Override
        public State decrypt(char[] password) throws DatabaseImporterException {
            List<PasswordSlot> slots = getSlots().findAll(PasswordSlot.class);
            PasswordSlotDecrypter.Result result = PasswordSlotDecrypter.decrypt(slots, password);
            if (result == null) {
                throw new DatabaseImporterException("Password incorrect");
            }

            VaultFileCredentials creds = new VaultFileCredentials(result.getKey(), getSlots());
            return decrypt(creds);
        }
    }

    public static class DecryptedState extends State {
        private JSONObject _obj;
        private VaultFileCredentials _creds;

        private DecryptedState(JSONObject obj) {
            this(obj, null);
        }

        private DecryptedState(JSONObject obj, VaultFileCredentials creds) {
            super(false);
            _obj = obj;
            _creds = creds;
        }

        @Nullable
        public VaultFileCredentials getCredentials() {
            return _creds;
        }

        @Override
        public Result convert() throws DatabaseImporterException {
            Result result = new Result();

            try {
                if (_obj.has("groups")) {
                    JSONArray groupArray = _obj.getJSONArray("groups");
                    for (int i = 0; i < groupArray.length(); i++) {
                        JSONObject groupObj = groupArray.getJSONObject(i);
                        try {
                            VaultGroup group = convertGroup(groupObj);
                            if (!result.getGroups().has(group)) {
                                result.addGroup(group);
                            }
                        } catch (DatabaseImporterEntryException e) {
                            result.addError(e);
                        }
                    }
                }

                JSONArray entryArray = _obj.getJSONArray("entries");
                for (int i = 0; i < entryArray.length(); i++) {
                    JSONObject entryObj = entryArray.getJSONObject(i);
                    try {
                        VaultEntry entry = convertEntry(entryObj);
                        for (UUID groupUuid : entry.getGroups()) {
                            if (!result.getGroups().has(groupUuid)) {
                                entry.getGroups().remove(groupUuid);
                            }
                        }
                        result.addEntry(entry);
                    } catch (DatabaseImporterEntryException e) {
                        result.addError(e);
                    }
                }
            } catch (JSONException e) {
                throw new DatabaseImporterException(e);
            }

            return result;
        }

        private static VaultEntry convertEntry(JSONObject obj) throws DatabaseImporterEntryException {
            try {
                return VaultEntry.fromJson(obj);
            } catch (VaultEntryException e) {
                throw new DatabaseImporterEntryException(e, obj.toString());
            }
        }

        private static VaultGroup convertGroup(JSONObject obj) throws DatabaseImporterEntryException {
            try {
                return VaultGroup.fromJson(obj);
            } catch (VaultEntryException e) {
                throw new DatabaseImporterEntryException(e, obj.toString());
            }
        }
    }
}
