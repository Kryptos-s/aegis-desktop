package com.beemdevelopment.aegis.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

/**
 * Owner-readable temporary files for plaintext secrets, overwritten before deletion. Overwriting is
 * best-effort: on a copy-on-write filesystem or on flash the old blocks may still be there.
 */
public final class TempFiles {
    private static final SecureRandom RANDOM = new SecureRandom();

    /** rw------- */
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    /** rwx------ */
    private static final Set<PosixFilePermission> OWNER_ONLY_DIR =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);

    private TempFiles() {

    }

    public static Path createPrivateFile(String prefix, String suffix) throws IOException {
        Path path;
        if (supportsPosixPermissions()) {
            path = Files.createTempFile(prefix, suffix,
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE));
        } else {
            path = Files.createTempFile(prefix, suffix);
            restrictToOwner(path);
        }
        return path;
    }

    public static Path createPrivateDirectory(String prefix) throws IOException {
        Path path;
        if (supportsPosixPermissions()) {
            path = Files.createTempDirectory(prefix,
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIR));
        } else {
            path = Files.createTempDirectory(prefix);
            restrictToOwner(path);
        }
        return path;
    }

    /** Restricts a path to the current user on filesystems without POSIX permissions (Windows). */
    public static void restrictToOwner(Path path) {
        java.io.File file = path.toFile();
        // Clear all access, then grant it back to the owner only.
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        if (Files.isDirectory(path)) {
            file.setExecutable(true, true);
        }
    }

    private static boolean supportsPosixPermissions() {
        return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /** Overwrites the file with random bytes, then deletes it. Never throws. */
    public static void shred(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                Files.deleteIfExists(path);
                return;
            }

            long size = Files.size(path);
            if (size > 0) {
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rws")) {
                    byte[] buf = new byte[(int) Math.min(size, 64 * 1024)];
                    long written = 0;
                    while (written < size) {
                        RANDOM.nextBytes(buf);
                        int len = (int) Math.min(buf.length, size - written);
                        raf.write(buf, 0, len);
                        written += len;
                    }
                    raf.getFD().sync();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Deleting still matters even if overwriting failed.
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    /** Shreds every file in the directory tree, then removes the directories. Never throws. */
    public static void shredDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    shred(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }
}
