package com.resumematch.exception;

/**
 * Base class for every error this application expects. Its message is always safe to
 * show the user: no stack trace, no SQL, no file system paths. Servlets catch
 * {@code AppException}, put {@link #getUserMessage()} in a request attribute and forward
 * back to the page.
 *
 * <p>Demonstrates: abstract classes, inheritance, checked exceptions, exception chaining.</p>
 */
public abstract class AppException extends Exception {

    private static final long serialVersionUID = 1L;

    protected AppException(String userMessage) {
        super(userMessage);
    }

    /** Keeps the low-level cause for the server log while the user sees only the message. */
    protected AppException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }

    /** The friendly message to show in the browser. */
    public String getUserMessage() {
        return getMessage();
    }
}
