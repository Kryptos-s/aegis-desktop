package com.beemdevelopment.aegis.util;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Replacement for {@code android.net.Uri}, limited to the subset Aegis uses. It reproduces the
 * behaviour of AOSP's {@code Uri.StringUri}, {@code Uri.Builder} and {@code UriCodec}, quirks
 * included: otpauth:// URIs that encode or decode differently here stop round-tripping with the
 * Android app. {@code java.net.URI} is stricter, encodes a different character set and does not
 * decode query parameters, so it is not usable as a substitute.
 */
public final class Uri implements Serializable, Comparable<Uri> {
    private static final long serialVersionUID = 1L;

    private static final int NOT_FOUND = -1;

    /** Characters AOSP's {@code Uri.encode} never percent-encodes, on top of the alphanumerics. */
    private static final String ALWAYS_ALLOWED = "_-!.~'()*";

    private final String _uri;

    private Uri(String uri) {
        _uri = uri;
    }

    /** Parses without validating, like Android: components are resolved lazily and may come back null. */
    public static Uri parse(String uriString) {
        if (uriString == null) {
            throw new NullPointerException("uriString");
        }
        return new Uri(uriString);
    }

    private int findSchemeSeparator() {
        return _uri.indexOf(':');
    }

    private int findFragmentSeparator() {
        int ssi = findSchemeSeparator();
        return _uri.indexOf('#', ssi + 1);
    }

    public String getScheme() {
        int ssi = findSchemeSeparator();
        return ssi == NOT_FOUND ? null : _uri.substring(0, ssi);
    }

    public String getEncodedAuthority() {
        int ssi = findSchemeSeparator();
        int length = _uri.length();

        if (length <= ssi + 2 || _uri.charAt(ssi + 1) != '/' || _uri.charAt(ssi + 2) != '/') {
            return null;
        }

        int end = ssi + 3;
        while (end < length) {
            char c = _uri.charAt(end);
            // AOSP treats a backslash as a path separator, so it ends the authority too.
            if (c == '/' || c == '\\' || c == '?' || c == '#') {
                break;
            }
            end++;
        }

        return _uri.substring(ssi + 3, end);
    }

    public String getAuthority() {
        return decode(getEncodedAuthority());
    }

    public String getHost() {
        String authority = getEncodedAuthority();
        if (authority == null) {
            return null;
        }

        int userInfoSeparator = authority.lastIndexOf('@');
        int portSeparator = findPortSeparator(authority);
        String encodedHost = authority.substring(userInfoSeparator + 1, portSeparator);
        return decode(encodedHost);
    }

    private static int findPortSeparator(String authority) {
        // A ':' inside an IPv6 literal ("[::1]") or inside the userinfo is not a port separator.
        int start = Math.max(authority.lastIndexOf(']'), authority.lastIndexOf('@') + 1);
        int index = authority.indexOf(':', start);
        return index == NOT_FOUND ? authority.length() : index;
    }

    public String getEncodedPath() {
        int ssi = findSchemeSeparator();
        int length = _uri.length();

        int pathStart;
        if (length > ssi + 2 && _uri.charAt(ssi + 1) == '/' && _uri.charAt(ssi + 2) == '/') {
            pathStart = ssi + 3;
            while (pathStart < length) {
                char c = _uri.charAt(pathStart);
                if (c == '?' || c == '#') {
                    return "";
                }
                if (c == '/' || c == '\\') {
                    break;
                }
                pathStart++;
            }
        } else {
            pathStart = ssi + 1;
        }

        int pathEnd = pathStart;
        while (pathEnd < length) {
            char c = _uri.charAt(pathEnd);
            if (c == '?' || c == '#') {
                break;
            }
            pathEnd++;
        }

        return _uri.substring(pathStart, pathEnd);
    }

    public String getPath() {
        return decode(getEncodedPath());
    }

    public String getEncodedQuery() {
        int ssi = findSchemeSeparator();
        int qsi = _uri.indexOf('?', ssi + 1);
        if (qsi == NOT_FOUND) {
            return null;
        }

        int fsi = findFragmentSeparator();
        if (fsi == NOT_FOUND) {
            return _uri.substring(qsi + 1);
        }
        if (fsi < qsi) {
            return null;
        }

        return _uri.substring(qsi + 1, fsi);
    }

    /** Returns the first value for the given key, decoded, or null if absent. '+' decodes to a space. */
    public String getQueryParameter(String key) {
        if (key == null) {
            throw new NullPointerException("key");
        }

        String query = getEncodedQuery();
        if (query == null) {
            return null;
        }

        String encodedKey = encode(key, null);
        int length = query.length();
        int start = 0;

        while (true) {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != NOT_FOUND ? nextAmpersand : length;

            int separator = query.indexOf('=', start);
            if (separator > end || separator == NOT_FOUND) {
                separator = end;
            }

            if (separator - start == encodedKey.length()
                    && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) {
                    return "";
                }
                return decode(query.substring(separator + 1, end), true, false);
            }

            if (nextAmpersand == NOT_FOUND) {
                break;
            }
            start = nextAmpersand + 1;
        }

        return null;
    }

    @Override
    public String toString() {
        return _uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Uri)) {
            return false;
        }
        return _uri.equals(((Uri) o)._uri);
    }

    @Override
    public int hashCode() {
        return _uri.hashCode();
    }

    @Override
    public int compareTo(Uri other) {
        return _uri.compareTo(other._uri);
    }

    /** Percent-encodes everything outside [A-Za-z0-9], {@code _-!.~'()*} and {@code allow}. */
    public static String encode(String s, String allow) {
        if (s == null) {
            return null;
        }

        StringBuilder encoded = null;
        int oldLength = s.length();
        int current = 0;

        while (current < oldLength) {
            int nextToEncode = current;
            while (nextToEncode < oldLength && isAllowed(s.charAt(nextToEncode), allow)) {
                nextToEncode++;
            }

            if (nextToEncode == oldLength && encoded == null) {
                return s;
            }

            if (encoded == null) {
                encoded = new StringBuilder(oldLength + 16);
            }
            if (nextToEncode > current) {
                encoded.append(s, current, nextToEncode);
            }
            current = nextToEncode;
            if (current == oldLength) {
                break;
            }

            // Encode a whole run at once, so that multi-byte sequences and surrogate pairs survive.
            int nextAllowed = current;
            while (nextAllowed < oldLength && !isAllowed(s.charAt(nextAllowed), allow)) {
                nextAllowed++;
            }

            byte[] bytes = s.substring(current, nextAllowed).getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
                encoded.append('%');
                encoded.append(toHexDigit((b & 0xf0) >> 4));
                encoded.append(toHexDigit(b & 0xf));
            }
            current = nextAllowed;
        }

        return encoded == null ? s : encoded.toString();
    }

    public static String encode(String s) {
        return encode(s, null);
    }

    private static boolean isAllowed(char c, String allow) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || ALWAYS_ALLOWED.indexOf(c) != NOT_FOUND
                || (allow != null && allow.indexOf(c) != NOT_FOUND);
    }

    private static char toHexDigit(int value) {
        return (char) (value < 10 ? '0' + value : 'A' + value - 10);
    }

    /** Decodes percent escapes, leaving '+' alone. Malformed escapes become U+FFFD, as on Android. */
    public static String decode(String s) {
        return decode(s, false, false);
    }

    public static String decode(String s, boolean convertPlus, boolean throwOnFailure) {
        if (s == null) {
            return null;
        }
        if (s.indexOf('%') == NOT_FOUND && (!convertPlus || s.indexOf('+') == NOT_FOUND)) {
            return s;
        }

        StringBuilder out = new StringBuilder(s.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        int length = s.length();

        while (i < length) {
            char c = s.charAt(i);
            if (c == '%') {
                // Consume the whole run first: one UTF-8 character can span several escapes.
                boolean malformed = false;
                while (i < length && s.charAt(i) == '%') {
                    if (i + 2 >= length) {
                        malformed = true;
                        i = length;
                        break;
                    }
                    int hi = hexToInt(s.charAt(i + 1));
                    int lo = hexToInt(s.charAt(i + 2));
                    if (hi == NOT_FOUND || lo == NOT_FOUND) {
                        malformed = true;
                        i += 3;
                        break;
                    }
                    bytes.write((hi << 4) | lo);
                    i += 3;
                }

                if (bytes.size() > 0) {
                    out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    bytes.reset();
                }
                if (malformed) {
                    if (throwOnFailure) {
                        throw new IllegalArgumentException("Invalid percent escape in: " + s);
                    }
                    out.append('�');
                }
            } else if (convertPlus && c == '+') {
                out.append(' ');
                i++;
            } else {
                out.append(c);
                i++;
            }
        }

        return out.toString();
    }

    private static int hexToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return 10 + (c - 'a');
        }
        if (c >= 'A' && c <= 'F') {
            return 10 + (c - 'A');
        }
        return NOT_FOUND;
    }

    public static final class Builder {
        private String _scheme;
        private String _authority;
        private String _path;
        private StringBuilder _query;
        private String _fragment;

        public Builder scheme(String scheme) {
            _scheme = scheme;
            return this;
        }

        public Builder authority(String authority) {
            _authority = encode(authority, null);
            return this;
        }

        public Builder encodedAuthority(String authority) {
            _authority = authority;
            return this;
        }

        public Builder path(String path) {
            _path = encode(path, "/");
            return this;
        }

        public Builder encodedPath(String path) {
            _path = path;
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            String encoded = encode(key, null) + "=" + (value == null ? "" : encode(value, null));
            if (_query == null) {
                _query = new StringBuilder(encoded);
            } else {
                _query.append('&').append(encoded);
            }
            return this;
        }

        public Builder fragment(String fragment) {
            _fragment = encode(fragment, null);
            return this;
        }

        public Uri build() {
            StringBuilder sb = new StringBuilder();

            if (_scheme != null) {
                sb.append(_scheme).append(':');
            }
            if (_authority != null) {
                // AOSP appends "//" even for an empty authority.
                sb.append("//").append(_authority);
            }

            String path = _path;
            if (path != null && !path.isEmpty()) {
                if ((_scheme != null || _authority != null) && !path.startsWith("/")) {
                    path = "/" + path;
                }
                sb.append(path);
            }

            if (_query != null) {
                sb.append('?').append(_query);
            }
            if (_fragment != null) {
                sb.append('#').append(_fragment);
            }

            return new Uri(sb.toString());
        }

        @Override
        public String toString() {
            return build().toString();
        }
    }
}
