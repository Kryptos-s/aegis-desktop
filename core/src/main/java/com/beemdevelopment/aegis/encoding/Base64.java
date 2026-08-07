package com.beemdevelopment.aegis.encoding;

import java.nio.charset.StandardCharsets;

/** RFC 4648 base64. Decoding tolerates missing padding, like Guava's {@code base64()}; encoding pads. */
public class Base64 {
    private static final java.util.Base64.Decoder DECODER = java.util.Base64.getDecoder();
    private static final java.util.Base64.Encoder ENCODER = java.util.Base64.getEncoder();

    private Base64() {

    }

    public static byte[] decode(String s) throws EncodingException {
        try {
            return DECODER.decode(s);
        } catch (IllegalArgumentException e) {
            throw new EncodingException(e);
        }
    }

    public static byte[] decode(byte[] s) throws EncodingException {
        return decode(new String(s, StandardCharsets.UTF_8));
    }

    public static String encode(byte[] data) {
        return ENCODER.encodeToString(data);
    }
}
