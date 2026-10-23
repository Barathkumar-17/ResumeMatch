package com.resumematch.web;

import com.resumematch.dao.ApplicationDAO;
import com.resumematch.dao.JobDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.InvalidJobException;
import com.resumematch.model.Job;
import com.resumematch.model.User;

import java.io.IOException;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Handles the buttons on the My jobs page. Every form posts {@code jobId} and
 * {@code action}:
 * <ul>
 *   <li>{@code apply} - moves a saved job to the Applied tab with status Applied</li>
 *   <li>{@code status} - changes an application's status to the {@code status} parameter</li>
 *   <li>{@code remove} - deletes the job from the list (bookmark and application)</li>
 * </ul>
 * Then it redirects back to {@code /my-jobs}, leaving a one-shot {@code flash} (or
 * {@code flashError}) message in the session.
 *
 * <p>Demonstrates: {@code HttpServlet} doPost, switch on a String, input validation with
 * an immutable {@code List}, custom exceptions, the Post/Redirect/Get pattern.</p>
 */
@WebServlet("/application")
public class ApplicationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** The statuses the applications table accepts, in pipeline order. */
    public static final List<String> VALID_STATUSES =
            List.of("Applied", "Interview", "Rejected", "Selected");

    private static final String INVALID_STATUS_MESSAGE =
            "Choose a status: Applied, Interview, Rejected or Selected.";
    private static final String NOT_APPLIED_MESSAGE =
            "You haven't marked that job as applied yet, so its status can't be changed.";
    private static final String INVALID_ACTION_MESSAGE =
            "That action isn't available. Please use the buttons on this page.";

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

        String action = request.getParameter("action");
        String tab = "applied";
        try {
            Job job = findJob(request.getParameter("jobId"));
            switch (action == null ? "" : action) {
                case "apply":
                    applicationDAO.markApplied(user.getId(), job.getId());
                    session.setAttribute("flash", "Marked " + job.getTitle() + " as applied.");
                    break;
                case "status":
                    String status = request.getParameter("status");
                    if (!isValidStatus(status)) {
                        session.setAttribute("flashError", INVALID_STATUS_MESSAGE);
                        break;
                    }
                    if (applicationDAO.updateStatus(user.getId(), job.getId(), status)) {
                        session.setAttribute("flash", "Status changed to " + status + ".");
                    } else {
                        session.setAttribute("flashError", NOT_APPLIED_MESSAGE);
                    }
                    break;
                case "remove":
                    applicationDAO.removeSavedJob(user.getId(), job.getId());
                    session.setAttribute("flash", "Removed from My jobs.");
                    tab = request.getParameter("tab");
                    break;
                default:
                    session.setAttribute("flashError", INVALID_ACTION_MESSAGE);
                    tab = null;
                    break;
            }
        } catch (AppException e) {
            session.setAttribute("flashError", e.getUserMessage());
            tab = request.getParameter("tab");
        }

        String target = request.getContextPath() + "/my-jobs";
        if ("applied".equals(tab)) {
            target += "?tab=applied";
        }
        response.sendRedirect(target);
    }

    /**
     * True when the value is exactly one of the four statuses. A missing parameter is
     * null, and {@code List.of(...).contains(null)} throws, so null is checked first.
     */
    public static boolean isValidStatus(String status) {
        return status != null && VALID_STATUSES.contains(status);
    }

    /** Opening /application directly just shows the My jobs page. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/my-jobs");
    }

    /** Parses the job id and checks that the job still exists. */
    private Job findJob(String jobIdParam) throws AppException {
        int jobId;
        try {
            jobId = Integer.parseInt(jobIdParam == null ? "" : jobIdParam.trim());
        } catch (NumberFormatException e) {
            throw new InvalidJobException();
        }
        Job job = jobDAO.findById(jobId);
        if (job == null) {
            throw new InvalidJobException();
        }
        return job;
    }
}
