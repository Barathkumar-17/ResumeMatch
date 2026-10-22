package com.resumematch.service;

import com.resumematch.model.Job;

import java.util.List;
import java.util.Set;

/**
 * Compares the skills found in a resume with the skills a job requires. Callers hold a
 * {@code JobMatcher} reference, never the implementing class, so the matching rule can be
 * swapped without touching them:
 * <pre>
 *   JobMatcher matcher = new SkillBasedJobMatcher();
 * </pre>
 *
 * <p>Demonstrates: interfaces (abstraction), polymorphism, {@code Set<String>} and
 * {@code List<String>} as parameter and return types.</p>
 */
public interface JobMatcher {

    /**
     * Match score from 0 to 100: matched required skills / total required skills x 100,
     * rounded. 0 when the job lists no required skills. Preferred skills never count.
     */
    int calculateMatchScore(Set<String> resumeSkills, Job job);

    /** The job's required skills that the resume has, in the job's order. */
    List<String> findMatchingSkills(Set<String> resumeSkills, Job job);

    /** The job's required skills that the resume lacks, in the job's order. */
    List<String> findMissingSkills(Set<String> resumeSkills, Job job);
}
