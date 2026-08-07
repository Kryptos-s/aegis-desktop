package com.beemdevelopment.aegis.vault;

import com.beemdevelopment.aegis.util.TempFiles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Owns the vault file on disk. Writes go to a mode 0600 temporary file in the same directory, are
 * fsynced and then moved into place, so an interrupted write cannot truncate an encrypted vault.
 * Replaces {@code androidx.core.util.AtomicFile}, which does not fsync.
 */
public class VaultStore {
    private final Path _path;

    public VaultStore(Path path) {
        _path = path.toAbsolutePath();
    }

    public Path getPath() {
        return _path;
    }

    public boolean exists() {
        return Files.isRegularFile(_path);
    }

    public byte[] read() throws IOException {
        return Files.readAllBytes(_path);
    }

    public void write(byte[] data) throws IOException {
        Path dir = _path.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
            TempFiles.restrictToOwner(dir);
        }

        Path temp = Files.createTempFile(dir, ".aegis-", ".tmp");
        TempFiles.restrictToOwner(temp);

        try {
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                out.write(data);
                out.flush();
            }

            // Force the contents out before the rename, or a crash in between leaves the vault
            // pointing at a file whose data never reached the disk.
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            try {
                Files.move(temp, _path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, _path, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;

            syncDirectory(dir);
        } finally {
            if (temp != null) {
                TempFiles.shred(temp);
            }
        }
    }

    public void delete() throws IOException {
        Files.deleteIfExists(_path);
    }

    /** Flushes the directory entry so the rename itself survives a power loss. */
    private static void syncDirectory(Path dir) {
        if (dir == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Not available on every platform or filesystem, Windows included.
        }
    }
}
