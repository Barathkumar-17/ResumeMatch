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
import com.resumematch.service.SkillBasedJobMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * The "Find jobs" page. Reads the keyword and filter values from the query string, lets
 * {@link JobDAO#search} apply the keyword, location, job type and experience filters in
 * SQL, then scores every job against the student's latest resume, sorts best match first
 * and drops jobs below the minimum-match slider. Without a resume the jobs are listed
 * unranked and the page asks the student to upload one.
 *
 * <p>Request attributes for jobs.jsp: {@code matches} (List&lt;JobMatch&gt;),
 * {@code resultCount}, {@code hasResume}, {@code savedJobIds} (Set&lt;Integer&gt;),
 * the echoed filters {@code q}, {@code location}, {@code type}, {@code exp},
 * {@code minMatch}, the dropdown options {@code locations}, {@code jobTypes},
 * {@code experienceLevels}, and {@code error}, {@code flash}, {@code flashError}.</p>
 *
 * <p>Demonstrates: {@code HttpServlet} doGet, request parameters, {@code ArrayList} and
 * {@code TreeSet} (sorted, de-duplicated dropdown values), {@code HashSet<Integer>},
 * filtering a list after sorting, custom exception handling.</p>
 */
@WebServlet("/jobs")
public class JobSearchServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final transient JobDAO jobDAO = new JobDAO();
    private final transient ResumeDAO resumeDAO = new ResumeDAO();
    private final transient ApplicationDAO applicationDAO = new ApplicationDAO();
    private final transient SkillBasedJobMatcher matcher = new SkillBasedJobMatcher();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        User user = (User) request.getSession().getAttribute("user");

        String q = clean(request.getParameter("q"));
        String location = clean(request.getParameter("location"));
        String type = clean(request.getParameter("type"));
        String exp = clean(request.getParameter("exp"));
        int minMatch = parsePercent(request.getParameter("minMatch"));

        List<JobMatch> matches = new ArrayList<>();
        Set<String> locations = new TreeSet<>();
        Set<String> jobTypes = new TreeSet<>();
        Set<String> experienceLevels = new TreeSet<>();
        Set<Integer> savedJobIds = new HashSet<>();
        boolean hasResume = false;

        try {
            for (Job job : jobDAO.findAll()) {
                addIfPresent(locations, job.getLocation());
                addIfPresent(jobTypes, job.getJobType());
                addIfPresent(experienceLevels, job.getExperience());
            }

            List<Job> found = jobDAO.search(q, location, type, exp);
            Resume resume = (user == null) ? null : resumeDAO.findLatestByUser(user.getId());
            hasResume = resume != null;

            if (hasResume) {
                for (JobMatch m : matcher.rankJobs(resume.getSkills(), found)) {
                    if (m.getScore() >= minMatch) {
                        matches.add(m);
                    }
                }
            } else {
                // No resume: keep the database order and show no scores.
                Set<String> noSkills = new HashSet<>();
                for (Job job : found) {
                    matches.add(matcher.match(noSkills, job));
                }
            }

            if (user != null) {
                savedJobIds = findSavedJobIds(user.getId());
            }
        } catch (AppException e) {
            request.setAttribute("error", e.getUserMessage());
        }

        request.setAttribute("q", q);
        request.setAttribute("location", location);
        request.setAttribute("type", type);
        request.setAttribute("exp", exp);
        request.setAttribute("minMatch", minMatch);
        request.setAttribute("locations", locations);
        request.setAttribute("jobTypes", jobTypes);
        request.setAttribute("experienceLevels", experienceLevels);
        request.setAttribute("matches", matches);
        request.setAttribute("resultCount", matches.size());
        request.setAttribute("hasResume", hasResume);
        request.setAttribute("savedJobIds", savedJobIds);
        request.setAttribute("activePage", "jobs");
        moveFlashToRequest(request);
        request.getRequestDispatcher("/WEB-INF/views/jobs.jsp").forward(request, response);
    }

    /** Ids of every job on the student's list, saved or applied (same rule as isSaved). */
    private Set<Integer> findSavedJobIds(int userId) throws AppException {
        Set<Integer> ids = new HashSet<>();
        for (Application a : applicationDAO.findSaved(userId)) {
            ids.add(a.getJobId());
        }
        for (Application a : applicationDAO.findApplications(userId)) {
            ids.add(a.getJobId());
        }
        return ids;
    }

    /** Shows the one-time message left by the save form, then forgets it. */
    private static void moveFlashToRequest(HttpServletRequest request) {
        HttpSession session = request.getSession();
        for (String key : new String[] {"flash", "flashError"}) {
            Object message = session.getAttribute(key);
            if (message != null) {
                request.setAttribute(key, message);
                session.removeAttribute(key);
            }
        }
    }

    private static void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    /** Trims a parameter; a missing parameter becomes an empty string. */
    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** The slider value 0-100; anything missing or invalid means 0 (no minimum). */
    private static int parsePercent(String value) {
        try {
            int p = Integer.parseInt(clean(value));
            return Math.max(0, Math.min(100, p));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
