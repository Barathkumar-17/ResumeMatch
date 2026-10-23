package com.resumematch.web;

import com.resumematch.dao.ApplicationDAO;
import com.resumematch.dao.JobDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.InvalidJobException;
import com.resumematch.model.Job;
import com.resumematch.model.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Small helpers shared by the servlets so the same few lines are not copied into each of
 * them: parsing and loading a job from a request parameter, moving the one-shot flash
 * messages from the session to the request, and the job count shown in the sidebar.
 *
 * <p>Demonstrates: a final utility class with static methods, {@code NumberFormatException}
 * handling, throwing a custom exception, session attributes used as one-shot messages.</p>
 */
final class PageSupport {

    /** Session attribute holding a one-time success message for the next page. */
    static final String FLASH = "flash";

    /** Session attribute holding a one-time error message for the next page. */
    static final String FLASH_ERROR = "flashError";

    private PageSupport() {
        // utility class, no instances
    }

    /**
     * Parses a job id and loads the job.
     *
     * @throws InvalidJobException if the id is missing, not a number or not in the database
     * @throws AppException        if the database cannot be reached
     */
    static Job findJob(JobDAO jobDAO, String idParam) throws AppException {
        int id;
        try {
            id = Integer.parseInt(idParam == null ? "" : idParam.trim());
        } catch (NumberFormatException e) {
            throw new InvalidJobException();
        }
        Job job = jobDAO.findById(id);
        if (job == null) {
            throw new InvalidJobException();
        }
        return job;
    }

    /**
     * Copies the one-shot messages from the session to the request and forgets them, so each
     * is shown once. The success message becomes the request attribute {@code flash}; the
     * error message becomes the attribute named by {@code errorAttribute}.
     */
    static void moveFlash(HttpServletRequest request, String errorAttribute) {
        HttpSession session = request.getSession();
        Object message = session.getAttribute(FLASH);
        if (message != null) {
            session.removeAttribute(FLASH);
            request.setAttribute(FLASH, message);
        }
        Object error = session.getAttribute(FLASH_ERROR);
        if (error != null) {
            session.removeAttribute(FLASH_ERROR);
            request.setAttribute(errorAttribute, error);
        }
    }

    /**
     * Sets {@code myJobsCount}, the number next to "My jobs" in the sidebar (saved plus
     * applied jobs). If the database cannot answer the number is left out; the sidebar then
     * shows no count and the page itself reports the problem.
     */
    static void loadMyJobsCount(HttpServletRequest request, User user) {
        if (user == null) {
            return;
        }
        try {
            request.setAttribute("myJobsCount", new ApplicationDAO().countMyJobs(user.getId()));
        } catch (AppException e) {
            // already logged by the DAO; the sidebar simply shows no number
        }
    }
}
