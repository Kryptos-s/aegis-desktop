package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.otp.GoogleAuthInfo;
import com.beemdevelopment.aegis.otp.HotpInfo;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.vault.VaultEntry;

import java.io.InputStream;
import java.util.List;

public class GoogleAuthImporter extends DatabaseImporter {
    private static final int TYPE_TOTP = 0;
    private static final int TYPE_HOTP = 1;

    @Override
    public State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        SqlImporterHelper helper = new SqlImporterHelper();
        List<Entry> entries = helper.read(Entry.class, stream, "accounts");
        return new State(entries);
    }

    public static class State extends DatabaseImporter.State {
        private List<Entry> _entries;

        private State(List<Entry> entries) {
            super(false);
            _entries = entries;
        }

        @Override
        public Result convert() {
            Result result = new Result();

            for (Entry sqlEntry : _entries) {
                try {
                    VaultEntry entry = convertEntry(sqlEntry);
                    result.addEntry(entry);
                } catch (DatabaseImporterEntryException e) {
                    result.addError(e);
                }
            }

            return result;
        }

        private static VaultEntry convertEntry(Entry entry) throws DatabaseImporterEntryException {
            try {
                if (entry.isEncrypted()) {
                    throw new DatabaseImporterException(String.format("Encrypted entry was skipped: %s", entry.getEmail()));
                }
                byte[] secret = GoogleAuthInfo.parseSecret(entry.getSecret());

                OtpInfo info;
                switch (entry.getType()) {
                    case TYPE_TOTP:
                        info = new TotpInfo(secret);
                        break;
                    case TYPE_HOTP:
                        info = new HotpInfo(secret, entry.getCounter());
                        break;
                    default:
                        throw new DatabaseImporterException("unsupported otp type: " + entry.getType());
                }

                String name = entry.getEmail();
                String[] parts = name.split(":");
                if (parts.length == 2) {
                    name = parts[1];
                }

                return new VaultEntry(info, name, entry.getIssuer());
            } catch (EncodingException | OtpInfoException | DatabaseImporterException e) {
                throw new DatabaseImporterEntryException(e, entry.toString());
            }
        }
    }

    private static class Entry extends SqlImporterHelper.Entry {
        private int _type;
        private boolean _isEncrypted;
        private String _secret;
        private String _email;
        private String _issuer;
        private long _counter;

        public Entry(SqlImporterHelper.Row row) {
            super(row);
            _type = SqlImporterHelper.getInt(row, "type");
            _secret = SqlImporterHelper.getString(row, "secret");
            _email = SqlImporterHelper.getString(row, "email", "");
            _issuer = SqlImporterHelper.getString(row, "issuer", "");
            _counter = SqlImporterHelper.getLong(row, "counter");
            _isEncrypted = (row.has("isencrypted") && SqlImporterHelper.getInt(row, "isencrypted") > 0);
        }


        public int getType() {
            return _type;
        }

        public boolean isEncrypted() {
            return _isEncrypted;
        }

        public String getSecret() {
            return _secret;
        }

        public String getEmail() {
            return _email;
        }

        public String getIssuer() {
            return _issuer;
        }

        public long getCounter() {
            return _counter;
        }
    }
}
