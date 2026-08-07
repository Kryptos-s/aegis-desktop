package com.beemdevelopment.aegis.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.zxing.Result;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/** Same luminance vectors the Android app uses for its CameraX analyzer. */
public class QrCodesTest {
    private static final String _expectedUri = "otpauth://totp/neo4j:Charlotte?secret=B33WS2ALPT34K4BNY24AYROE4M&issuer=neo4j&algorithm=SHA1&digits=6&period=30";

    @Test
    public void testScanQrCode() {
        Result result = scan("qr.y.gz", 1600, 1200, 1600);
        assertNotNull("QR code not found", result);
        assertEquals(_expectedUri, result.getText());
    }

    @Test
    public void testScanStridedQrCode() {
        // Reading a padded buffer as if it were unpadded shears the image beyond recognition.
        assertNull("QR code found", scan("qr.strided.y.gz", 1840, 1380, 1840));

        Result result = scan("qr.strided.y.gz", 1840, 1380, 1856);
        assertNotNull("QR code not found", result);
        assertEquals(_expectedUri, result.getText());
    }

    private Result scan(String fileName, int width, int height, int rowStride) {
        byte[] data;
        try (InputStream inStream = getClass().getResourceAsStream(fileName);
             GZIPInputStream zipStream = new GZIPInputStream(inStream)) {
            data = IOUtils.readAll(zipStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return QrCodes.decodeFromLuminance(data, width, height, rowStride);
    }
}
