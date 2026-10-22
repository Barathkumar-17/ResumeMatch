package com.resumematch.dao;

import com.resumematch.exception.DatabaseConnectionException;
import com.resumematch.model.Job;
import com.resumematch.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads job listings from the {@code jobs} table. Every returned {@link Job} also carries
 * the names of its REQUIRED and PREFERRED skills from {@code job_skills}.
 *
 * <p>Demonstrates: JDBC with {@code PreparedStatement} and {@code ResultSet},
 * try-with-resources, {@code LinkedHashMap} for grouping rows by id, building a query
 * from fixed SQL fragments with {@code ?} placeholders only, wrapping {@code SQLException}
 * in a custom exception.</p>
 */
public class JobDAO {

    private static final String JOB_COLUMNS =
            "SELECT j.id, j.title, j.company, j.location, j.job_type, j.experience,"
            + " j.description, j.salary FROM jobs j";

    private static final String SELECT_ALL = JOB_COLUMNS + " ORDER BY j.id";

    private static final String SELECT_BY_ID = JOB_COLUMNS + " WHERE j.id = ?";

    private static final String SELECT_ALL_SKILLS =
            "SELECT js.job_id, s.skill_name, js.required_level"
            + " FROM job_skills js JOIN skills s ON s.id = js.skill_id"
            + " ORDER BY js.job_id, s.id";

    private static final String SELECT_SKILLS_FOR_JOB =
            "SELECT js.job_id, s.skill_name, js.required_level"
            + " FROM job_skills js JOIN skills s ON s.id = js.skill_id"
            + " WHERE js.job_id = ? ORDER BY s.id";

    /*
     * Search filters. Each fragment is fixed text; only the values go in as parameters.
     * The keyword matches title, company, location or description as a substring, or
     * any of the job's skills by exact name (so "Java" does not pull in "JavaScript").
     */
    private static final String KEYWORD_FILTER =
            " AND (LOWER(j.title) LIKE ? OR LOWER(j.company) LIKE ?"
            + " OR LOWER(j.location) LIKE ? OR LOWER(j.description) LIKE ?"
            + " OR EXISTS (SELECT 1 FROM job_skills js JOIN skills s ON s.id = js.skill_id"
            + " WHERE js.job_id = j.id AND LOWER(s.skill_name) = ?))";
    private static final String LOCATION_FILTER = " AND j.location = ?";
    private static final String JOB_TYPE_FILTER = " AND j.job_type = ?";
    private static final String EXPERIENCE_FILTER = " AND j.experience = ?";

    /**
     * @return every job ordered by id, each with its skill lists filled in
     * @throws DatabaseConnectionException if the query fails
     */
    public List<Job> findAll() throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            Map<Integer, Job> jobs = new LinkedHashMap<>();
            while (rs.next()) {
                Job job = mapJob(rs);
                jobs.put(job.getId(), job);
            }
            loadAllSkills(con, jobs);
            return new ArrayList<>(jobs.values());
        } catch (SQLException e) {
            System.err.println("[JobDAO] findAll failed: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * @param id the job id
     * @return the job with its skill lists, or {@code null} if no job has this id
     * @throws DatabaseConnectionException if the query fails
     */
    public Job findById(int id) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            Job job = null;
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    job = mapJob(rs);
                }
            }
            if (job != null) {
                Map<Integer, Job> one = new LinkedHashMap<>();
                one.put(job.getId(), job);
                try (PreparedStatement skillPs = con.prepareStatement(SELECT_SKILLS_FOR_JOB)) {
                    skillPs.setInt(1, id);
                    try (ResultSet rs = skillPs.executeQuery()) {
                        addSkillRows(rs, one);
                    }
                }
            }
            return job;
        } catch (SQLException e) {
            System.err.println("[JobDAO] findById(" + id + ") failed: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * Finds jobs matching all the given filters. A null or blank filter is ignored, so
     * passing all nulls returns every job.
     *
     * @param keyword    substring of title, company, location or description, or an exact
     *                   skill name; case-insensitive
     * @param location   exact location, e.g. "Chennai"
     * @param jobType    exact job type: Internship, Full-time or Part-time
     * @param experience exact experience level, e.g. "Fresher"
     * @return matching jobs ordered by id, each with its skill lists filled in
     * @throws DatabaseConnectionException if the query fails
     */
    public List<Job> search(String keyword, String location, String jobType, String experience)
            throws DatabaseConnectionException {
        StringBuilder sql = new StringBuilder(JOB_COLUMNS).append(" WHERE 1 = 1");
        List<String> params = new ArrayList<>();

        if (!isBlank(keyword)) {
            String k = keyword.trim().toLowerCase();
            String like = "%" + escapeLike(k) + "%";
            sql.append(KEYWORD_FILTER);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(k);
        }
        if (!isBlank(location)) {
            sql.append(LOCATION_FILTER);
            params.add(location.trim());
        }
        if (!isBlank(jobType)) {
            sql.append(JOB_TYPE_FILTER);
            params.add(jobType.trim());
        }
        if (!isBlank(experience)) {
            sql.append(EXPERIENCE_FILTER);
            params.add(experience.trim());
        }
        sql.append(" ORDER BY j.id");

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            Map<Integer, Job> jobs = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Job job = mapJob(rs);
                    jobs.put(job.getId(), job);
                }
            }
            if (!jobs.isEmpty()) {
                loadAllSkills(con, jobs);
            }
            return new ArrayList<>(jobs.values());
        } catch (SQLException e) {
            System.err.println("[JobDAO] search failed: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    /** Reads every job_skills row once and attaches each to its job if that job is in the map. */
    private void loadAllSkills(Connection con, Map<Integer, Job> jobs) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT_ALL_SKILLS);
             ResultSet rs = ps.executeQuery()) {
            addSkillRows(rs, jobs);
        }
    }

    private void addSkillRows(ResultSet rs, Map<Integer, Job> jobs) throws SQLException {
        while (rs.next()) {
            Job job = jobs.get(rs.getInt("job_id"));
            if (job == null) {
                continue;
            }
            String name = rs.getString("skill_name");
            if ("PREFERRED".equals(rs.getString("required_level"))) {
                job.getPreferredSkills().add(name);
            } else {
                job.getRequiredSkills().add(name);
            }
        }
    }

    private Job mapJob(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getInt("id"));
        job.setTitle(rs.getString("title"));
        job.setCompany(rs.getString("company"));
        job.setLocation(rs.getString("location"));
        job.setJobType(rs.getString("job_type"));
        job.setExperience(rs.getString("experience"));
        job.setDescription(rs.getString("description"));
        job.setSalary(rs.getString("salary"));
        return job;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Escapes the LIKE wildcards so a search for "100%" means the literal text. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
