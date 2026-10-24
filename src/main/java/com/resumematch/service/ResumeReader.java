package com.resumematch.service;

import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;

import java.nio.file.Path;
import java.text.Normalizer;

/**
 * Reads the text of a stored resume file. The rest of the application only depends on
 * this interface, so {@link TxtResumeReader}, {@link PdfResumeReader} and
 * {@link DocxResumeReader} can be swapped without changing {@link FileService} or
 * {@link ResumeAnalyzer}.
 *
 * <p>Demonstrates: interfaces (abstraction, static helper method, constants), programming
 * to an interface, checked exceptions.</p>
 */
public interface ResumeReader {

    /**
     * Longest text kept from a PDF or DOCX, in characters. This is a sensible limit for
     * analysis (a resume is normally well under 20,000 characters); the {@code resumes.resume_text}
     * column is a MEDIUMTEXT column, so storage is not the constraint.
     */
    int MAX_TEXT_CHARS = 20000;

    /**
     * Returns the full text of the file, lines separated by {@code \n}.
     *
     * @param p the file to read
     * @return the file's text (may be empty; the caller decides whether that is an error)
     * @throws FileReadException    when the file is missing or cannot be read
     * @throws InvalidFileException when the file is readable but its text cannot be extracted
     *                              (encrypted or scanned PDF, damaged document)
     */
    String read(Path p) throws FileReadException, InvalidFileException;

    /**
     * Cleans text taken out of a PDF or DOCX: ligatures and non-breaking spaces become
     * plain characters (Unicode NFKC), private-use bullet symbols become a bullet, control
     * characters are dropped, spaces are collapsed, every line is trimmed, blank lines are
     * limited to one in a row and the result is cut to {@link #MAX_TEXT_CHARS}.
     */
    static String tidy(CharSequence raw) {
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(text.length());
        int newlines = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < text.length() && out.length() < MAX_TEXT_CHARS; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                newlines++;
                pendingSpace = false;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '­') {
                pendingSpace = c != '­';
                continue;
            }
            if (c == '�' || Character.isISOControl(c)) {
                continue;
            }
            if (c >= '' && c <= '') {
                c = '•'; // private-use symbol fonts (Wingdings, Symbol) are almost always bullets
            }
            if (out.length() > 0) {
                if (newlines > 0) {
                    out.append(newlines > 1 ? "\n\n" : "\n");
                } else if (pendingSpace) {
                    out.append(' ');
                }
            }
            newlines = 0;
            pendingSpace = false;
            out.append(c);
        }
        return out.toString();
    }
}
