package com.resumematch.service;

import com.resumematch.exception.InvalidFileException;
import com.resumematch.util.ValidationUtil;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The resume file types the application accepts: {@code .txt}, {@code .pdf} and
 * {@code .docx}. Each type knows its extension, its download Content-Type, its size limit
 * and how to check that the bytes really are that kind of file (the file name alone is
 * never trusted): UTF-8 text without NUL bytes, a {@code %PDF-} header, or a ZIP package
 * that holds {@code word/document.xml}.
 *
 * <p>Demonstrates: enum with fields, constructor and methods, behaviour per constant
 * (switch), byte-array handling, charset decoding, static factory lookup.</p>
 */
public enum ResumeFileType {

    TXT(".txt", "text/plain; charset=UTF-8", ValidationUtil.MAX_FILE_SIZE_BYTES),
    PDF(".pdf", "application/pdf", ValidationUtil.MAX_DOCUMENT_SIZE_BYTES),
    DOCX(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ValidationUtil.MAX_DOCUMENT_SIZE_BYTES);

    private static final String NOT_TEXT_MESSAGE =
            "This file doesn't look like plain text. Save it again as a .txt file (UTF-8) and upload it.";
    private static final String NOT_PDF_MESSAGE =
            "This file isn't a real PDF. Export your resume as a PDF again and upload that.";
    private static final String NOT_DOCX_MESSAGE =
            "This file isn't a valid .docx document. Save it again as .docx (not the older .doc) and upload it.";

    /** Most entries looked at in a .docx package; a real resume has a few dozen. */
    private static final int MAX_ZIP_ENTRIES = 2000;

    private final String extension;
    private final String contentType;
    private final long maxBytes;

    ResumeFileType(String extension, String contentType, long maxBytes) {
        this.extension = extension;
        this.contentType = contentType;
        this.maxBytes = maxBytes;
    }

    /** The extension including the dot, for example {@code .pdf}. */
    public String getExtension() {
        return extension;
    }

    /** The Content-Type used when the original file is downloaded. */
    public String getContentType() {
        return contentType;
    }

    /** The largest upload of this type, in bytes. */
    public long getMaxBytes() {
        return maxBytes;
    }

    /** True when the size is within this type's limit. */
    public boolean isWithinSizeLimit(long sizeInBytes) {
        return sizeInBytes <= maxBytes;
    }

    /** The "too large" exception that names this type's limit. */
    public InvalidFileException tooLarge() {
        return this == TXT ? InvalidFileException.tooLarge() : InvalidFileException.documentTooLarge();
    }

    /**
     * Finds the type from a file name's ending, ignoring case.
     *
     * @return the type, or {@code null} for any other file (including the old {@code .doc})
     */
    public static ResumeFileType fromFileName(String fileName) {
        if (ValidationUtil.isBlank(fileName)) {
            return null;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        for (ResumeFileType type : values()) {
            if (lower.endsWith(type.extension)) {
                return type;
            }
        }
        return null;
    }

    /** Finds the type of a stored file from its server-generated name. */
    public static ResumeFileType fromPath(Path path) {
        return (path == null || path.getFileName() == null) ? null
                : fromFileName(path.getFileName().toString());
    }

    /**
     * Checks that the bytes really are this kind of file.
     *
     * @throws InvalidFileException with a friendly message when they are not
     */
    public void checkContent(byte[] data) throws InvalidFileException {
        switch (this) {
            case TXT:
                checkPlainText(data);
                break;
            case PDF:
                if (!startsWith(data, new byte[] {'%', 'P', 'D', 'F', '-'})) {
                    throw new InvalidFileException(NOT_PDF_MESSAGE);
                }
                break;
            default:
                if (!startsWith(data, new byte[] {'P', 'K', 3, 4}) || !zipHasEntry(data, "word/document.xml")) {
                    throw new InvalidFileException(NOT_DOCX_MESSAGE);
                }
                break;
        }
    }

    /** Rejects bytes that hold a NUL or are not valid UTF-8 (so binary files are refused). */
    private static void checkPlainText(byte[] data) throws InvalidFileException {
        for (byte b : data) {
            if (b == 0) {
                throw new InvalidFileException(NOT_TEXT_MESSAGE);
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
        } catch (CharacterCodingException e) {
            throw new InvalidFileException(NOT_TEXT_MESSAGE);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Looks for a file name in the ZIP central directory without unpacking anything, so a
     * zip bomb costs nothing here. Every offset is range-checked and the entry count is capped.
     */
    private static boolean zipHasEntry(byte[] d, String entryName) {
        int eocd = -1;
        for (int i = d.length - 22; i >= 0 && i >= d.length - 22 - 65535; i--) {
            if (littleEndian32(d, i) == 0x06054b50L) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) {
            return false;
        }
        int count = Math.min(littleEndian16(d, eocd + 10), MAX_ZIP_ENTRIES);
        long pos = littleEndian32(d, eocd + 16);
        for (int k = 0; k < count; k++) {
            if (pos < 0 || pos + 46 > d.length || littleEndian32(d, (int) pos) != 0x02014b50L) {
                return false;
            }
            int p = (int) pos;
            int nameLen = littleEndian16(d, p + 28);
            int extraLen = littleEndian16(d, p + 30);
            int commentLen = littleEndian16(d, p + 32);
            if (p + 46L + nameLen > d.length) {
                return false;
            }
            if (entryName.equals(new String(d, p + 46, nameLen, StandardCharsets.UTF_8))) {
                return true;
            }
            pos = p + 46L + nameLen + extraLen + commentLen;
        }
        return false;
    }

    private static int littleEndian16(byte[] d, int i) {
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8);
    }

    private static long littleEndian32(byte[] d, int i) {
        return (littleEndian16(d, i) & 0xFFFFL) | ((long) littleEndian16(d, i + 2) << 16);
    }
}
