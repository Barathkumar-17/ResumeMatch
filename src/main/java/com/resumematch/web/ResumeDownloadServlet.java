package com.resumematch.web;

import com.resumematch.dao.ResumeDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.FileService;
import com.resumematch.service.ResumeFileType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends the logged-in user's stored resume file back as a download: the latest one, or
 * the one named by {@code ?id=}. A resume is only served when it belongs to the user in
 * the session and its file lies inside that user's own folder under the resumes folder,
 * so a changed id or a path with "../" in it can never reach another user's file or any
 * other file on the server.
 * The original file is sent as an attachment with the Content-Type of its type
 * ({@code text/plain}, {@code application/pdf} or the Word type) and {@code nosniff}, and the download
 * name is cleaned so it cannot add headers or break out of the quotes.
 *
 * <p>Demonstrates: Servlets (streaming a response, setting headers), Java File Handling
 * ({@code Path.normalize}, {@code startsWith}, {@code Files.newInputStream}),
 * try-with-resources, input validation, {@code java.util.logging}, catching custom exceptions.</p>
 */
@WebServlet("/resume-file")
public class ResumeDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ResumeDownloadServlet.class.getName());

    private static final int BUFFER_SIZE = 8192;

    private static final String FALLBACK_NAME = "resume.txt";

    private static final String HEX = "0123456789ABCDEF";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Resume resume;
        try {
            resume = findOwnedResume(user.getId(), request.getParameter("id"));
        } catch (AppException e) {
            LOGGER.log(Level.SEVERE, "could not load the resume for user " + user.getId(), e);
            // A download has no page to show the message on, so it is sent as plain text.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getWriter().write(e.getUserMessage());
            return;
        }

        // Another user's resume is answered exactly like a missing one.
        if (resume == null || resume.getUserId() != user.getId()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Only the owner's own folder, resumes/user_<id>/, is allowed.
        Path userDir = ResumeUploadServlet.resumeBaseDir(getServletContext()).resolve("user_" + user.getId());
        Path file = resolveInside(userDir, resume.getFilePath());
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            LOGGER.log(Level.WARNING, "file missing or outside the resumes folder for resume "
                    + resume.getId());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        ResumeFileType type = ResumeFileType.fromPath(file);
        response.setContentType(type == null ? "application/octet-stream" : type.getContentType());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", contentDisposition(resume.getFileName()));
        response.setContentLengthLong(Files.size(file));

        try (InputStream in = Files.newInputStream(file);
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * Finds the user's latest resume, or the one with the given id among the user's own
     * resumes. Only the user's resumes are searched, so another user's id finds nothing.
     *
     * @return the resume, or null when there is none or the id is not a valid number
     */
    private static Resume findOwnedResume(int userId, String idParam) throws AppException {
        ResumeDAO dao = new ResumeDAO();
        if (idParam == null || idParam.trim().isEmpty()) {
            return dao.findLatestByUser(userId);
        }
        int id;
        try {
            id = Integer.parseInt(idParam.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        List<Resume> resumes = dao.findAllByUser(userId);
        for (Resume r : resumes) {
            if (r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    /**
     * Turns a stored path into an absolute, normalised path and checks it is inside the
     * base folder. "resumes/user_1/../../secret.txt" normalises to "secret.txt", which is
     * outside, so it is refused. A relative stored path is read against the working
     * directory, the same way it was written.
     *
     * @return the safe absolute path, or null when the path is blank, invalid or outside
     */
    static Path resolveInside(Path baseDir, String storedPath) {
        if (baseDir == null || storedPath == null || storedPath.trim().isEmpty()) {
            return null;
        }
        try {
            Path base = baseDir.toAbsolutePath().normalize();
            Path file = Paths.get(storedPath.trim()).toAbsolutePath().normalize();
            if (!file.startsWith(base) || file.equals(base)) {
                return null;
            }
            return file;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Builds a Content-Disposition value that cannot inject headers: the plain
     * {@code filename="..."} holds only letters, digits, '_', '.' and '-' (so no quotes or
     * line breaks), and {@code filename*=UTF-8''...} carries the real name percent-encoded.
     */
    static String contentDisposition(String name) {
        String shown = FileService.displayName(name).replace("\"", "");
        if (shown.isEmpty()) {
            shown = FALLBACK_NAME;
        }
        return "attachment; filename=\"" + safeFileName(shown) + "\"; filename*=UTF-8''" + percentEncode(shown);
    }

    /**
     * Makes the name safe for the quoted Content-Disposition filename: keeps letters,
     * digits, '_', '.' and '-', replaces everything else (quotes, slashes, CR/LF) with '_'.
     */
    static String safeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return FALLBACK_NAME;
        }
        String base = FileService.displayName(name).replaceAll("[^A-Za-z0-9_.\\-]", "_");
        if (base.isEmpty() || base.matches("[._]*")) {
            return FALLBACK_NAME;
        }
        return base;
    }

    /** RFC 5987 encoding: UTF-8 bytes, everything except letters, digits and "._-" as %XX. */
    private static String percentEncode(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                out.append((char) c);
            } else {
                out.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0x0F));
            }
        }
        return out.toString();
    }
}
