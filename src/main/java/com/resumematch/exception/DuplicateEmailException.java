package com.resumematch.exception;

/**
 * Thrown when someone registers with an email that already has an account. Raised by
 * catching {@code SQLIntegrityConstraintViolationException} from the UNIQUE index on
 * {@code users.email}.
 *
 * <p>Demonstrates: custom checked exception, exception chaining (wraps the SQL cause).</p>
 */
public class DuplicateEmailException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "An account with this email already exists. Log in instead.";

    public DuplicateEmailException() {
        super(MESSAGE);
    }

    public DuplicateEmailException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
