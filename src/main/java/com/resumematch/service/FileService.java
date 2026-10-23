package com.resumematch.service;

import com.resumematch.exception.EmptyResumeException;
import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;
import com.resumematch.exception.MissingFieldException;
import com.resumematch.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the resume file on disk: checks an upload (a .txt, .pdf or .docx that is not
 * empty, at most 200 KB for .txt and 2 MB for .pdf and .docx, and whose bytes really are
 * that kind of file), creates the {@code resumes/user_<id>/} folder, saves the file under
 * a name the server makes up ({@code <timestamp>_<random>.<ext>}, never the name the
 * browser sent) so two uploads never overwrite each other, and reads the text back through
 * the {@link ResumeReader} that matches the file type. Every path is normalised and must
 * stay inside the base folder before it is written, read or deleted.
 *
 * <p>Demonstrates: Java File Handling ({@code java.nio.file.Files}, {@code Path},
 * {@code InputStream}), composition (has one {@link ResumeReader} per file type, typed by
 * the interface, kept in an {@code EnumMap}), custom checked exceptions, exception
 * wrapping, constructor overloading.</p>
 */
public class FileService {

    private static final Logger LOGGER = Logger.getLogger(FileService.class.getName());

    /** Folder (relative to the working directory) used when no other folder is given. */
    public static final String DEFAULT_BASE_DIR = "resumes";

    /** Longest file name kept for display and in the database. */
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final Path baseDir;
    private final Map<ResumeFileType, ResumeReader> readers = new EnumMap<>(ResumeFileType.class);

    /** Stores files under {@code ./resumes}. */
    public FileService() {
        this(Paths.get(DEFAULT_BASE_DIR));
    }

    /** Stores files under the given folder; .txt, .pdf and .docx each get their own reader. */
    public FileService(Path baseDir) {
        this.baseDir = baseDir;
        readers.put(ResumeFileType.TXT, new TxtResumeReader());
        readers.put(ResumeFileType.PDF, new PdfResumeReader());
        readers.put(ResumeFileType.DOCX, new DocxResumeReader());
    }

    /** The root folder that holds every {@code user_<id>} folder. */
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Checks an upload before anything is written, in this order: a file was chosen,
     * the name ends in .txt, .pdf or .docx, it is not empty, it is not over the limit for
     * its type (200 KB for .txt, 2 MB for .pdf and .docx).
     *
     * @param fileName    the name the browser sent
     * @param sizeInBytes the upload size, or -1 when unknown (then only checked after reading)
     */
    public void validate(String fileName, long sizeInBytes)
            throws MissingFieldException, InvalidFileException, EmptyResumeException {
        if (ValidationUtil.isBlank(fileName)) {
            throw new MissingFieldException("file", "Choose a resume file first.");
        }
        String name = displayName(fileName);
        ResumeFileType type = ResumeFileType.fromFileName(name);
        if (type == null) {
            throw InvalidFileException.unsupportedType(name);
        }
        if (sizeInBytes == 0) {
            throw new EmptyResumeException();
        }
        if (sizeInBytes > 0 && !type.isWithinSizeLimit(sizeInBytes)) {
            throw type.tooLarge();
        }
    }

    /**
     * Validates the upload, then saves it as
     * {@code <baseDir>/user_<userId>/<timestamp>_<random>.<txt|pdf|docx>}.
     * The bytes are read first (never more than the type's size limit plus one byte), so an
     * empty, too large or fake upload (a .pdf that is really text, a .docx that is not a
     * Word package) is rejected before anything is written to disk.
     *
     * @param userId      owner of the resume
     * @param fileName    original file name from the browser; only used for the checks
     * @param sizeInBytes upload size, or -1 when unknown
     * @param content     the uploaded bytes; the caller closes it
     * @return the normalised absolute path of the saved file
     */
    public Path saveResume(int userId, String fileName, long sizeInBytes, InputStream content)
            throws MissingFieldException, InvalidFileException, EmptyResumeException,
                   FileReadException {
        validate(fileName, sizeInBytes);
        ResumeFileType type = ResumeFileType.fromFileName(displayName(fileName));
        if (content == null) {
            throw new FileReadException();
        }

        // The declared size can be missing or wrong, so check what actually arrives.
        byte[] data;
        try {
            data = content.readNBytes((int) type.getMaxBytes() + 1);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not read the uploaded bytes", e);
            throw new FileReadException(e);
        }
        if (data.length == 0) {
            throw new EmptyResumeException();
        }
        if (!type.isWithinSizeLimit(data.length)) {
            throw type.tooLarge();
        }
        type.checkContent(data);

        Path target;
        try {
            Path userDir = baseDir.resolve("user_" + userId);
            Files.createDirectories(userDir);
            target = newTargetFile(userDir, type);
            if (!isInsideBase(target)) {
                LOGGER.log(Level.SEVERE, "refusing to write outside the resumes folder: " + target);
                throw new FileReadException();
            }
            // CREATE_NEW fails instead of replacing an existing file.
            Files.write(target, data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "could not save the resume file", e);
            throw new FileReadException(e);
        }
        return target.toAbsolutePath().normalize();
    }

    /**
     * Reads a stored resume back with the {@link ResumeReader} for its file type.
     *
     * @throws EmptyResumeException when the file holds no text (only blank lines counts as empty)
     * @throws FileReadException    when the file cannot be read or lies outside the base folder
     * @throws InvalidFileException when the text cannot be extracted (encrypted or scanned PDF,
     *                              damaged document)
     */
    public String readResume(Path path)
            throws FileReadException, EmptyResumeException, InvalidFileException {
        if (path == null || !isInsideBase(path)) {
            LOGGER.log(Level.WARNING, "refusing to read outside the resumes folder: " + path);
            throw new FileReadException();
        }
        ResumeReader reader = readers.get(ResumeFileType.fromPath(path));
        if (reader == null) {
            throw new FileReadException();
        }
        String text = reader.read(path);
        if (ValidationUtil.isBlank(text)) {
            throw new EmptyResumeException();
        }
        return text;
    }

    /**
     * Deletes a stored resume, for example when saving it to the database failed. A path
     * outside the base folder is never deleted.
     *
     * @return true when a file was deleted
     */
    public boolean deleteResume(Path path) {
        if (path == null) {
            return false;
        }
        if (!isInsideBase(path)) {
            LOGGER.log(Level.WARNING, "refusing to delete outside the resumes folder: " + path);
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not delete " + path, e);
            return false;
        }
    }

    /**
     * Reduces the name a browser sent to a safe display name: folder parts (both '/' and
     * '\\') are dropped, control characters are removed and the result is cut to
     * {@value #MAX_DISPLAY_NAME_LENGTH} characters, keeping a .txt, .pdf or .docx ending.
     * ("C:\\docs\\cv.txt" becomes "cv.txt"; null becomes an empty string.)
     */
    public static String displayName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isISOControl(c)) {
                clean.append(c);
            }
        }
        name = clean.toString().trim();
        if (name.length() > MAX_DISPLAY_NAME_LENGTH) {
            ResumeFileType type = ResumeFileType.fromFileName(name);
            if (type != null) {
                int extLength = type.getExtension().length();
                name = name.substring(0, MAX_DISPLAY_NAME_LENGTH - extLength)
                        + name.substring(name.length() - extLength);
            } else {
                name = name.substring(0, MAX_DISPLAY_NAME_LENGTH);
            }
        }
        return name;
    }

    /** True when the normalised absolute path lies strictly inside the base folder. */
    public boolean isInsideBase(Path path) {
        try {
            Path base = baseDir.toAbsolutePath().normalize();
            Path file = path.toAbsolutePath().normalize();
            return file.startsWith(base) && !file.equals(base);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Picks {@code <millis>_<random>.<ext>} inside the folder. The random part is a UUID
     * fragment, so the name never depends on anything the browser sent; if the name is
     * somehow taken the next one is tried.
     */
    private static Path newTargetFile(Path userDir, ResumeFileType type) {
        Path candidate;
        do {
            String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            candidate = userDir.resolve(System.currentTimeMillis() + "_" + random + type.getExtension());
        } while (Files.exists(candidate));
        return candidate;
    }
}
