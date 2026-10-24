package com.resumematch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The facts pulled out of a resume's text: candidate name, contact line, whether a
 * degree was found, the CGPA (null when not listed), the bullet items of each section,
 * and whether the projects or experience mention measurable results.
 *
 * <p>Demonstrates: encapsulation, wrapper types ({@code Double} for an optional value),
 * {@code List<String>} / {@code ArrayList}.</p>
 */
public class ResumeInfo {

    private String name;
    private String contact;
    private boolean hasDegree;
    private Double cgpa;
    private List<String> education = new ArrayList<>();
    private List<String> projects = new ArrayList<>();
    private List<String> certifications = new ArrayList<>();
    private List<String> experience = new ArrayList<>();
    private boolean hasMetrics;

    public ResumeInfo() {
    }

    public ResumeInfo(String name, String contact, boolean hasDegree, Double cgpa,
                      List<String> education, List<String> projects,
                      List<String> certifications, List<String> experience,
                      boolean hasMetrics) {
        this.name = name;
        this.contact = contact;
        this.hasDegree = hasDegree;
        this.cgpa = cgpa;
        this.education = (education == null) ? new ArrayList<>() : education;
        this.projects = (projects == null) ? new ArrayList<>() : projects;
        this.certifications = (certifications == null) ? new ArrayList<>() : certifications;
        this.experience = (experience == null) ? new ArrayList<>() : experience;
        this.hasMetrics = hasMetrics;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public boolean hasDegree() {
        return hasDegree;
    }

    /** Bean-style form of {@link #hasDegree()} so EL can read {@code info.hasDegree}. */
    public boolean isHasDegree() {
        return hasDegree;
    }

    public void setHasDegree(boolean hasDegree) {
        this.hasDegree = hasDegree;
    }

    /** The CGPA, or {@code null} when the resume does not list one. */
    public Double getCgpa() {
        return cgpa;
    }

    public void setCgpa(Double cgpa) {
        this.cgpa = cgpa;
    }

    /** Never null. */
    public List<String> getEducation() {
        return education;
    }

    public void setEducation(List<String> education) {
        this.education = (education == null) ? new ArrayList<>() : education;
    }

    /** Never null. */
    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = (projects == null) ? new ArrayList<>() : projects;
    }

    /** Never null. */
    public List<String> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<String> certifications) {
        this.certifications = (certifications == null) ? new ArrayList<>() : certifications;
    }

    /** Never null. */
    public List<String> getExperience() {
        return experience;
    }

    public void setExperience(List<String> experience) {
        this.experience = (experience == null) ? new ArrayList<>() : experience;
    }

    public boolean hasMetrics() {
        return hasMetrics;
    }

    /** Bean-style form of {@link #hasMetrics()} so EL can read {@code info.hasMetrics}. */
    public boolean isHasMetrics() {
        return hasMetrics;
    }

    public void setHasMetrics(boolean hasMetrics) {
        this.hasMetrics = hasMetrics;
    }

    @Override
    public String toString() {
        return "ResumeInfo{name='" + name + "', contact='" + contact + "', hasDegree=" + hasDegree
                + ", cgpa=" + cgpa + ", education=" + education.size()
                + ", projects=" + projects.size() + ", certifications=" + certifications.size()
                + ", experience=" + experience.size() + ", hasMetrics=" + hasMetrics + "}";
    }
}
