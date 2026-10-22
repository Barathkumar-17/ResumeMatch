package com.resumematch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A job opening (the {@code jobs} table) together with the names of its REQUIRED and
 * PREFERRED skills from {@code job_skills}. Only the required skills affect the match score.
 *
 * <p>Demonstrates: encapsulation, {@code List<String>} / {@code ArrayList}.</p>
 */
public class Job {

    private int id;
    private String title;
    private String company;
    private String location;
    private String jobType;
    private String experience;
    private String description;
    private String salary;
    private List<String> requiredSkills = new ArrayList<>();
    private List<String> preferredSkills = new ArrayList<>();

    public Job() {
    }

    public Job(int id, String title, String company, String location, String jobType,
               String experience, String description, String salary,
               List<String> requiredSkills, List<String> preferredSkills) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.location = location;
        this.jobType = jobType;
        this.experience = experience;
        this.description = description;
        this.salary = salary;
        this.requiredSkills = (requiredSkills == null) ? new ArrayList<>() : requiredSkills;
        this.preferredSkills = (preferredSkills == null) ? new ArrayList<>() : preferredSkills;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    /** Never null. */
    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = (requiredSkills == null) ? new ArrayList<>() : requiredSkills;
    }

    /** Never null. */
    public List<String> getPreferredSkills() {
        return preferredSkills;
    }

    public void setPreferredSkills(List<String> preferredSkills) {
        this.preferredSkills = (preferredSkills == null) ? new ArrayList<>() : preferredSkills;
    }

    @Override
    public String toString() {
        return "Job{id=" + id + ", title='" + title + "', company='" + company
                + "', location='" + location + "', jobType='" + jobType
                + "', required=" + requiredSkills + ", preferred=" + preferredSkills + "}";
    }
}
