package com.resumematch.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An uploaded resume: the stored file, its full text, the score out of 100, the set of
 * skills found in it, the extracted {@link ResumeInfo} and the five score rows.
 *
 * <p>Demonstrates: encapsulation, composition (a Resume has a ResumeInfo and a list of
 * ScoreBreakdown), {@code HashSet<String>} for skills, {@code java.time.LocalDateTime}.</p>
 */
public class Resume {

    private int id;
    private int userId;
    private String fileName;
    private String filePath;
    private String resumeText;
    private int score;
    private LocalDateTime uploadedAt;
    private Set<String> skills = new HashSet<>();
    private ResumeInfo info;
    private List<ScoreBreakdown> breakdown = new ArrayList<>();

    public Resume() {
    }

    public Resume(int id, int userId, String fileName, String filePath, String resumeText,
                  int score, LocalDateTime uploadedAt) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.resumeText = resumeText;
        this.score = score;
        this.uploadedAt = uploadedAt;
    }

    public Resume(int id, int userId, String fileName, String filePath, String resumeText,
                  int score, LocalDateTime uploadedAt, Set<String> skills, ResumeInfo info,
                  List<ScoreBreakdown> breakdown) {
        this(id, userId, fileName, filePath, resumeText, score, uploadedAt);
        this.skills = (skills == null) ? new HashSet<>() : skills;
        this.info = info;
        this.breakdown = (breakdown == null) ? new ArrayList<>() : breakdown;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    /** Never null. A {@code HashSet}, so {@code contains} is O(1) during matching. */
    public Set<String> getSkills() {
        return skills;
    }

    public void setSkills(Set<String> skills) {
        this.skills = (skills == null) ? new HashSet<>() : skills;
    }

    public ResumeInfo getInfo() {
        return info;
    }

    public void setInfo(ResumeInfo info) {
        this.info = info;
    }

    /** Never null. */
    public List<ScoreBreakdown> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(List<ScoreBreakdown> breakdown) {
        this.breakdown = (breakdown == null) ? new ArrayList<>() : breakdown;
    }

    /** The resume text is left out because it can be several kilobytes long. */
    @Override
    public String toString() {
        return "Resume{id=" + id + ", userId=" + userId + ", fileName='" + fileName
                + "', score=" + score + ", uploadedAt=" + uploadedAt + ", skills=" + skills + "}";
    }
}
