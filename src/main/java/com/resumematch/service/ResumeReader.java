package com.resumematch.service;

import com.resumematch.exception.FileReadException;

import java.nio.file.Path;

/**
 * Reads the text of a stored resume file. The rest of the application only depends on
 * this interface, so a {@code PdfResumeReader} could be added later without changing
 * {@link FileService} or {@link ResumeAnalyzer}.
 *
 * <p>Demonstrates: interfaces (abstraction), programming to an interface, checked exceptions.</p>
 */
public interface ResumeReader {

    /**
     * Returns the full text of the file, lines separated by {@code \n}.
     *
     * @param p the file to read
     * @return the file's text (may be empty; the caller decides whether that is an error)
     * @throws FileReadException when the file is missing or cannot be read
     */
    String read(Path p) throws FileReadException;
}
