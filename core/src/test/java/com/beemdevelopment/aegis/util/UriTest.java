package com.beemdevelopment.aegis.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The expected values here are what AOSP produces. Changing one breaks otpauth:// interop. */
public class UriTest {
    @Test
    public void testParseOtpauth() {
        Uri uri = Uri.parse("otpauth://totp/Issuer%3AAccount?secret=ABCD&issuer=Issuer&digits=6");
        assertEquals("otpauth", uri.getScheme());
        assertEquals("totp", uri.getHost());
        assertEquals("/Issuer:Account", uri.getPath());
        assertEquals("ABCD", uri.getQueryParameter("secret"));
        assertEquals("Issuer", uri.getQueryParameter("issuer"));
        assertEquals("6", uri.getQueryParameter("digits"));
        assertNull(uri.getQueryParameter("period"));
    }

    @Test
    public void testParseSchemeOnly() {
        // motp URIs have no authority; the path starts straight after the scheme separator.
        Uri uri = Uri.parse("motp:/Bob?secret=0123456789abcdef");
        assertEquals("motp", uri.getScheme());
        assertNull(uri.getHost());
        assertEquals("/Bob", uri.getPath());
        assertEquals("0123456789abcdef", uri.getQueryParameter("secret"));
    }

    @Test
    public void testMissingComponents() {
        Uri uri = Uri.parse("not a uri at all");
        assertNull(uri.getScheme());
        assertNull(uri.getHost());
        assertNull(uri.getQueryParameter("secret"));
    }

    @Test
    public void testQueryParameterDecoding() {
        Uri uri = Uri.parse("otpauth://totp/x?a=one+two&b=%C3%A9&c=&d=%2B");
        assertEquals("one two", uri.getQueryParameter("a"));
        assertEquals("é", uri.getQueryParameter("b"));
        assertEquals("", uri.getQueryParameter("c"));
        assertEquals("+", uri.getQueryParameter("d"));
    }

    @Test
    public void testQueryParameterPrefixIsNotAMatch() {
        Uri uri = Uri.parse("otpauth://totp/x?secretive=no&secret=yes");
        assertEquals("yes", uri.getQueryParameter("secret"));
    }

    @Test
    public void testMalformedEscapeDoesNotThrow() {
        Uri uri = Uri.parse("otpauth://totp/x?a=%zz");
        assertEquals("�", uri.getQueryParameter("a"));
    }

    @Test
    public void testDecodeDoesNotConvertPlus() {
        assertEquals("a+b", Uri.decode("a+b"));
        assertEquals("a b", Uri.decode("a%20b"));
    }

    @Test
    public void testEncodeAllowedCharacters() {
        assertEquals("_-!.~'()*", Uri.encode("_-!.~'()*", null));
        assertEquals("%20", Uri.encode(" ", null));
        assertEquals("%3A", Uri.encode(":", null));
        assertEquals("a%2Fb", Uri.encode("a/b", null));
        assertEquals("a/b", Uri.encode("a/b", "/"));
        assertEquals("%C3%A9", Uri.encode("é", null));
    }

    @Test
    public void testBuildOtpauthUri() {
        Uri uri = new Uri.Builder()
                .scheme("otpauth")
                .authority("totp")
                .appendQueryParameter("period", "30")
                .appendQueryParameter("digits", "6")
                .appendQueryParameter("algorithm", "SHA1")
                .appendQueryParameter("secret", "JBSWY3DPEHPK3PXP")
                .path("Example:alice@example.com")
                .appendQueryParameter("issuer", "Example")
                .build();

        assertEquals("otpauth://totp/Example%3Aalice%40example.com"
                + "?period=30&digits=6&algorithm=SHA1&secret=JBSWY3DPEHPK3PXP&issuer=Example",
                uri.toString());
    }

    @Test
    public void testBuildMakesPathAbsolute() {
        Uri uri = new Uri.Builder()
                .scheme("motp")
                .appendQueryParameter("secret", "abcdef")
                .path("Bob")
                .build();
        assertEquals("motp:/Bob?secret=abcdef", uri.toString());
    }

    @Test
    public void testRoundTrip() {
        String s = "otpauth://totp/ACME%20Co%3Ajohn.doe%40email.com"
                + "?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30";
        Uri uri = Uri.parse(s);
        assertEquals("/ACME Co:john.doe@email.com", uri.getPath());
        assertEquals("ACME Co", uri.getQueryParameter("issuer"));
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", uri.getQueryParameter("secret"));
        assertEquals(s, uri.toString());
    }
}
