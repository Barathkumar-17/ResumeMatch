package com.resumematch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of comparing one resume against one job: the match score (0-100, required
 * skills only), the required skills the resume has, the required skills it lacks, and
 * the preferred skills it lacks.
 *
 * <p>Demonstrates: composition (a JobMatch has a Job), {@code List<String>} / {@code ArrayList}.</p>
 */
public class JobMatch {

    /** A score at or above this is a strong match (shown in green). */
    public static final int HIGH_SCORE = 75;

    /** A score at or above this, and below {@link #HIGH_SCORE}, is a fair match (amber). */
    public static final int MID_SCORE = 50;

    private Job job;
    private int score;
    private List<String> matched = new ArrayList<>();
    private List<String> missing = new ArrayList<>();
    private List<String> missingPreferred = new ArrayList<>();

    public JobMatch() {
    }

    public JobMatch(Job job, int score, List<String> matched, List<String> missing,
                    List<String> missingPreferred) {
        this.job = job;
        this.score = score;
        this.matched = (matched == null) ? new ArrayList<>() : matched;
        this.missing = (missing == null) ? new ArrayList<>() : missing;
        this.missingPreferred = (missingPreferred == null) ? new ArrayList<>() : missingPreferred;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public int getScore() {
        return score;
    }

    /** Colour band for a 0-100 score: {@code hi} (green), {@code mid} (amber) or {@code lo} (red). */
    public static String levelFor(int score) {
        if (score >= HIGH_SCORE) {
            return "hi";
        }
        return (score >= MID_SCORE) ? "mid" : "lo";
    }

    /** Colour band of this match's score, see {@link #levelFor(int)}. */
    public String getLevel() {
        return levelFor(score);
    }

    public void setScore(int score) {
        this.score = score;
    }

    /** Never null. */
    public List<String> getMatched() {
        return matched;
    }

    public void setMatched(List<String> matched) {
        this.matched = (matched == null) ? new ArrayList<>() : matched;
    }

    /** Never null. */
    public List<String> getMissing() {
        return missing;
    }

    public void setMissing(List<String> missing) {
        this.missing = (missing == null) ? new ArrayList<>() : missing;
    }

    /** Never null. */
    public List<String> getMissingPreferred() {
        return missingPreferred;
    }

    public void setMissingPreferred(List<String> missingPreferred) {
        this.missingPreferred = (missingPreferred == null) ? new ArrayList<>() : missingPreferred;
    }

    @Override
    public String toString() {
        return "JobMatch{job=" + (job == null ? "null" : job.getId() + " " + job.getTitle())
                + ", score=" + score + "%, matched=" + matched + ", missing=" + missing
                + ", missingPreferred=" + missingPreferred + "}";
    }
}
