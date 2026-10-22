package com.resumematch.exception;

/**
 * Thrown when a required form field is blank or invalid. Carries the name of the field
 * so the page can show the message next to it, e.g. field "email", "Enter your email."
 *
 * <p>Demonstrates: custom checked exception carrying extra state.</p>
 */
public class MissingFieldException extends AppException {

    private static final long serialVersionUID = 1L;

    private final String fieldName;

    public MissingFieldException(String userMessage) {
        this(null, userMessage);
    }

    public MissingFieldException(String fieldName, String userMessage) {
        super(userMessage);
        this.fieldName = fieldName;
    }

    /** The form field this message belongs to, or {@code null} if not tied to one. */
    public String getFieldName() {
        return fieldName;
    }
}
