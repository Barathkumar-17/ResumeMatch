package com.resumematch.exception;

/**
 * Thrown when a job id from the URL or a form does not exist in the database.
 *
 * <p>Demonstrates: custom checked exception, inheritance from an abstract base class.</p>
 */
public class InvalidJobException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "This job no longer exists.";

    public InvalidJobException() {
        super(MESSAGE);
    }
}
