package com.resumematch.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes passwords with SHA-256 and returns the 64-character lowercase hex string that is
 * stored in {@code users.password}. The plain password is never stored.
 *
 * <p>Demonstrates: {@code java.security.MessageDigest}, byte-to-hex conversion with
 * {@code StringBuilder}, static utility methods.</p>
 */
public final class PasswordUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PasswordUtil() {
        // utility class, no instances
    }

    /**
     * Returns the SHA-256 hash of the password as lowercase hex.
     * For example {@code hash("demo1234")} gives
     * {@code 0ead2060b65992dca4769af601a1b3a35ef38cfad2c2c465bb160ea764157c5d}.
     */
    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to provide SHA-256, so this cannot happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** True when the plain password hashes to the stored hash. */
    public static boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(password).getBytes(StandardCharsets.UTF_8),
                storedHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
