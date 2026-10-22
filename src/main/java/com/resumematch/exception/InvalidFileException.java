package com.resumematch.exception;

/**
 * Thrown when an upload is rejected before it is saved: it is not a .txt file, or it is
 * larger than the size limit. Use the static factory methods for the standard messages.
 *
 * <p>Demonstrates: custom checked exception, static factory methods.</p>
 */
public class InvalidFileException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String TOO_LARGE_MESSAGE =
            "This file is larger than 200 KB. Resumes are usually under 20 KB of text.";

    public InvalidFileException(String userMessage) {
        super(userMessage);
    }

    /** The upload is not a plain-text file. */
    public static InvalidFileException notTxt(String fileName) {
        return new InvalidFileException("\"" + fileName + "\" isn't a .txt file. "
                + "This version reads plain-text resumes only; PDF support comes later.");
    }

    /** The upload is over the 200 KB limit. */
    public static InvalidFileException tooLarge() {
        return new InvalidFileException(TOO_LARGE_MESSAGE);
    }
}
