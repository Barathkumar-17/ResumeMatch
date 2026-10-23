package com.resumematch.exception;

/**
 * Thrown when a resume file cannot be written or read back. Wraps the underlying
 * {@code IOException} so the cause is still in the server log.
 *
 * <p>Demonstrates: custom checked exception, exception chaining (wraps IOException).</p>
 */
public class FileReadException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "The file couldn't be read. Try saving it again as .txt, .pdf or .docx.";

    public FileReadException() {
        super(MESSAGE);
    }

    public FileReadException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
