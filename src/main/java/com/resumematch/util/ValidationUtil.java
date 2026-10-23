package com.resumematch.util;

import com.resumematch.exception.MissingFieldException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Input checks for the register and login forms and for resume uploads. The
 * {@code ...Error} methods return the message to show next to a field, or {@code null}
 * when the value is fine, so a servlet can collect one message per field.
 *
 * <p>Demonstrates: {@code java.util.regex.Pattern}, String handling, static utility
 * methods, throwing a custom exception.</p>
 */
public final class ValidationUtil {

    /** .txt uploads larger than this many bytes (200 KB) are rejected. */
    public static final long MAX_FILE_SIZE_BYTES = 200L * 1024;

    /** .pdf and .docx uploads larger than this many bytes (2 MB) are rejected. */
    public static final long MAX_DOCUMENT_SIZE_BYTES = 2L * 1024 * 1024;

    /** Shortest password accepted at registration. */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /** Longest full name accepted (matches {@code users.name VARCHAR(100)}). */
    public static final int MAX_NAME_LENGTH = 100;

    /** Longest email accepted (matches {@code users.email VARCHAR(150)}). */
    public static final int MAX_EMAIL_LENGTH = 150;

    /** Longest password accepted, so a huge input cannot be used to slow down hashing. */
    public static final int MAX_PASSWORD_LENGTH = 128;

    /** something@something.tld, with a top-level part of at least two characters. */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private ValidationUtil() {
        // utility class, no instances
    }

    /** True when the value is null, empty or only whitespace. */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** True when the trimmed value looks like an email address. */
    public static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL.matcher(email.trim()).matches();
    }

    /** True when the password is at least {@link #MIN_PASSWORD_LENGTH} characters. */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /** True when the file name ends with {@code .txt}, ignoring case. */
    public static boolean isTxtFile(String fileName) {
        return !isBlank(fileName) && fileName.trim().toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    /** True when the size is within the 200 KB limit for .txt uploads. */
    public static boolean isWithinSizeLimit(long sizeInBytes) {
        return sizeInBytes <= MAX_FILE_SIZE_BYTES;
    }

    /** Register form: full name. */
    public static String nameError(String name) {
        if (isBlank(name)) {
            return "Enter your full name.";
        }
        return name.trim().length() > MAX_NAME_LENGTH
                ? "Use at most " + MAX_NAME_LENGTH + " characters." : null;
    }

    /** Register and login forms: email. */
    public static String emailError(String email) {
        if (isBlank(email)) {
            return "Enter your email.";
        }
        if (email.trim().length() > MAX_EMAIL_LENGTH) {
            return "Use at most " + MAX_EMAIL_LENGTH + " characters.";
        }
        return isValidEmail(email) ? null : "Enter a valid email, like name@example.com.";
    }

    /** Register form: new password. */
    public static String newPasswordError(String password) {
        if (password == null || password.isEmpty()) {
            return "Create a password.";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Use at most " + MAX_PASSWORD_LENGTH + " characters.";
        }
        return isValidPassword(password) ? null : "Use at least " + MIN_PASSWORD_LENGTH + " characters.";
    }

    /** Register form: confirm password. */
    public static String confirmPasswordError(String password, String confirm) {
        if (confirm == null || confirm.isEmpty()) {
            return "Re-enter your password.";
        }
        return confirm.equals(password) ? null : "Passwords don't match.";
    }

    /** Login form: password (checked for presence and maximum length). */
    public static String loginPasswordError(String password) {
        if (password == null || password.isEmpty()) {
            return "Enter your password.";
        }
        return password.length() > MAX_PASSWORD_LENGTH
                ? "Use at most " + MAX_PASSWORD_LENGTH + " characters." : null;
    }

    /**
     * Throws when the value is blank, otherwise returns it trimmed.
     *
     * @param value     the submitted value
     * @param fieldName the form field, e.g. "email"
     * @param message   the message to show, e.g. "Enter your email."
     */
    public static String requireField(String value, String fieldName, String message)
            throws MissingFieldException {
        if (isBlank(value)) {
            throw new MissingFieldException(fieldName, message);
        }
        return value.trim();
    }
}
