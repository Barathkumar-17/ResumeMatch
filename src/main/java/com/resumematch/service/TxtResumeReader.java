package com.resumematch.service;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.resumematch.exception.FileReadException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a plain-text (.txt) resume line by line with a {@link BufferedReader}, decoding
 * it as UTF-8. Any {@link IOException} is wrapped in a {@link FileReadException} so the
 * user only sees a friendly message.
 *
 * <p>Demonstrates: interface implementation, Java File Handling ({@code BufferedReader},
 * {@code InputStreamReader}, {@code java.nio.file.Files}), try-with-resources,
 * exception wrapping, {@code StringBuilder}.</p>
 */
public class TxtResumeReader implements ResumeReader {

    private static final Logger LOGGER = Logger.getLogger(TxtResumeReader.class.getName());

    /** Byte order mark that some Windows editors put at the start of a UTF-8 file. */
    private static final char BOM = '﻿';

    @Override
    public String read(Path p) throws FileReadException {
        if (p == null) {
            throw new FileReadException();
        }
        StringBuilder text = new StringBuilder();
        // try-with-resources closes the reader even when reading fails half way
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(p), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    if (!line.isEmpty() && line.charAt(0) == BOM) {
                        line = line.substring(1);
                    }
                    first = false;
                } else {
                    text.append('\n');
                }
                text.append(line);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not read the resume file", e);
            throw new FileReadException(e);
        }
        return text.toString();
    }
}
