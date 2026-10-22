package com.resumematch.service;

import com.resumematch.model.ResumeInfo;
import com.resumematch.model.ScoreBreakdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scores a resume out of 100 with five fixed rules, one per section, and keeps the rule
 * text so the page can show exactly why each section got its points:
 * <pre>
 *   Skills          min(40, round(skills x 4.5))
 *   Projects        min(16, projects x 8) + 4 if a measurable result is found
 *   Education       10 for a degree + 5 if CGPA &gt;= 8.5, 3 if a lower CGPA is listed (max 15)
 *   Certifications  min(10, round(certifications x 3.5))
 *   Experience      min(15, entries x 10)
 * </pre>
 * Also lists strengths and areas to improve, each from a simple condition.
 *
 * <p>Demonstrates: {@code ArrayList<ScoreBreakdown>} and {@code List<String>}, {@code Set}
 * lookups ({@code contains} on the {@code HashSet} of skills), {@code Math.min} /
 * {@code Math.round}, String building.</p>
 */
public class ResumeScorer {

    public static final int SKILLS_MAX = 40;
    public static final int PROJECTS_MAX = 20;
    public static final int EDUCATION_MAX = 15;
    public static final int CERTIFICATIONS_MAX = 10;
    public static final int EXPERIENCE_MAX = 15;

    private static final String TIMES = " × ";   // multiplication sign
    private static final String AT_LEAST = " ≥ "; // greater-than-or-equal sign

    /**
     * Applies the five rules.
     *
     * @param info   facts extracted from the resume
     * @param skills canonical names of the skills found
     * @return the five rows in order: Skills, Projects, Education, Certifications, Experience
     */
    public List<ScoreBreakdown> calculateScore(ResumeInfo info, Set<String> skills) {
        int s = (skills == null) ? 0 : skills.size();
        int p = info.getProjects().size();
        int c = info.getCertifications().size();
        int e = info.getExperience().size();
        Double cgpa = info.getCgpa();

        List<ScoreBreakdown> rows = new ArrayList<>();

        rows.add(row("Skills", Math.min(SKILLS_MAX, Math.round(s * 4.5f)), SKILLS_MAX,
                s + " skills" + TIMES + "4.5 points (full marks at 9 skills)"));

        rows.add(row("Projects", Math.min(16, p * 8) + (info.hasMetrics() ? 4 : 0), PROJECTS_MAX,
                p + " project" + (p != 1 ? "s" : "") + TIMES + "8 points, max 16; +4 for measurable results "
                        + (info.hasMetrics() ? "(found)" : "(not found)")));

        int edu = (info.hasDegree() ? 10 : 0) + (cgpa != null ? (cgpa >= 8.5 ? 5 : 3) : 0);
        String cgpaText = (cgpa == null)
                ? "not listed"
                : formatNumber(cgpa) + (cgpa >= 8.5 ? AT_LEAST + "8.5 (+5)" : " listed (+3)");
        rows.add(row("Education", Math.min(EDUCATION_MAX, edu), EDUCATION_MAX,
                "Degree " + (info.hasDegree() ? "found (+10)" : "not found") + "; CGPA " + cgpaText));

        rows.add(row("Certifications", Math.min(CERTIFICATIONS_MAX, Math.round(c * 3.5f)), CERTIFICATIONS_MAX,
                c + " certification" + (c != 1 ? "s" : "") + TIMES + "3.5 points"));

        rows.add(row("Experience", Math.min(EXPERIENCE_MAX, e * 10), EXPERIENCE_MAX,
                e + " internship/job entr" + (e != 1 ? "ies" : "y") + TIMES + "10 points, max 15"));

        return rows;
    }

    /** Adds up the points of the rows; the total is out of 100. */
    public int totalScore(List<ScoreBreakdown> rows) {
        int total = 0;
        if (rows != null) {
            for (ScoreBreakdown row : rows) {
                total += row.getPoints();
            }
        }
        return total;
    }

    /** Strengths, in a fixed order, each shown only when its condition holds. */
    public List<String> findStrengths(ResumeInfo info, Set<String> skills) {
        List<String> good = new ArrayList<>();
        if (has(skills, "Java")) {
            good.add(has(skills, "JDBC")
                    ? "Good Java knowledge, including database access with JDBC"
                    : "Good Java knowledge");
        }
        if (has(skills, "SQL") || has(skills, "MySQL")) {
            good.add("SQL experience");
        }
        if (info.getProjects().size() >= 2) {
            good.add("Multiple projects (" + info.getProjects().size() + ")");
        }
        if (has(skills, "DSA")) {
            good.add("Data structures and algorithms listed, which most coding interviews test");
        }
        if (!info.getExperience().isEmpty()) {
            good.add("Has internship experience");
        }
        if (has(skills, "Git")) {
            good.add("Uses Git for version control");
        }
        return good;
    }

    /** Areas to improve, in a fixed order, each shown only when its condition holds. */
    public List<String> findWeaknesses(ResumeInfo info, Set<String> skills) {
        List<String> bad = new ArrayList<>();
        if (!has(skills, "Spring Boot") && !has(skills, "Flask")) {
            bad.add("Add more backend technologies, such as Spring Boot or REST APIs");
        }
        if (!info.hasMetrics()) {
            bad.add("Add measurable project achievements, such as \"handles 500 records\" "
                    + "or \"cut load time by 30%\"");
        }
        if (info.getCertifications().size() < 3) {
            bad.add("Add relevant certifications (you have " + info.getCertifications().size() + ")");
        }
        if (!has(skills, "Docker")) {
            bad.add("Learn a deployment tool like Docker");
        }
        if (info.getCgpa() == null) {
            bad.add("Mention your CGPA in the education section");
        }
        return bad;
    }

    private static ScoreBreakdown row(String label, int points, int max, String reason) {
        return new ScoreBreakdown(label, points, max, reason);
    }

    private static boolean has(Set<String> skills, String name) {
        return skills != null && skills.contains(name);
    }

    /** 8.2 stays "8.2"; a whole number such as 9.0 is shown as "9". */
    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
