package com.resumematch.exception;

/**
 * Thrown when MySQL cannot be reached or a query fails. DAOs catch the raw
 * {@code SQLException}, print it to the server console, and rethrow it wrapped in this
 * exception, so the browser only ever sees the friendly message.
 *
 * <p>Demonstrates: custom checked exception, exception chaining (wraps SQLException).</p>
 */
public class DatabaseConnectionException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Can't reach the database right now. Your changes weren't saved.";

    public DatabaseConnectionException() {
        super(MESSAGE);
    }

    public DatabaseConnectionException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
