package com.beemdevelopment.aegis.encoding;

import java.util.HexFormat;

/** Base16. Decoding is case-insensitive; encoding produces lowercase, as vault files expect. */
public class Hex {
    private static final HexFormat FORMAT = HexFormat.of();

    private Hex() {

    }

    public static byte[] decode(String s) throws EncodingException {
        try {
            return FORMAT.parseHex(s);
        } catch (IllegalArgumentException e) {
            throw new EncodingException(e);
        }
    }

    public static String encode(byte[] data) {
        return FORMAT.formatHex(data);
    }
}
