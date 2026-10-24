package com.resumematch.web;

import com.resumematch.dao.ApplicationDAO;
import com.resumematch.dao.JobDAO;
import com.resumematch.dao.ResumeDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.InvalidJobException;
import com.resumematch.model.Application;
import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.SkillBasedJobMatcher;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * One job in full (URL /job?id=101): its details, the required and preferred skills, and
 * how the user's latest resume compares. The numbers for the worked calculation
 * ("4 ÷ 5 × 100 = 80%") are prepared here so the page only prints them. A missing,
 * non-numeric or unknown id shows "This job no longer exists." instead of an error page.
 *
 * <p>Request attributes for job-detail.jsp: {@code job} (Job), {@code match} (JobMatch,
 * null without a resume), {@code hasResume}, {@code matchedCount}, {@code requiredCount},
 * {@code missingCount}, {@code score}, {@code ringClass}, {@code saved}, {@code applied},
 * {@code savedJobIds} (Set&lt;Integer&gt;), {@code error}, {@code flash},
 * {@code flashError}.</p>
 *
 * <p>Demonstrates: {@code HttpServlet} doGet, parsing a query parameter with
 * {@code NumberFormatException} handling, throwing and catching a custom exception
 * ({@link InvalidJobException}), the {@code JobMatch} composition object.</p>
 */
@WebServlet("/job")
public class JobDetailServlet extends HttpServlet {

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
        Set<Integer> savedJobIds = new HashSet<>();

        try {
            Job job = PageSupport.findJob(jobDAO, request.getParameter("id"));
            request.setAttribute("job", job);

            Resume resume = (user == null) ? null : resumeDAO.findLatestSummaryByUser(user.getId());
            request.setAttribute("hasResume", resume != null);
            if (resume != null) {
                JobMatch match = matcher.match(resume.getSkills(), job);
                request.setAttribute("match", match);
                request.setAttribute("matchedCount", match.getMatched().size());
                request.setAttribute("requiredCount", job.getRequiredSkills().size());
                request.setAttribute("missingCount",
                        match.getMissing().size() + match.getMissingPreferred().size());
                request.setAttribute("score", match.getScore());
                request.setAttribute("ringClass", match.getLevel());
            }

            boolean saved = false;
            boolean applied = false;
            if (user != null) {
                saved = applicationDAO.isSaved(user.getId(), job.getId());
                applied = hasApplied(user.getId(), job.getId());
                if (saved) {
                    savedJobIds.add(job.getId());
                }
            }
            request.setAttribute("saved", saved);
            request.setAttribute("applied", applied);
        } catch (InvalidJobException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            request.setAttribute("error", e.getUserMessage());
        } catch (AppException e) {
            request.setAttribute("error", e.getUserMessage());
        }

        request.setAttribute("savedJobIds", savedJobIds);
        request.setAttribute("activePage", "jobs");
        PageSupport.moveFlash(request, "flashError");
        PageSupport.loadMyJobsCount(request, user);
        request.getRequestDispatcher("/WEB-INF/views/job-detail.jsp").forward(request, response);
    }

    /** True if the user has marked this job as applied (any status after that). */
    private boolean hasApplied(int userId, int jobId) throws AppException {
        for (Application a : applicationDAO.findApplications(userId)) {
            if (a.getJobId() == jobId) {
                return true;
            }
        }
        return false;
    }
}
