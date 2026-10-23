package com.resumematch.service;

import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matches a resume to a job by counting skills: the score is the share of the job's
 * REQUIRED skills that the resume has. Preferred skills are reported separately and never
 * change the score. Skill names are compared exactly, so the resume's skills must be the
 * canonical names produced by {@link ResumeAnalyzer#extractSkills(String)}.
 *
 * <p>Demonstrates: interface implementation ({@code implements JobMatcher}),
 * {@code @Override}, {@code HashSet} lookups ({@code contains} is O(1)),
 * {@code ArrayList<JobMatch>} sorted with a {@code Comparator} (ranking),
 * {@code HashMap<String, Integer>} (counting how often each missing skill is demanded),
 * composition (builds {@link JobMatch} objects).</p>
 */
public class SkillBasedJobMatcher implements JobMatcher {

    /** How many skills {@link #recommendSkills} returns at most. */
    public static final int MAX_RECOMMENDATIONS = 5;

    /** Ranking order: match score high to low, then job title A to Z (ignoring case). */
    public static final Comparator<JobMatch> BEST_MATCH_FIRST =
            Comparator.comparingInt(JobMatch::getScore).reversed()
                    .thenComparing(m -> m.getJob().getTitle(), String.CASE_INSENSITIVE_ORDER);

    @Override
    public int calculateMatchScore(Set<String> resumeSkills, Job job) {
        List<String> required = job.getRequiredSkills();
        if (required.isEmpty()) {
            return 0;
        }
        int matched = findMatchingSkills(resumeSkills, job).size();
        return percentOf(matched, required.size());
    }

    /**
     * {@code part / whole x 100}, rounded half up, or 0 when {@code whole} is 0. Every
     * percentage in the application (match score, skill gap, learning bars) uses this one
     * rule, so the same fraction never shows two different numbers.
     */
    public static int percentOf(int part, int whole) {
        return (whole <= 0) ? 0 : Math.round(part * 100f / whole);
    }

    @Override
    public List<String> findMatchingSkills(Set<String> resumeSkills, Job job) {
        return filter(job.getRequiredSkills(), resumeSkills, true);
    }

    @Override
    public List<String> findMissingSkills(Set<String> resumeSkills, Job job) {
        return filter(job.getRequiredSkills(), resumeSkills, false);
    }

    /** The job's preferred skills that the resume lacks. Does not affect the score. */
    public List<String> findMissingPreferred(Set<String> resumeSkills, Job job) {
        return filter(job.getPreferredSkills(), resumeSkills, false);
    }

    /** Builds the full result for one job: score, matched, missing and missing preferred. */
    public JobMatch match(Set<String> resumeSkills, Job job) {
        return new JobMatch(job,
                calculateMatchScore(resumeSkills, job),
                findMatchingSkills(resumeSkills, job),
                findMissingSkills(resumeSkills, job),
                findMissingPreferred(resumeSkills, job));
    }

    /**
     * Matches the resume against every job and sorts the results best first: match score
     * high to low, then job title A to Z.
     *
     * @return a new {@code ArrayList<JobMatch>}, one entry per job
     */
    public List<JobMatch> rankJobs(Set<String> resumeSkills, List<Job> jobs) {
        List<JobMatch> results = new ArrayList<>();
        if (jobs == null) {
            return results;
        }
        for (Job job : jobs) {
            results.add(match(resumeSkills, job));
        }
        results.sort(BEST_MATCH_FIRST);
        return results;
    }

    /**
     * Skill-gap percentage for one job, counting required AND preferred skills:
     * skills of the job the resume has / all skills the job lists x 100, rounded.
     * This is intentionally a different number from {@link #calculateMatchScore}, which
     * counts required skills only. 0 when the job lists no skills.
     */
    public int calculateSkillGapScore(Set<String> resumeSkills, Job job) {
        List<String> all = new ArrayList<>(job.getRequiredSkills());
        all.addAll(job.getPreferredSkills());
        if (all.isEmpty()) {
            return 0;
        }
        int have = filter(all, resumeSkills, true).size();
        return percentOf(have, all.size());
    }

    /**
     * Skills to learn next. For every job, each missing REQUIRED skill is counted once in a
     * {@code HashMap<String, Integer>}, and that job's match score is added to the skill's
     * weight. Skills are sorted by total weight (high first), then by how many jobs need
     * them, then by name, so skills for jobs the user almost fits come first.
     *
     * @return at most 5 entries: key = skill name, value = number of jobs that require it
     */
    public List<Map.Entry<String, Integer>> recommendSkills(Set<String> resumeSkills, List<Job> jobs) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> weights = new HashMap<>();
        if (jobs != null) {
            for (Job job : jobs) {
                int score = calculateMatchScore(resumeSkills, job);
                for (String skill : findMissingSkills(resumeSkills, job)) {
                    counts.put(skill, counts.getOrDefault(skill, 0) + 1);
                    weights.put(skill, weights.getOrDefault(skill, 0) + score);
                }
            }
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        Comparator<Map.Entry<String, Integer>> byWeight =
                Comparator.comparingInt(e -> weights.get(e.getKey()));
        ranked.sort(byWeight.reversed()
                .thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed())
                .thenComparing(Map.Entry.comparingByKey()));

        return new ArrayList<>(ranked.subList(0, Math.min(MAX_RECOMMENDATIONS, ranked.size())));
    }

    /**
     * Keeps the skills from {@code jobSkills} that the resume has ({@code wanted == true})
     * or lacks ({@code wanted == false}), in the job's order.
     */
    private static List<String> filter(List<String> jobSkills, Set<String> resumeSkills, boolean wanted) {
        List<String> result = new ArrayList<>();
        for (String skill : jobSkills) {
            boolean has = resumeSkills != null && resumeSkills.contains(skill);
            if (has == wanted) {
                result.add(skill);
            }
        }
        return result;
    }
}
