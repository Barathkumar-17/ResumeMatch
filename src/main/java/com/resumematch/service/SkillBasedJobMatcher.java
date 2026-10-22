package com.resumematch.service;

import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Matches a resume to a job by counting skills: the score is the share of the job's
 * REQUIRED skills that the resume has. Preferred skills are reported separately and never
 * change the score. Skill names are compared exactly, so the resume's skills must be the
 * canonical names produced by {@link ResumeAnalyzer#extractSkills(String)}.
 *
 * <p>Demonstrates: interface implementation ({@code implements JobMatcher}),
 * {@code @Override}, {@code HashSet} lookups ({@code contains} is O(1)),
 * {@code ArrayList}, composition (builds {@link JobMatch} objects).</p>
 */
public class SkillBasedJobMatcher implements JobMatcher {

    @Override
    public int calculateMatchScore(Set<String> resumeSkills, Job job) {
        List<String> required = job.getRequiredSkills();
        if (required.isEmpty()) {
            return 0;
        }
        int matched = findMatchingSkills(resumeSkills, job).size();
        return Math.round(matched * 100f / required.size());
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
