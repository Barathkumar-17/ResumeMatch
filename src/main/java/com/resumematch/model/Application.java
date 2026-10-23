package com.resumematch.model;

import java.time.LocalDate;

/**
 * A job the user has applied to (the {@code applications} table), with its current
 * status: Applied, Interview, Rejected or Selected.
 *
 * <p>Demonstrates: encapsulation, {@code java.time.LocalDate}.</p>
 */
public class Application {

    private int id;
    private int userId;
    private int jobId;
    private String status;
    private LocalDate date;

    public Application() {
    }

    public Application(int id, int userId, int jobId, String status, LocalDate date) {
        this.id = id;
        this.userId = userId;
        this.jobId = jobId;
        this.status = status;
        this.date = date;
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

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** The day the user applied ({@code applied_at}). */
    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Application{id=" + id + ", userId=" + userId + ", jobId=" + jobId
                + ", status='" + status + "', date=" + date + "}";
    }
}
