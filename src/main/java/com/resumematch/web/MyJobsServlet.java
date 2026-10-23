package com.resumematch.web;

import com.resumematch.dao.ApplicationDAO;
import com.resumematch.dao.JobDAO;
import com.resumematch.dao.ResumeDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.Application;
import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.JobMatcher;
import com.resumematch.service.SkillBasedJobMatcher;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Shows the My jobs page: the jobs the user saved and the ones they applied to, with a
 * count for every status. Each table row pairs the {@link Job} with its
 * {@link Application} and the match score against the latest resume, all worked out here
 * so the JSP only displays it.
 *
 * <p>Request attributes for {@code my-jobs.jsp}:</p>
 * <ul>
 *   <li>{@code saved}, {@code applied} - {@code List<Application>} straight from the DAO</li>
 *   <li>{@code savedRows}, {@code appliedRows} - {@code List<MyJobsServlet.Row>} ready to show</li>
 *   <li>{@code savedCount}, {@code appliedCount}, {@code myJobsCount} - Integer</li>
 *   <li>{@code statusCounts} - {@code Map<String,Integer>} for Saved, Applied, Interview,
 *       Rejected, Selected in that order</li>
 *   <li>{@code tab} - "saved" or "applied"; {@code loadFailed} - Boolean</li>
 *   <li>{@code flash}, {@code error} - one-shot messages (taken from the session)</li>
 * </ul>
 *
 * <p>Demonstrates: {@code HttpServlet} doGet, {@code ArrayList}, {@code LinkedHashMap}
 * (keeps the pipeline order), a static nested class, the {@link JobMatcher} interface type,
 * {@code DateTimeFormatter}, one-shot session attributes.</p>
 */
@WebServlet("/my-jobs")
public class MyJobsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final transient ApplicationDAO applicationDAO = new ApplicationDAO();
    private final transient JobDAO jobDAO = new JobDAO();
    private final transient ResumeDAO resumeDAO = new ResumeDAO();
    private final transient JobMatcher matcher = new SkillBasedJobMatcher();

    /**
     * One line of the My jobs table: the job, the user's saved/applied entry for it and
     * the match score text and colour class.
     *
     * <p>Demonstrates: static nested class, immutable object with getters for EL.</p>
     */
    public static final class Row {
        private final Job job;
        private final Application application;
        private final String dateText;
        private final String matchText;
        private final String matchClass;

        Row(Job job, Application application, String dateText, String matchText, String matchClass) {
            this.job = job;
            this.application = application;
            this.dateText = dateText;
            this.matchText = matchText;
            this.matchClass = matchClass;
        }

        public Job getJob() {
            return job;
        }

        public Application getApplication() {
            return application;
        }

        /** The application status, or "Saved" for a bookmarked job. */
        public String getStatus() {
            return application.getStatus();
        }

        /** Date saved or applied, e.g. "22 Oct 2026". */
        public String getDateText() {
            return dateText;
        }

        /** Match score such as "82%", or an em dash when there is no resume yet. */
        public String getMatchText() {
            return matchText;
        }

        /** pct-hi, pct-mid, pct-lo or pct-na. */
        public String getMatchClass() {
            return matchClass;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        request.setAttribute("activePage", "my-jobs");
        request.setAttribute("tab", "applied".equals(request.getParameter("tab")) ? "applied" : "saved");
        moveFlash(session, "flash", request, "flash");
        moveFlash(session, "flashError", request, "error");

        List<Application> saved = Collections.emptyList();
        List<Application> applied = Collections.emptyList();
        List<Row> savedRows = new ArrayList<>();
        List<Row> appliedRows = new ArrayList<>();
        boolean loadFailed = false;
        try {
            saved = applicationDAO.findSaved(user.getId());
            applied = applicationDAO.findApplications(user.getId());
            Resume latest = resumeDAO.findLatestByUser(user.getId());
            Set<String> resumeSkills = latest == null ? null : latest.getSkills();
            savedRows = buildRows(saved, resumeSkills);
            appliedRows = buildRows(applied, resumeSkills);
        } catch (AppException e) {
            loadFailed = true;
            request.setAttribute("error", e.getUserMessage());
        }

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put(ApplicationDAO.STATUS_SAVED, savedRows.size());
        for (String status : ApplicationServlet.VALID_STATUSES) {
            statusCounts.put(status, 0);
        }
        for (Row row : appliedRows) {
            statusCounts.merge(row.getStatus(), 1, Integer::sum);
        }

        request.setAttribute("saved", saved);
        request.setAttribute("applied", applied);
        request.setAttribute("savedRows", savedRows);
        request.setAttribute("appliedRows", appliedRows);
        request.setAttribute("savedCount", savedRows.size());
        request.setAttribute("appliedCount", appliedRows.size());
        request.setAttribute("myJobsCount", savedRows.size() + appliedRows.size());
        request.setAttribute("statusCounts", statusCounts);
        request.setAttribute("loadFailed", loadFailed);
        request.getRequestDispatcher("/WEB-INF/views/my-jobs.jsp").forward(request, response);
    }

    /** Pairs each entry with its job; a job deleted in the meantime is skipped. */
    private List<Row> buildRows(List<Application> entries, Set<String> resumeSkills) throws AppException {
        List<Row> rows = new ArrayList<>();
        for (Application entry : entries) {
            Job job = jobDAO.findById(entry.getJobId());
            if (job == null) {
                continue;
            }
            String date = entry.getDate() == null ? "" : entry.getDate().format(DATE_FORMAT);
            if (resumeSkills == null) {
                rows.add(new Row(job, entry, date, "—", "pct-na"));
            } else {
                int score = matcher.calculateMatchScore(resumeSkills, job);
                rows.add(new Row(job, entry, date, score + "%", matchClass(score)));
            }
        }
        return rows;
    }

    /** Same colour bands as the job cards: 75+ green, 50+ amber, below that red. */
    private static String matchClass(int score) {
        return "pct-" + JobMatch.levelFor(score);
    }

    /** Copies a one-shot message from the session to the request and forgets it. */
    private static void moveFlash(HttpSession session, String sessionName,
                                  HttpServletRequest request, String requestName) {
        Object message = session.getAttribute(sessionName);
        if (message != null) {
            session.removeAttribute(sessionName);
            request.setAttribute(requestName, message);
        }
    }
}
