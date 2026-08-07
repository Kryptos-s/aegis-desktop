package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.util.UUIDMap;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultGroup;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads a vault exported by another authenticator app and converts it into Aegis entries. The
 * Android app's root-assisted extraction has no desktop equivalent, but {@code isInternal} remains:
 * a user who obtains an app's internal database themselves can still import it.
 */
public abstract class DatabaseImporter {
    private static final List<Definition> _importers;

    static {
        // note: keep these lists sorted alphabetically
        List<Definition> importers = new ArrayList<>();
        importers.add(new Definition("2FAS Authenticator", TwoFASImporter.class, "importer_help_2fas", false));
        importers.add(new Definition("Aegis", AegisImporter.class, "importer_help_aegis", false));
        importers.add(new Definition("andOTP", AndOtpImporter.class, "importer_help_andotp", false));
        importers.add(new Definition("Authenticator Plus", AuthenticatorPlusImporter.class, "importer_help_authenticator_plus", false));
        importers.add(new Definition("Authy", AuthyImporter.class, "importer_help_authy", true));
        importers.add(new Definition("Battle.net Authenticator", BattleNetImporter.class, "importer_help_battle_net_authenticator", true));
        importers.add(new Definition("Bitwarden", BitwardenImporter.class, "importer_help_bitwarden", false));
        importers.add(new Definition("Duo", DuoImporter.class, "importer_help_duo", true));
        importers.add(new Definition("Ente Auth", EnteAuthImporter.class, "importer_help_ente_auth", false));
        importers.add(new Definition("FreeOTP", FreeOtpImporter.class, "importer_help_freeotp", true));
        importers.add(new Definition("FreeOTP+ (JSON)", FreeOtpPlusImporter.class, "importer_help_freeotp_plus", true));
        importers.add(new Definition("Google Authenticator", GoogleAuthImporter.class, "importer_help_google_authenticator", true));
        importers.add(new Definition("Microsoft Authenticator", MicrosoftAuthImporter.class, "importer_help_microsoft_authenticator", true));
        importers.add(new Definition("Plain text", GoogleAuthUriImporter.class, "importer_help_plain_text", false));
        importers.add(new Definition("Proton Authenticator", ProtonAuthenticatorImporter.class, "importer_help_proton_authenticator", false));
        importers.add(new Definition("Steam", SteamImporter.class, "importer_help_steam", true));
        importers.add(new Definition("Stratum (Authenticator Pro)", StratumImporter.class, "importer_help_stratum", true));
        importers.add(new Definition("TOTP Authenticator", TotpAuthenticatorImporter.class, "importer_help_totp_authenticator", true));
        importers.add(new Definition("WinAuth", WinAuthImporter.class, "importer_help_winauth", false));
        _importers = Collections.unmodifiableList(importers);
    }

    protected abstract State read(InputStream stream, boolean isInternal) throws DatabaseImporterException;

    public State read(InputStream stream) throws DatabaseImporterException {
        return read(stream, false);
    }

    public static DatabaseImporter create(Class<? extends DatabaseImporter> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException
                | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Definition> getImporters() {
        return _importers;
    }

    /** Returns the importers that can read the source app's internal database, not just its export. */
    public static List<Definition> getInternalImporters() {
        return Collections.unmodifiableList(_importers.stream()
                .filter(Definition::supportsInternal)
                .collect(Collectors.toList()));
    }

    public static class Definition implements Serializable {
        private final String _name;
        private final Class<? extends DatabaseImporter> _type;
        private final String _help;
        private final boolean _supportsInternal;

        public Definition(String name, Class<? extends DatabaseImporter> type, String help, boolean supportsInternal) {
            _name = name;
            _type = type;
            _help = help;
            _supportsInternal = supportsInternal;
        }

        public String getName() {
            return _name;
        }

        public Class<? extends DatabaseImporter> getType() {
            return _type;
        }

        /** Name of the string resource explaining which file this importer needs. */
        public String getHelp() {
            return _help;
        }

        public boolean supportsInternal() {
            return _supportsInternal;
        }
    }

    public static abstract class State {
        private final boolean _encrypted;

        public State(boolean encrypted) {
            _encrypted = encrypted;
        }

        public boolean isEncrypted() {
            return _encrypted;
        }

        /**
         * Runs an expensive key derivation, so callers must stay off the UI thread. The caller owns
         * the password array and should wipe it afterwards.
         */
        public State decrypt(char[] password) throws DatabaseImporterException {
            if (!_encrypted) {
                throw new IllegalStateException("Attempted to decrypt a plain text database");
            }

            throw new UnsupportedOperationException();
        }

        /**
         * String resource names for the decryption modes this state supports. andOTP and FreeOTP
         * have old and new formats that cannot be told apart from the file, so the user picks.
         */
        public List<String> getDecryptionVariants() {
            return Collections.emptyList();
        }

        public State decrypt(char[] password, int variant) throws DatabaseImporterException {
            if (variant != 0) {
                throw new IllegalArgumentException(String.format("Unknown decryption variant: %d", variant));
            }
            return decrypt(password);
        }

        public Result convert() throws DatabaseImporterException {
            if (_encrypted) {
                throw new IllegalStateException("Attempted to convert database before decrypting it");
            }

            throw new UnsupportedOperationException();
        }
    }

    public static class Result {
        private final UUIDMap<VaultEntry> _entries = new UUIDMap<>();
        private final UUIDMap<VaultGroup> _groups = new UUIDMap<>();
        private final List<DatabaseImporterEntryException> _errors = new ArrayList<>();

        public void addEntry(VaultEntry entry) {
            _entries.add(entry);
        }

        public void addGroup(VaultGroup group) {
            _groups.add(group);
        }

        public void addError(DatabaseImporterEntryException error) {
            _errors.add(error);
        }

        public UUIDMap<VaultEntry> getEntries() {
            return _entries;
        }

        public UUIDMap<VaultGroup> getGroups() {
            return _groups;
        }

        public List<DatabaseImporterEntryException> getErrors() {
            return _errors;
        }
    }
}
