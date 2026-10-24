package com.resumematch.exception;

/**
 * Thrown when a new upload would push the total size of the stored resumes over the
 * storage limit. The message is safe to show: it names no folder and no size.
 *
 * <p>Demonstrates: custom checked exception, inheritance from an abstract base class.</p>
 */
public class StorageFullException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Uploads are paused: storage limit reached. Please try again later.";

    public StorageFullException() {
        super(MESSAGE);
    }
}
