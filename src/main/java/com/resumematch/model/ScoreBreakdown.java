package com.resumematch.model;

/**
 * One of the five rows of the resume score (Skills, Projects, Education, Certifications,
 * Experience): the points earned, the maximum for that section, and the rule text shown
 * under the bar.
 *
 * <p>Demonstrates: encapsulation, a value object with a derived getter ({@link #getPercent()}).</p>
 */
public class ScoreBreakdown {

    private String label;
    private int points;
    private int maxPoints;
    private String reason;

    public ScoreBreakdown() {
    }

    public ScoreBreakdown(String label, int points, int maxPoints, String reason) {
        this.label = label;
        this.points = points;
        this.maxPoints = maxPoints;
        this.reason = reason;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /** Points as a whole percentage of the maximum, used for the width of the bar. */
    public int getPercent() {
        return (maxPoints == 0) ? 0 : Math.round(points * 100f / maxPoints);
    }

    @Override
    public String toString() {
        return "ScoreBreakdown{" + label + " " + points + "/" + maxPoints + ", reason='" + reason + "'}";
    }
}
