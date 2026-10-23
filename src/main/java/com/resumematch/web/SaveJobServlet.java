package com.resumematch.web;

import com.resumematch.dao.ApplicationDAO;
import com.resumematch.dao.JobDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.Job;
import com.resumematch.model.User;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Saves a job to, or removes it from, the user's "My jobs" list. The Save button on the
 * job search and job detail pages posts here with {@code jobId} and {@code action}
 * ({@code save} or {@code remove}; remove only drops the bookmark and never an application,
 * which is deleted from the My jobs page instead); afterwards the browser goes back to the page it came
 * from (the Referer header) so the button can show its new state.
 *
 * <p>A one-shot message is left in the session for the next page to show: {@code flash}
 * on success, {@code flashError} when something went wrong.</p>
 *
 * <p>Demonstrates: {@code HttpServlet} doPost, the Post/Redirect/Get pattern, session
 * attributes, {@code NumberFormatException} handling, custom exceptions,
 * {@code java.net.URI} parsing.</p>
 */
@WebServlet("/save-job")
public class SaveJobServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final transient JobDAO jobDAO = new JobDAO();
    private final transient ApplicationDAO applicationDAO = new ApplicationDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        try {
            Job job = PageSupport.findJob(jobDAO, request.getParameter("jobId"));
            String action = request.getParameter("action");
            if ("remove".equals(action)) {
                applicationDAO.removeBookmark(user.getId(), job.getId());
                session.setAttribute(PageSupport.FLASH, "Removed from My jobs.");
            } else if (!"save".equals(action)) {
                session.setAttribute(PageSupport.FLASH_ERROR,
                        "That action isn't available. Please use the buttons on this page.");
            } else {
                applicationDAO.saveJob(user.getId(), job.getId());
                session.setAttribute(PageSupport.FLASH, "Saved " + job.getTitle() + " at " + job.getCompany() + ".");
            }
        } catch (AppException e) {
            session.setAttribute(PageSupport.FLASH_ERROR, e.getUserMessage());
        }
        response.sendRedirect(backUrl(request));
    }

    /** Someone typing /save-job into the address bar just goes to the job list. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/jobs");
    }

    /**
     * The page the form was posted from, but only if it is on this server and inside this
     * application; anything else falls back to the job list.
     */
    private String backUrl(HttpServletRequest request) {
        String fallback = request.getContextPath() + "/jobs";
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return fallback;
        }
        try {
            URI uri = new URI(referer);
            String scheme = uri.getScheme();
            boolean webScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            boolean sameHost = request.getServerName().equalsIgnoreCase(uri.getHost());
            String path = uri.getRawPath();
            // "//other-site" would be read by the browser as a different host
            boolean insideApp = path != null && !path.startsWith("//")
                    && path.startsWith(request.getContextPath() + "/");
            if (webScheme && sameHost && insideApp) {
                return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
            }
        } catch (URISyntaxException e) {
            // a malformed header is treated like a missing one
        }
        return fallback;
    }
}
