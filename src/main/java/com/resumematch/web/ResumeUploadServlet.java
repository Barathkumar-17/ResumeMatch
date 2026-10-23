package com.resumematch.web;

import com.resumematch.dao.ResumeDAO;
import com.resumematch.dao.SkillDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.FileService;
import com.resumematch.service.ResumeAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

/**
 * Receives the resume upload ({@code POST /upload}, multipart form, file field
 * {@code resume}) and runs the whole pipeline: check and save the file under
 * {@code resumes/user_<id>/}, read its text back, find the skills and score it, then store
 * the resume row and its skill rows in one database transaction. On success the browser
 * is redirected to {@code /analysis}.
 *
 * <p>Any problem (no file chosen, not a .txt, empty, over 200 KB, unreadable, database
 * down) re-shows the dashboard with the exception's friendly message. If the file was
 * already written when a later step fails, it is deleted again, so a failed upload leaves
 * nothing behind on disk or in the database.</p>
 *
 * <p>The container's own size limit is set a little above 200 KB, so normal uploads are
 * checked by {@link FileService} and get its exact messages; a much larger upload is
 * stopped by the container and gets the same "larger than 200 KB" message.</p>
 *
 * <p>Demonstrates: Servlets with {@code @MultipartConfig} and {@code Part}, Java File
 * Handling through {@link FileService}, try-with-resources on an {@code InputStream},
 * custom exception handling (one {@code catch} for the abstract {@link AppException}),
 * a JDBC transaction through {@link ResumeDAO#saveResumeWithSkills}, Post/Redirect/Get.</p>
 */
@WebServlet("/upload")
@MultipartConfig(maxFileSize = ResumeUploadServlet.MAX_PART_BYTES,
                 maxRequestSize = ResumeUploadServlet.MAX_REQUEST_BYTES)
public class ResumeUploadServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ResumeUploadServlet.class.getName());

    private static final long serialVersionUID = 1L;

    /** Container limit for the file: 210 KB, just above the 200 KB rule FileService checks. */
    static final long MAX_PART_BYTES = 210L * 1024;

    /** Container limit for the whole request (file plus form overhead). */
    static final long MAX_REQUEST_BYTES = 220L * 1024;

    /**
     * Optional context parameter naming the folder that holds {@code user_<id>} folders.
     * Without it, {@link FileService#DEFAULT_BASE_DIR} ({@code resumes}) is used.
     */
    static final String RESUME_DIR_PARAM = "resumeDir";

    /** Session attribute holding a one-time success message for the next page. */
    static final String FLASH = "flash";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // Only the form posts here; a typed-in URL goes back to the dashboard.
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        User user = (User) request.getSession().getAttribute("user");
        FileService fileService = createFileService();
        Path saved = null;
        try {
            Part part = readFilePart(request);
            String fileName = (part == null) ? null : part.getSubmittedFileName();
            long size = (part == null) ? -1 : part.getSize();

            // Checks name, type and size before anything is written.
            fileService.validate(fileName, size);
            try (InputStream content = part.getInputStream()) {
                saved = fileService.saveResume(user.getId(), fileName, size, content);
            }

            Resume resume = new Resume();
            resume.setUserId(user.getId());
            resume.setFileName(FileService.displayName(fileName));
            resume.setFilePath(saved.toString());
            resume.setResumeText(fileService.readResume(saved));

            SkillDAO skillDAO = new SkillDAO();
            new ResumeAnalyzer(skillDAO.findAll()).analyze(resume);
            Set<Integer> skillIds = skillDAO.findIdsByNames(resume.getSkills());
            new ResumeDAO().saveResumeWithSkills(resume, skillIds);

            request.getSession().setAttribute(FLASH, "Resume analyzed. Score " + resume.getScore()
                    + "/100, " + resume.getSkills().size() + " skills found.");
            response.sendRedirect(request.getContextPath() + "/analysis");
        } catch (AppException e) {
            fileService.deleteResume(saved); // does nothing when nothing was saved
            showDashboard(request, response, user, e.getUserMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "upload failed while reading the request or file", e);
            fileService.deleteResume(saved);
            showDashboard(request, response, user, FileReadException.MESSAGE);
        }
    }

    /**
     * Gets the {@code resume} part, turning container problems into friendly exceptions.
     *
     * @return the part, or {@code null} when the form had no file field
     */
    private static Part readFilePart(HttpServletRequest request) throws AppException {
        try {
            return request.getPart("resume");
        } catch (IllegalStateException e) {
            // Thrown when the upload is bigger than the @MultipartConfig limits.
            throw InvalidFileException.tooLarge();
        } catch (IOException | ServletException e) {
            // Not a multipart request, or the upload was cut off.
            LOGGER.log(Level.WARNING, "could not read the upload request", e);
            throw new FileReadException(e);
        }
    }

    /** Re-shows the full dashboard with the message under the upload area. */
    private void showDashboard(HttpServletRequest request, HttpServletResponse response,
                               User user, String message) throws ServletException, IOException {
        try {
            DashboardServlet.loadDashboard(request, user);
        } catch (AppException e) {
            // The upload message matters more; the stats simply show as empty.
        }
        request.setAttribute("error", message);
        request.getRequestDispatcher(DashboardServlet.VIEW).forward(request, response);
    }

    private FileService createFileService() {
        return new FileService(resumeBaseDir(getServletContext()));
    }

    /**
     * Folder that holds uploaded resumes. Uses the {@code resumeDir} context parameter
     * when set, otherwise {@code <catalina.base>/resumes}, otherwise {@code resumes}
     * next to the working directory. Shared with the download servlet.
     */
    static Path resumeBaseDir(ServletContext context) {
        String dir = context.getInitParameter(RESUME_DIR_PARAM);
        if (dir != null && !dir.trim().isEmpty()) {
            return Paths.get(dir.trim());
        }
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null && !catalinaBase.isEmpty()) {
            return Paths.get(catalinaBase, FileService.DEFAULT_BASE_DIR);
        }
        return Paths.get(FileService.DEFAULT_BASE_DIR);
    }
}
