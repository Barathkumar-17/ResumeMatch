package com.resumematch.exception;

/**
 * Thrown when an uploaded resume file has no content.
 *
 * <p>Demonstrates: custom checked exception, inheritance from an abstract base class.</p>
 */
public class EmptyResumeException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "This file is empty. Choose a resume that contains text.";

    public EmptyResumeException() {
        super(MESSAGE);
    }
}
