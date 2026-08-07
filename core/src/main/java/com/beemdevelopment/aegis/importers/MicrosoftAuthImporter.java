package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.otp.GoogleAuthInfo;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.vault.VaultEntry;

import java.io.InputStream;
import java.util.List;

public class MicrosoftAuthImporter extends DatabaseImporter {
    private static final int TYPE_TOTP = 0;
    private static final int TYPE_MICROSOFT = 1;

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
                    int type = sqlEntry.getType();
                    if (type == TYPE_TOTP || type == TYPE_MICROSOFT) {
                        VaultEntry entry = convertEntry(sqlEntry);
                        result.addEntry(entry);
                    }
                } catch (DatabaseImporterEntryException e) {
                    result.addError(e);
                }
            }

            return result;
        }

        private static VaultEntry convertEntry(Entry entry) throws DatabaseImporterEntryException {
            try {
                byte[] secret;
                int digits = 6;

                switch (entry.getType()) {
                    case TYPE_TOTP:
                        secret = GoogleAuthInfo.parseSecret(entry.getSecret());
                        break;
                    case TYPE_MICROSOFT:
                        digits = 8;
                        secret = Base64.decode(entry.getSecret());
                        break;
                    default:
                        throw new DatabaseImporterEntryException(String.format("Unsupported OTP type: %d", entry.getType()), entry.toString());
                }

                OtpInfo info = new TotpInfo(secret, OtpInfo.DEFAULT_ALGORITHM, digits, TotpInfo.DEFAULT_PERIOD);
                return new VaultEntry(info, entry.getUserName(), entry.getIssuer());
            } catch (EncodingException | OtpInfoException e) {
                throw new DatabaseImporterEntryException(e, entry.toString());
            }
        }
    }

    private static class Entry extends SqlImporterHelper.Entry {
        private int _type;
        private String _secret;
        private String _issuer;
        private String _userName;

        public Entry(SqlImporterHelper.Row row) {
            super(row);
            _type = SqlImporterHelper.getInt(row, "account_type");
            _secret = SqlImporterHelper.getString(row, "oath_secret_key");
            _issuer = SqlImporterHelper.getString(row, "name");
            _userName = SqlImporterHelper.getString(row, "username");
        }

        public int getType() {
            return _type;
        }

        public String getSecret() {
            return _secret;
        }

        public String getIssuer() {
            return _issuer;
        }

        public String getUserName() {
            return _userName;
        }
    }
}
