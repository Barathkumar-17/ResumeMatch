package com.resumematch.exception;

/**
 * Thrown when an upload is rejected: it is not a .txt, .pdf or .docx file, it is larger
 * than the size limit, or the text cannot be read out of it (an encrypted or scanned PDF,
 * a damaged document). Use the static factory methods for the standard messages.
 *
 * <p>Demonstrates: custom checked exception, static factory methods.</p>
 */
public class InvalidFileException extends AppException {

    private static final long serialVersionUID = 1L;

    public static final String TOO_LARGE_MESSAGE =
            "This file is larger than 200 KB. Resumes are usually under 20 KB of text.";

    public static final String DOCUMENT_TOO_LARGE_MESSAGE =
            "This file is larger than 2 MB. Export a smaller PDF or DOCX, or upload a .txt file.";

    public static final String UPLOAD_TOO_LARGE_MESSAGE =
            "This file is too large. A .txt resume can be up to 200 KB; a .pdf or .docx up to 2 MB.";

    public InvalidFileException(String userMessage) {
        super(userMessage);
    }

    /** The upload is not a .txt, .pdf or .docx file (for example a legacy .doc). */
    public static InvalidFileException unsupportedType(String fileName) {
        return new InvalidFileException("\"" + fileName + "\" can't be used. "
                + "Upload a .txt, .pdf or .docx file.");
    }

    /** The upload is a .txt file over the 200 KB limit. */
    public static InvalidFileException tooLarge() {
        return new InvalidFileException(TOO_LARGE_MESSAGE);
    }

    /** The upload is a .pdf or .docx file over the 2 MB limit. */
    public static InvalidFileException documentTooLarge() {
        return new InvalidFileException(DOCUMENT_TOO_LARGE_MESSAGE);
    }

    /** The container stopped the upload before its type was known. */
    public static InvalidFileException uploadTooLarge() {
        return new InvalidFileException(UPLOAD_TOO_LARGE_MESSAGE);
    }

    /** The PDF is password protected or encrypted. */
    public static InvalidFileException encryptedPdf() {
        return new InvalidFileException("This PDF is password-protected, so its text can't be read. "
                + "Remove the protection, or upload a .txt or .docx copy instead.");
    }

    /** The PDF has no text layer, for example a scan or a photo of a page. */
    public static InvalidFileException noTextInPdf() {
        return new InvalidFileException("No text could be read from this PDF. It may be a scanned image. "
                + "Export a text-based PDF from Word or Google Docs, or upload a .txt or .docx file.");
    }

    /** The PDF or DOCX is damaged or uses a layout that cannot be read. */
    public static InvalidFileException unreadableDocument(String kind) {
        return new InvalidFileException("This " + kind + " file couldn't be read; it may be damaged. "
                + "Save it again, or upload a .txt file instead.");
    }
}
