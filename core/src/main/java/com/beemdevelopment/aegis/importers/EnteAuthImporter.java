package com.beemdevelopment.aegis.importers;

import com.beemdevelopment.aegis.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class EnteAuthImporter extends DatabaseImporter {
    @Override
    protected State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        try {
            byte[] bytes = IOUtils.readAll(stream);
            GoogleAuthUriImporter importer = new GoogleAuthUriImporter();
            return importer.read(new ByteArrayInputStream(bytes), isInternal);
        } catch (IOException e) {
            throw new DatabaseImporterException(e);
        }
    }
}
