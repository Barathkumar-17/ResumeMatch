package com.resumematch.exception;

/**
 * Thrown when the email is unknown or the password does not match. The message is the
 * same in both cases so it does not reveal which emails are registered.
 *
 * <p>Demonstrates: custom checked exception, inheritance from an abstract base class.</p>
 */
public class InvalidLoginException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "The email or password is incorrect.";

    public InvalidLoginException() {
        super(MESSAGE);
    }
}
