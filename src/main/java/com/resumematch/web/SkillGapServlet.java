package com.resumematch.web;

import com.resumematch.dao.JobDAO;
import com.resumematch.dao.ResumeDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.InvalidJobException;
import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.SkillBasedJobMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The skill gap page (URL /skill-gap?job=101). Compares the user's latest resume with
 * everything one target job lists, required AND preferred, so the percentage here (4 of 6
 * = 67% for job 101) is intentionally different from the job match score (required only,
 * 4 of 5 = 80%). Without a {@code job} parameter the best-matching job is chosen. The
 * right-hand panel recommends the skills to learn next across all jobs.
 *
 * <p>Request attributes for skill-gap.jsp: {@code hasResume}, {@code jobs} (List&lt;Job&gt;
 * for the dropdown), {@code targetJob}, {@code gapScore}, {@code haveCount},
 * {@code totalCount}, {@code matchScore}, {@code matchedRequired},
 * {@code matchedPreferred}, {@code missingRequired}, {@code missingPreferred}
 * (List&lt;String&gt;), {@code ringClass} (hi, mid or lo), {@code nothingMatched},
 * {@code nothingMissing} (Boolean), {@code recommendations} (List&lt;Map&lt;String,Object&gt;&gt;
 * with the keys {@code skill}, {@code jobs} = number of jobs that require it and
 * {@code percent} = that number as a share of all jobs), {@code totalJobs}, {@code error}.</p>
 *
 * <p>Demonstrates: {@code HttpServlet} doGet, {@code List<String>} filtering,
 * {@code Map.Entry} results from a {@code HashMap} count, choosing a default with the
 * ranked {@code ArrayList<JobMatch>}, custom exception handling.</p>
 */
@WebServlet("/skill-gap")
public class SkillGapServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final transient JobDAO jobDAO = new JobDAO();
    private final transient ResumeDAO resumeDAO = new ResumeDAO();
    private final transient SkillBasedJobMatcher matcher = new SkillBasedJobMatcher();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        User user = (User) request.getSession().getAttribute("user");
        request.setAttribute("activePage", "skill-gap");

        try {
            Resume resume = (user == null) ? null : resumeDAO.findLatestByUser(user.getId());
            request.setAttribute("hasResume", resume != null);
            if (resume != null) {
                List<Job> jobs = jobDAO.findAll();
                request.setAttribute("jobs", jobs);
                if (!jobs.isEmpty()) {
                    showGap(request, resume.getSkills(), jobs);
                }
            }
        } catch (AppException e) {
            request.setAttribute("error", e.getUserMessage());
        }

        request.getRequestDispatcher("/WEB-INF/views/skill-gap.jsp").forward(request, response);
    }

    /** Fills in every attribute for the target job and the learning recommendations. */
    private void showGap(HttpServletRequest request, Set<String> skills, List<Job> jobs) {
        Job target;
        try {
            target = pickTarget(request.getParameter("job"), skills, jobs);
        } catch (InvalidJobException e) {
            request.setAttribute("error", e.getUserMessage());
            target = bestMatch(skills, jobs);
        }

        List<String> matchedRequired = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        split(target.getRequiredSkills(), skills, matchedRequired, missingRequired);
        List<String> matchedPreferred = new ArrayList<>();
        List<String> missingPreferred = new ArrayList<>();
        split(target.getPreferredSkills(), skills, matchedPreferred, missingPreferred);

        List<Map.Entry<String, Integer>> recommendations = matcher.recommendSkills(skills, jobs);

        int gapScore = matcher.calculateSkillGapScore(skills, target);
        int haveCount = matchedRequired.size() + matchedPreferred.size();
        request.setAttribute("targetJob", target);
        request.setAttribute("gapScore", gapScore);
        request.setAttribute("ringClass", JobMatch.levelFor(gapScore));
        request.setAttribute("nothingMatched", haveCount == 0);
        request.setAttribute("nothingMissing", missingRequired.isEmpty() && missingPreferred.isEmpty());
        request.setAttribute("haveCount", haveCount);
        request.setAttribute("totalCount",
                target.getRequiredSkills().size() + target.getPreferredSkills().size());
        request.setAttribute("matchScore", matcher.calculateMatchScore(skills, target));
        request.setAttribute("matchedRequired", matchedRequired);
        request.setAttribute("matchedPreferred", matchedPreferred);
        request.setAttribute("missingRequired", missingRequired);
        request.setAttribute("missingPreferred", missingPreferred);
        request.setAttribute("recommendations", recommendationRows(recommendations, jobs.size()));
        request.setAttribute("totalJobs", jobs.size());
    }

    /** One row per recommended skill: its name, the number of jobs needing it and that as a percentage of all jobs. */
    private static List<Map<String, Object>> recommendationRows(
            List<Map.Entry<String, Integer>> recommendations, int totalJobs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recommendations) {
            Map<String, Object> row = new HashMap<>();
            row.put("skill", entry.getKey());
            row.put("jobs", entry.getValue());
            row.put("percent", SkillBasedJobMatcher.percentOf(entry.getValue(), totalJobs));
            rows.add(row);
        }
        return rows;
    }

    /**
     * The job named by the {@code job} parameter, or, when there is none, the job the
     * resume matches best (ties broken by title, like the job list).
     *
     * @throws InvalidJobException if the parameter is present but not a known job id
     */
    private Job pickTarget(String jobParam, Set<String> skills, List<Job> jobs)
            throws InvalidJobException {
        if (jobParam == null || jobParam.isBlank()) {
            return bestMatch(skills, jobs);
        }
        int id;
        try {
            id = Integer.parseInt(jobParam.trim());
        } catch (NumberFormatException e) {
            throw new InvalidJobException();
        }
        for (Job job : jobs) {
            if (job.getId() == id) {
                return job;
            }
        }
        throw new InvalidJobException();
    }

    /** The first job of the ranked list: highest match score, then title A to Z. */
    private Job bestMatch(Set<String> skills, List<Job> jobs) {
        List<JobMatch> ranked = matcher.rankJobs(skills, jobs);
        return ranked.get(0).getJob();
    }

    /** Sorts each skill of the job into "has" or "lacks", keeping the job's order. */
    private static void split(List<String> jobSkills, Set<String> resumeSkills,
                              List<String> has, List<String> lacks) {
        for (String skill : jobSkills) {
            if (resumeSkills.contains(skill)) {
                has.add(skill);
            } else {
                lacks.add(skill);
            }
        }
    }
}
