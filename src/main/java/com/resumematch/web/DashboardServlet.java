package com.resumematch.web;

import com.resumematch.dao.ApplicationDAO;
import com.resumematch.dao.JobDAO;
import com.resumematch.dao.ResumeDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.SkillBasedJobMatcher;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The user's home page ({@code GET /dashboard}): the stat strip (latest resume score,
 * skills extracted, jobs at 50%+ match, saved or applied jobs), the upload area, the
 * recent analyses and the three best job matches. All numbers are worked out here and
 * handed to {@code dashboard.jsp} as request attributes; the page only displays them.
 *
 * <p>Demonstrates: Servlets ({@code @WebServlet}, {@code doGet}, request attributes,
 * {@code RequestDispatcher.forward}), using the DAO layer instead of SQL, composition
 * ({@link SkillBasedJobMatcher} ranks the jobs), {@code ArrayList},
 * {@code HashMap}, {@code java.time} formatting, catching a custom exception and showing
 * only its friendly message.</p>
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** The page both this servlet and the upload servlet forward to. */
    static final String VIEW = "/WEB-INF/views/dashboard.jsp";

    /** A job counts as a good fit from this match score upwards. */
    static final int GOOD_MATCH = JobMatch.MID_SCORE;

    /** How many earlier analyses and best matches are listed. */
    private static final int RECENT_LIMIT = 4;
    private static final int TOP_MATCHES = 3;

    /** Dates like "22 Oct 2026". */
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        User user = (User) request.getSession().getAttribute("user");
        try {
            loadDashboard(request, user);
        } catch (AppException e) {
            request.setAttribute("error", e.getUserMessage());
        }
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /**
     * Puts everything {@code dashboard.jsp} shows into request attributes. The upload
     * servlet calls this too, so a rejected upload re-shows the full dashboard with its
     * message. Defaults are set first, so the page still renders if the database fails.
     *
     * @throws AppException when the database cannot be reached
     */
    static void loadDashboard(HttpServletRequest request, User user) throws AppException {
        request.setAttribute("activePage", "dashboard");
        request.setAttribute("firstName", firstName(user == null ? null : user.getName()));
        request.setAttribute("recentAnalyses", new ArrayList<Map<String, Object>>());
        request.setAttribute("topMatches", new ArrayList<JobMatch>());
        request.setAttribute("savedCount", 0);
        if (user == null) {
            return;
        }

        ApplicationDAO applicationDAO = new ApplicationDAO();
        int savedCount = applicationDAO.findSaved(user.getId()).size()
                + applicationDAO.findApplications(user.getId()).size();
        request.setAttribute("savedCount", savedCount);
        request.setAttribute("myJobsCount", savedCount);

        ResumeDAO resumeDAO = new ResumeDAO();
        List<Resume> resumes = resumeDAO.findAllByUser(user.getId()); // newest first
        if (resumes.isEmpty()) {
            return;
        }
        Resume latest = resumes.get(0);
        request.setAttribute("resume", latest);
        request.setAttribute("skillCount", latest.getSkills().size());

        // Rank every job against the latest resume's skills, best first.
        List<Job> jobs = new JobDAO().findAll();
        List<JobMatch> ranked = new SkillBasedJobMatcher().rankJobs(latest.getSkills(), jobs);
        int goodMatches = 0;
        for (JobMatch match : ranked) {
            if (match.getScore() >= GOOD_MATCH) {
                goodMatches++;
            }
        }
        request.setAttribute("jobCount", jobs.size());
        request.setAttribute("goodMatchCount", goodMatches);
        request.setAttribute("topMatches",
                new ArrayList<>(ranked.subList(0, Math.min(TOP_MATCHES, ranked.size()))));

        List<Map<String, Object>> recent = new ArrayList<>();
        for (Resume resume : resumes.subList(0, Math.min(RECENT_LIMIT, resumes.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("fileName", resume.getFileName());
            row.put("date", formatDate(resume.getUploadedAt()));
            row.put("skillCount", resume.getSkills().size());
            row.put("score", resume.getScore());
            row.put("ringClass", ringClass(resume.getScore()));
            row.put("latest", resume == latest);
            recent.add(row);
        }
        request.setAttribute("recentAnalyses", recent);
    }

    /** "Arun Kumar" becomes "Arun"; an empty name becomes "there" ("Hi, there"). */
    static String firstName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "there";
        }
        return name.trim().split("\\s+")[0];
    }

    /** "22 Oct 2026", or an empty string when the date is unknown. */
    static String formatDate(LocalDateTime dateTime) {
        return (dateTime == null) ? "" : DATE_FORMAT.format(dateTime);
    }

    /** Ring colour class: green from 75, amber from 50, red below. */
    static String ringClass(int score) {
        return JobMatch.levelFor(score);
    }
}
