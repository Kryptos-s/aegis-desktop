package com.beemdevelopment.aegis.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/** Desktop counterpart of {@code helpers.QrCodeHelper}, using AWT images instead of bitmaps. */
public final class QrCodes {
    public static final int BLACK = 0xff000000;
    public static final int WHITE = 0xffffffff;

    private QrCodes() {

    }

    public static Result decodeFromSource(LuminanceSource source) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.ALSO_INVERTED, true);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        return reader.decode(bitmap, hints);
    }

    /**
     * Decodes one camera frame from a raw 8-bit luminance plane, or returns null if it holds no
     * readable QR code. Capture buffers pad their rows, so {@code rowStride} can exceed the width.
     */
    public static Result decodeFromLuminance(byte[] data, int width, int height, int rowStride) {
        // The last row is usually not padded out, so the buffer can be shorter than rowStride * height.
        if (rowStride < width || height < 1
                || (long) rowStride * (height - 1) + width > data.length) {
            return null;
        }

        byte[] plane = data;
        if (rowStride != width) {
            plane = new byte[width * height];
            for (int y = 0; y < height; y++) {
                System.arraycopy(data, y * rowStride, plane, y * width, width);
            }
        }

        LuminanceSource source = new PlanarYUVLuminanceSource(
                plane, width, height, 0, 0, width, height, false);
        try {
            return decodeFromSource(source);
        } catch (NotFoundException e) {
            return null;
        }
    }

    public static Result decodeFromImage(BufferedImage image) throws DecodeError {
        // Retry on progressively smaller copies, like the Android app does. High resolution
        // screenshots often only decode once scaled down.
        BufferedImage current = image;
        for (int i = 0; i <= 2; i++) {
            if (i != 0) {
                int width = Math.max(1, image.getWidth() / (i * 2));
                int height = Math.max(1, image.getHeight() / (i * 2));
                current = resize(image, width, height);
            }

            try {
                return decodeFromSource(toLuminanceSource(current));
            } catch (NotFoundException ignored) {
            }
        }

        throw new DecodeError(NotFoundException.getNotFoundInstance());
    }

    public static Result decodeFromStream(InputStream inStream) throws DecodeError {
        BufferedImage image;
        try {
            image = ImageIO.read(inStream);
        } catch (IOException e) {
            throw new DecodeError(e);
        }

        if (image == null) {
            throw new DecodeError("Unable to decode stream to an image");
        }

        return decodeFromImage(image);
    }

    private static LuminanceSource toLuminanceSource(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        return new RGBLuminanceSource(width, height, pixels);
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    public static BufferedImage encodeToImage(String data, int width, int height, int backgroundColor)
            throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, width, height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? BLACK : backgroundColor);
            }
        }

        return image;
    }

    public static byte[] encodeToPng(String data, int width, int height, int backgroundColor)
            throws WriterException, IOException {
        BufferedImage image = encodeToImage(data, width, height, backgroundColor);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("No PNG writer available");
            }
            return out.toByteArray();
        }
    }

    public static class DecodeError extends Exception {
        public DecodeError(String message) {
            super(message);
        }

        public DecodeError(Throwable cause) {
            super(cause);
        }
    }
}
