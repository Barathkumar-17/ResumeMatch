package com.resumematch.service;

import com.resumematch.exception.EmptyResumeException;
import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;
import com.resumematch.exception.MissingFieldException;
import com.resumematch.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles the resume file on disk: checks an upload (must be a .txt, not empty, at most
 * 200 KB), creates the {@code resumes/user_<id>/} folder, saves the file as
 * {@code <timestamp>_<name>} so two uploads never overwrite each other, and reads the
 * text back through a {@link ResumeReader}.
 *
 * <p>Demonstrates: Java File Handling ({@code java.nio.file.Files}, {@code Path},
 * {@code InputStream}), composition (has a {@link ResumeReader}, typed by the interface),
 * custom checked exceptions, exception wrapping, constructor overloading.</p>
 */
public class FileService {

    /** Folder (relative to the working directory) used when no other folder is given. */
    public static final String DEFAULT_BASE_DIR = "resumes";

    private final Path baseDir;
    private final ResumeReader reader;

    /** Stores files under {@code ./resumes} and reads them with a {@link TxtResumeReader}. */
    public FileService() {
        this(Paths.get(DEFAULT_BASE_DIR));
    }

    /** Stores files under the given folder and reads them with a {@link TxtResumeReader}. */
    public FileService(Path baseDir) {
        this(baseDir, new TxtResumeReader());
    }

    /** Stores files under the given folder and reads them with the given reader. */
    public FileService(Path baseDir, ResumeReader reader) {
        this.baseDir = baseDir;
        this.reader = reader;
    }

    /** The root folder that holds every {@code user_<id>} folder. */
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Checks an upload before anything is written, in this order: a file was chosen,
     * the name ends in .txt, it is not empty, it is not over 200 KB.
     *
     * @param fileName    the name the browser sent
     * @param sizeInBytes the upload size, or -1 when unknown (then only checked after saving)
     */
    public void validate(String fileName, long sizeInBytes)
            throws MissingFieldException, InvalidFileException, EmptyResumeException {
        if (ValidationUtil.isBlank(fileName)) {
            throw new MissingFieldException("file", "Choose a resume file first.");
        }
        if (!ValidationUtil.isTxtFile(fileName)) {
            throw InvalidFileException.notTxt(baseName(fileName));
        }
        if (sizeInBytes == 0) {
            throw new EmptyResumeException();
        }
        if (sizeInBytes > 0 && !ValidationUtil.isWithinSizeLimit(sizeInBytes)) {
            throw InvalidFileException.tooLarge();
        }
    }

    /**
     * Validates the upload, then saves it as {@code <baseDir>/user_<userId>/<timestamp>_<name>}.
     * If the saved file turns out empty or too large it is deleted again, so a rejected
     * upload never stays on disk.
     *
     * @param userId      owner of the resume
     * @param fileName    original file name from the browser
     * @param sizeInBytes upload size, or -1 when unknown
     * @param content     the uploaded bytes; the caller closes it
     * @return the path of the saved file
     */
    public Path saveResume(int userId, String fileName, long sizeInBytes, InputStream content)
            throws MissingFieldException, InvalidFileException, EmptyResumeException,
                   FileReadException {
        validate(fileName, sizeInBytes);
        if (content == null) {
            throw new FileReadException();
        }

        Path target;
        try {
            Path userDir = baseDir.resolve("user_" + userId);
            Files.createDirectories(userDir);
            target = newTargetFile(userDir, safeName(fileName));
            Files.copy(content, target);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FileReadException(e);
        }

        // The declared size can be missing or wrong, so check what actually landed on disk.
        long actualSize;
        try {
            actualSize = Files.size(target);
        } catch (IOException e) {
            e.printStackTrace();
            deleteResume(target);
            throw new FileReadException(e);
        }
        if (actualSize == 0) {
            deleteResume(target);
            throw new EmptyResumeException();
        }
        if (!ValidationUtil.isWithinSizeLimit(actualSize)) {
            deleteResume(target);
            throw InvalidFileException.tooLarge();
        }
        return target;
    }

    /**
     * Reads a stored resume back with the {@link ResumeReader}.
     *
     * @throws EmptyResumeException when the file holds no text (only blank lines counts as empty)
     */
    public String readResume(Path path) throws FileReadException, EmptyResumeException {
        String text = reader.read(path);
        if (ValidationUtil.isBlank(text)) {
            throw new EmptyResumeException();
        }
        return text;
    }

    /**
     * Deletes a stored resume, for example when saving it to the database failed.
     *
     * @return true when a file was deleted
     */
    public boolean deleteResume(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Picks {@code <millis>_<name>} inside the folder; if that name is already taken
     * (two uploads in the same millisecond) the next number is tried. {@code Files.copy}
     * never replaces an existing file, so nothing is ever overwritten.
     */
    private static Path newTargetFile(Path userDir, String name) {
        long stamp = System.currentTimeMillis();
        Path candidate = userDir.resolve(stamp + "_" + name);
        while (Files.exists(candidate)) {
            stamp++;
            candidate = userDir.resolve(stamp + "_" + name);
        }
        return candidate;
    }

    /** Drops any folder part a browser may send ("C:\\docs\\cv.txt" becomes "cv.txt"). */
    private static String baseName(String fileName) {
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return (slash >= 0) ? name.substring(slash + 1) : name;
    }

    /** Keeps letters, digits, '_', '.' and '-'; everything else becomes '_'. */
    private static String safeName(String fileName) {
        return baseName(fileName).replaceAll("[^A-Za-z0-9_.\\-]", "_");
    }
}
