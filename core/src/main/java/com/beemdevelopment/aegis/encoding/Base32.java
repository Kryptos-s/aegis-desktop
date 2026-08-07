package com.beemdevelopment.aegis.encoding;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * RFC 4648 base32, matching Guava's {@code BaseEncoding.base32()} as used by the Android app:
 * decoding accepts input with or without '=' padding, encoding omits it.
 */
public class Base32 {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE_TABLE = new int[128];

    /** Valid values of {@code length % 8} for a base32 string. 1, 3 and 6 cannot occur. */
    private static final boolean[] VALID_REMAINDER = new boolean[8];

    static {
        Arrays.fill(DECODE_TABLE, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            DECODE_TABLE[ALPHABET.charAt(i)] = i;
        }
        // 0 -> 0 bytes, 2 -> 1 byte, 4 -> 2 bytes, 5 -> 3 bytes, 7 -> 4 bytes
        VALID_REMAINDER[0] = true;
        VALID_REMAINDER[2] = true;
        VALID_REMAINDER[4] = true;
        VALID_REMAINDER[5] = true;
        VALID_REMAINDER[7] = true;
    }

    private Base32() {

    }

    public static byte[] decode(String s) throws EncodingException {
        if (s == null) {
            throw new EncodingException(new NullPointerException("s"));
        }

        String upper = s.toUpperCase(Locale.ROOT);

        // Padding is only accepted at the end, and only as many characters as the length calls for.
        int len = upper.length();
        while (len > 0 && upper.charAt(len - 1) == '=') {
            len--;
        }
        if (len != upper.length()) {
            int expectedPadding = (8 - (len % 8)) % 8;
            if (upper.length() != len + expectedPadding) {
                throw new EncodingException(new IllegalArgumentException("Invalid base32 padding"));
            }
        }

        if (!VALID_REMAINDER[len % 8]) {
            throw new EncodingException(new IllegalArgumentException(
                    String.format("Invalid base32 length: %d", len)));
        }

        byte[] out = new byte[len * 5 / 8];
        int outIndex = 0;
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < len; i++) {
            char c = upper.charAt(i);
            int value = c < 128 ? DECODE_TABLE[c] : -1;
            if (value < 0) {
                throw new EncodingException(new IllegalArgumentException(
                        String.format("Invalid base32 character: %c", c)));
            }

            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[outIndex++] = (byte) (buffer >>> bitsLeft);
            }
        }

        // Reject non-canonical input: several strings would otherwise decode to the same bytes.
        if (bitsLeft > 0 && (buffer & ((1 << bitsLeft) - 1)) != 0) {
            throw new EncodingException(new IllegalArgumentException(
                    "Invalid base32: non-zero padding bits"));
        }

        return out;
    }

    public static String encode(byte[] data) {
        if (data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(ALPHABET.charAt((buffer >>> bitsLeft) & 0x1f));
            }
        }

        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }

        return sb.toString();
    }

    public static String encode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return encode(bytes);
    }
}
