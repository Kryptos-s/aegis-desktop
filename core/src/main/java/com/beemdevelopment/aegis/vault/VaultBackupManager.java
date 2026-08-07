package com.beemdevelopment.aegis.vault;

import com.beemdevelopment.aegis.BackupsVersioningStrategy;
import com.beemdevelopment.aegis.util.TempFiles;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Writes copies of the vault to a directory the user chose, and prunes old ones. A backup is a copy
 * of the vault as stored, so an encrypted vault yields an encrypted backup.
 */
public class VaultBackupManager {
    private static final StrictDateFormat _dateFormat =
            new StrictDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH);

    public static final String FILENAME_PREFIX = "aegis-backup";
    public static final String FILENAME_SINGLE = String.format("%s.json", FILENAME_PREFIX);

    private final ExecutorService _executor;
    private final List<Listener> _listeners = new ArrayList<>();

    public VaultBackupManager() {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "aegis-backup");
            t.setDaemon(true);
            return t;
        };
        _executor = Executors.newSingleThreadExecutor(factory);
    }

    public void addListener(Listener listener) {
        _listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        _listeners.remove(listener);
    }

    /** Copies the file into the backup directory on a background thread, then shreds it. */
    public void scheduleBackup(Path tempFile, BackupsVersioningStrategy strategy, Path dir, int versionsToKeep) {
        _executor.execute(() -> {
            try {
                createBackup(tempFile, strategy, dir, versionsToKeep);
                for (Listener listener : _listeners) {
                    listener.onBackupCreated();
                }
            } catch (VaultRepositoryException | VaultBackupPermissionException e) {
                for (Listener listener : _listeners) {
                    listener.onBackupFailed(e);
                }
            }
        });
    }

    /** Runs a backup on the calling thread, for callers that need the result synchronously. */
    public void createBackup(Path tempFile, BackupsVersioningStrategy strategy, @Nullable Path dir, int versionsToKeep)
            throws VaultRepositoryException, VaultBackupPermissionException {
        if (dir == null) {
            throw new VaultRepositoryException("No backup directory configured");
        }
        if (strategy == BackupsVersioningStrategy.SINGLE_BACKUP) {
            createSingleBackup(tempFile, dir);
        } else if (strategy == BackupsVersioningStrategy.MULTIPLE_BACKUPS) {
            createVersionedBackup(tempFile, dir, versionsToKeep);
        } else {
            throw new VaultRepositoryException("Invalid backups versioning strategy");
        }
    }

    private void createSingleBackup(Path tempFile, Path dir)
            throws VaultRepositoryException, VaultBackupPermissionException {
        try {
            requireWritableDirectory(dir);

            Path target = dir.resolve(FILENAME_SINGLE);
            copyInto(tempFile, target);
        } catch (IOException e) {
            throw new VaultRepositoryException(e);
        } finally {
            TempFiles.shred(tempFile);
        }
    }

    private void createVersionedBackup(Path tempFile, Path dir, int versionsToKeep)
            throws VaultRepositoryException, VaultBackupPermissionException {
        FileInfo fileInfo = new FileInfo(FILENAME_PREFIX);

        try {
            requireWritableDirectory(dir);

            Path target = dir.resolve(fileInfo.toString());
            if (Files.exists(target)) {
                throw new VaultRepositoryException("Backup file already exists");
            }

            copyInto(tempFile, target);
        } catch (IOException e) {
            throw new VaultRepositoryException(e);
        } finally {
            TempFiles.shred(tempFile);
        }

        enforceVersioning(dir, versionsToKeep);
    }

    private static void copyInto(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        TempFiles.restrictToOwner(target);
    }

    private static void requireWritableDirectory(Path dir) throws VaultBackupPermissionException {
        if (!Files.isDirectory(dir)) {
            throw new VaultBackupPermissionException(
                    String.format("Backup location is not a directory: %s", dir));
        }
        if (!Files.isWritable(dir)) {
            throw new VaultBackupPermissionException(
                    String.format("Backup location is not writable: %s", dir));
        }
    }

    /** Deletes all but the newest {@code versionsToKeep} backups. Unrecognized files are left alone. */
    public static void enforceVersioning(Path dir, int versionsToKeep) {
        if (versionsToKeep <= 0) {
            return;
        }

        List<BackupFile> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                try {
                    files.add(new BackupFile(path));
                } catch (ParseException ignored) {
                    // Not one of ours.
                }
            }
        } catch (IOException e) {
            return;
        }

        files.sort(new FileComparator());
        if (files.size() > versionsToKeep) {
            for (BackupFile file : files.subList(0, files.size() - versionsToKeep)) {
                try {
                    Files.deleteIfExists(file.getPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static class FileInfo {
        private final String _filename;
        private final String _ext;
        private final Date _date;

        public FileInfo(String filename, String extension, Date date) {
            _filename = filename;
            _ext = extension;
            _date = date;
        }

        public FileInfo(String filename, Date date) {
            this(filename, "json", date);
        }

        public FileInfo(String filename) {
            this(filename, Calendar.getInstance().getTime());
        }

        public FileInfo(String filename, String extension) {
            this(filename, extension, Calendar.getInstance().getTime());
        }

        public static FileInfo parseFilename(String filename) throws ParseException {
            if (filename == null) {
                throw new ParseException("The filename must not be null", 0);
            }

            final String ext = ".json";
            if (!filename.endsWith(ext)) {
                throwBadFormat(filename);
            }
            filename = filename.substring(0, filename.length() - ext.length());

            final String delim = "-";
            String[] parts = filename.split(delim);
            if (parts.length < 3) {
                throwBadFormat(filename);
            }

            filename = String.join(delim, Arrays.copyOf(parts, parts.length - 2));
            if (!filename.equals(FILENAME_PREFIX)) {
                throwBadFormat(filename);
            }

            Date date = _dateFormat.parse(parts[parts.length - 2] + delim + parts[parts.length - 1]);
            if (date == null) {
                throwBadFormat(filename);
            }

            return new FileInfo(filename, date);
        }

        private static void throwBadFormat(String filename) throws ParseException {
            throw new ParseException(String.format("Bad backup filename format: %s", filename), 0);
        }

        public String getFilename() {
            return _filename;
        }

        public String getExtension() {
            return _ext;
        }

        public Date getDate() {
            return _date;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("%s-%s.%s", _filename, _dateFormat.format(_date), _ext);
        }
    }

    private static class BackupFile {
        private final Path _path;
        private final FileInfo _info;

        BackupFile(Path path) throws ParseException {
            _path = path;
            _info = FileInfo.parseFilename(path.getFileName().toString());
        }

        Path getPath() {
            return _path;
        }

        FileInfo getInfo() {
            return _info;
        }
    }

    private static class FileComparator implements Comparator<BackupFile> {
        @Override
        public int compare(BackupFile o1, BackupFile o2) {
            return o1.getInfo().getDate().compareTo(o2.getInfo().getDate());
        }
    }

    public interface Listener {
        void onBackupCreated();

        void onBackupFailed(Exception e);
    }

    // https://stackoverflow.com/a/19503019
    private static class StrictDateFormat extends SimpleDateFormat {
        public StrictDateFormat(String pattern, Locale locale) {
            super(pattern, locale);
            setLenient(false);
        }

        @Override
        public Date parse(@NonNull String text, ParsePosition pos) {
            int posIndex = pos.getIndex();
            Date d = super.parse(text, pos);
            if (!isLenient() && d != null) {
                String format = format(d);
                if (posIndex + format.length() != text.length() ||
                        !text.endsWith(format)) {
                    d = null; // Not exact match
                }
            }
            return d;
        }
    }
}
