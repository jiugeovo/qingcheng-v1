package com.qingcheng.ordering;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** A result contains only a one-way marker, never a bearer token or client-supplied user ID. */
final class AnniversarySessionResult {
    static final String EXTRA_FINGERPRINT = "anniversary.session.fingerprint";

    private AnniversarySessionResult() { }

    static String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (byte value : digest) {
                result.append(alphabet[(value & 255) >>> 4]);
                result.append(alphabet[value & 15]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static boolean matches(String expectedFingerprint, String currentToken) {
        return expectedFingerprint != null && expectedFingerprint.equals(fingerprint(currentToken));
    }
}
