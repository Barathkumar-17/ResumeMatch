package com.resumematch.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes passwords with PBKDF2 (HMAC-SHA256) and a random salt. The value stored in
 * {@code users.password} looks like {@code pbkdf2$600000$<salt>$<hash>}, where the salt
 * and hash are Base64. The plain password is never stored. Older accounts that still
 * hold an unsalted SHA-256 hex hash are accepted by {@link #matches} and can be upgraded
 * after login with {@link #needsRehash}.
 *
 * <p>Demonstrates: {@code SecretKeyFactory}, {@code PBEKeySpec}, {@code SecureRandom},
 * {@code Base64}, constant-time comparison with {@code MessageDigest.isEqual}, static
 * utility methods.</p>
 */
public final class PasswordUtil {

    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Fixed salt for {@link #spendVerifyTime}; its value does not matter. */
    private static final byte[] DUMMY_SALT = new byte[SALT_BYTES];

    private PasswordUtil() {
        // utility class, no instances
    }

    /**
     * Returns {@code pbkdf2$600000$<salt>$<hash>} for the password, using a new random
     * 16-byte salt and a 256-bit key.
     */
    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key;
        try {
            key = deriveBits(password, salt, ITERATIONS, KEY_BITS);
        } catch (GeneralSecurityException e) {
            // Every Java runtime is required to provide PBKDF2WithHmacSHA256.
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(key);
    }

    /**
     * True when the plain password matches the stored value. Accepts a PBKDF2 string or
     * a legacy 64-character lowercase hex SHA-256 hash. A null or malformed stored value
     * gives false, never an exception.
     */
    public static boolean matches(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        try {
            if (isLegacy(stored)) {
                byte[] actual = MessageDigest.getInstance("SHA-256")
                        .digest(password.getBytes(StandardCharsets.UTF_8));
                return MessageDigest.isEqual(toHex(actual).getBytes(StandardCharsets.UTF_8),
                        stored.getBytes(StandardCharsets.UTF_8));
            }
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 4 || !PREFIX.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = deriveBits(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Does the same PBKDF2 work as a real check and throws the result away. The login uses
     * it for an unknown email, so a wrong password for a real account and any password for
     * a made-up account take the same time and the response time does not reveal which
     * emails are registered.
     */
    public static void spendVerifyTime(String password) {
        if (password == null) {
            return;
        }
        try {
            deriveBits(password, DUMMY_SALT, ITERATIONS, KEY_BITS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
    }

    /** True when the stored value is a legacy SHA-256 hash or uses fewer iterations than today. */
    public static boolean needsRehash(String stored) {
        if (stored == null) {
            return false;
        }
        if (isLegacy(stored)) {
            return true;
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** True for exactly 64 lowercase hex characters (the old SHA-256 format). */
    private static boolean isLegacy(String stored) {
        if (stored.length() != 64) {
            return false;
        }
        for (int i = 0; i < stored.length(); i++) {
            char c = stored.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static byte[] deriveBits(String password, byte[] salt, int iterations, int bits)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0x0F, 16)).append(Character.forDigit(b & 0x0F, 16));
        }
        return hex.toString();
    }
}
