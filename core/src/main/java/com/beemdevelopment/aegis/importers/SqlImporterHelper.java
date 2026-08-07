package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.util.IOUtils;
import com.beemdevelopment.aegis.util.TempFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads rows out of a SQLite database exported by another authenticator app, through the SQLite
 * JDBC driver rather than {@code android.database.sqlite}. The database is always opened read-only
 * from a private temporary copy, which is shredded afterwards.
 */
public class SqlImporterHelper {
    /** Table names are interpolated into the query, so keep them to what a table name can be. */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public <T extends Entry> List<T> read(Class<T> type, InputStream inStream, String table)
            throws DatabaseImporterException {
        Path file = null;
        try {
            file = TempFiles.createPrivateFile("db-import-", ".sqlite");
            try (OutputStream out = Files.newOutputStream(file)) {
                IOUtils.copy(inStream, out);
            }
            return readFile(type, file, table);
        } catch (IOException e) {
            throw new DatabaseImporterException(e);
        } finally {
            if (file != null) {
                TempFiles.shred(file);
            }
        }
    }

    /** Copies the database and any -wal/-shm/-journal sidecars aside, so the original is untouched. */
    public <T extends Entry> List<T> read(Class<T> type, Path path, String table)
            throws DatabaseImporterException {
        Path dir = null;
        try {
            dir = TempFiles.createPrivateDirectory("db-import-");
            Path mainCopy = dir.resolve(path.getFileName().toString());

            Path parent = path.getParent();
            String prefix = path.getFileName().toString();
            if (parent != null) {
                try (var stream = Files.list(parent)) {
                    for (Path sibling : stream.toList()) {
                        String name = sibling.getFileName().toString();
                        if (name.equals(prefix)
                                || name.equals(prefix + "-wal")
                                || name.equals(prefix + "-shm")
                                || name.equals(prefix + "-journal")) {
                            Files.copy(sibling, dir.resolve(name));
                        }
                    }
                }
            } else {
                Files.copy(path, mainCopy);
            }

            if (!Files.exists(mainCopy)) {
                throw new DatabaseImporterException(String.format("File does not exist: %s", path));
            }

            return readFile(type, mainCopy, table);
        } catch (IOException e) {
            throw new DatabaseImporterException(e);
        } finally {
            if (dir != null) {
                TempFiles.shredDirectory(dir);
            }
        }
    }

    private <T extends Entry> List<T> readFile(Class<T> type, Path file, String table)
            throws DatabaseImporterException {
        if (!SAFE_TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException(String.format("Unsafe table name: %s", table));
        }

        String url = String.format("jdbc:sqlite:file:%s?mode=ro&immutable=1", file.toAbsolutePath());

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format("SELECT * FROM %s", table))) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<T> entries = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> values = new LinkedHashMap<>(columnCount * 2);
                for (int i = 1; i <= columnCount; i++) {
                    values.put(meta.getColumnLabel(i), rs.getObject(i));
                }

                try {
                    entries.add(type.getDeclaredConstructor(Row.class).newInstance(new Row(values)));
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof DatabaseImporterException) {
                        throw (DatabaseImporterException) e.getCause();
                    }
                    throw new RuntimeException(e);
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }

            return entries;
        } catch (SQLException e) {
            throw new DatabaseImporterException(e);
        }
    }

    public static String getString(Row row, String columnName) {
        return row.getString(columnName);
    }

    public static String getString(Row row, String columnName, String def) {
        String res = row.getString(columnName);
        return res == null ? def : res;
    }

    public static int getInt(Row row, String columnName) {
        return row.getInt(columnName);
    }

    public static long getLong(Row row, String columnName) {
        return row.getLong(columnName);
    }

    public static byte[] getBlob(Row row, String columnName) {
        return row.getBlob(columnName);
    }

    /** Values are coerced the way {@code android.database.Cursor} does, so importers behave alike. */
    public static final class Row {
        private final Map<String, Object> _values;

        Row(Map<String, Object> values) {
            _values = values;
        }

        private Object require(String columnName) {
            if (!_values.containsKey(columnName)) {
                throw new IllegalArgumentException(String.format("No such column: %s", columnName));
            }
            return _values.get(columnName);
        }

        public boolean has(String columnName) {
            return _values.containsKey(columnName);
        }

        public String getString(String columnName) {
            Object value = require(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof byte[]) {
                return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
            }
            return String.valueOf(value);
        }

        public int getInt(String columnName) {
            return (int) getLong(columnName);
        }

        public long getLong(String columnName) {
            Object value = require(columnName);
            if (value == null) {
                return 0;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public byte[] getBlob(String columnName) {
            Object value = require(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof byte[]) {
                return (byte[]) value;
            }
            return String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static abstract class Entry {
        public Entry(Row row) {

        }
    }
}
