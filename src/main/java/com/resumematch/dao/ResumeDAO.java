package com.resumematch.dao;

import com.resumematch.exception.DatabaseConnectionException;
import com.resumematch.model.Resume;
import com.resumematch.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores uploaded resumes and the skills found in them ({@code resumes} and
 * {@code resume_skills}). Saving a resume is one transaction: either the resume row and
 * all its skill rows are written, or nothing is.
 *
 * <p>Demonstrates: JDBC transactions ({@code setAutoCommit(false)}, {@code commit},
 * {@code rollback}), batch inserts ({@code addBatch} / {@code executeBatch}), generated
 * keys, try-with-resources, {@code HashSet}, wrapping {@code SQLException} in a custom
 * exception.</p>
 */
public class ResumeDAO {

    private static final String INSERT_RESUME =
            "INSERT INTO resumes (user_id, file_name, file_path, resume_text, score)"
            + " VALUES (?, ?, ?, ?, ?)";

    private static final String INSERT_RESUME_SKILL =
            "INSERT INTO resume_skills (resume_id, skill_id) VALUES (?, ?)";

    private static final String RESUME_COLUMNS =
            "SELECT id, user_id, file_name, file_path, resume_text, score, uploaded_at FROM resumes";

    private static final String SELECT_LATEST_BY_USER =
            RESUME_COLUMNS + " WHERE user_id = ? ORDER BY uploaded_at DESC, id DESC LIMIT 1";

    private static final String SELECT_ALL_BY_USER =
            RESUME_COLUMNS + " WHERE user_id = ? ORDER BY uploaded_at DESC, id DESC";

    private static final String SELECT_SKILL_NAMES =
            "SELECT s.skill_name FROM resume_skills rs JOIN skills s ON s.id = rs.skill_id"
            + " WHERE rs.resume_id = ? ORDER BY s.id";

    /**
     * Inserts the resume and one {@code resume_skills} row per skill id in a single
     * transaction. If any insert fails, everything is rolled back and no orphan resume
     * row is left behind. On success the generated id is also set on {@code resume}.
     *
     * @param resume   the resume to save (user id, file name, path, text and score are used)
     * @param skillIds ids of the skills found in it; may be empty
     * @return the new resume id
     * @throws DatabaseConnectionException if the database cannot be reached or any insert fails
     */
    public int saveResumeWithSkills(Resume resume, Set<Integer> skillIds)
            throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection()) {
            boolean oldAutoCommit = con.getAutoCommit();
            try {
                con.setAutoCommit(false);

                int resumeId;
                try (PreparedStatement ps = con.prepareStatement(INSERT_RESUME,
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, resume.getUserId());
                    ps.setString(2, resume.getFileName());
                    ps.setString(3, resume.getFilePath());
                    ps.setString(4, resume.getResumeText());
                    ps.setInt(5, resume.getScore());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No id was generated for the new resume.");
                        }
                        resumeId = keys.getInt(1);
                    }
                }

                if (skillIds != null && !skillIds.isEmpty()) {
                    try (PreparedStatement ps = con.prepareStatement(INSERT_RESUME_SKILL)) {
                        for (Integer skillId : skillIds) {
                            ps.setInt(1, resumeId);
                            ps.setInt(2, skillId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                con.commit();
                resume.setId(resumeId);
                return resumeId;
            } catch (SQLException e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    System.err.println("[ResumeDAO] rollback failed: " + rollbackError.getMessage());
                }
                throw e;
            } finally {
                try {
                    con.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignored) {
                    // the connection is closed next anyway
                }
            }
        } catch (SQLException e) {
            System.err.println("[ResumeDAO] saveResumeWithSkills failed, rolled back: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * @param userId the user id
     * @return the user's most recent resume with its skill names, or {@code null} if they
     *         have not uploaded one
     * @throws DatabaseConnectionException if the query fails
     */
    public Resume findLatestByUser(int userId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_LATEST_BY_USER)) {
            ps.setInt(1, userId);
            Resume resume = null;
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    resume = mapResume(rs);
                }
            }
            if (resume != null) {
                resume.setSkills(new HashSet<>(readSkillNames(con, resume.getId())));
            }
            return resume;
        } catch (SQLException e) {
            System.err.println("[ResumeDAO] findLatestByUser(" + userId + ") failed: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * @param userId the user id
     * @return all of the user's resumes, newest first, each with its skill names; empty if none
     * @throws DatabaseConnectionException if the query fails
     */
    public List<Resume> findAllByUser(int userId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL_BY_USER)) {
            ps.setInt(1, userId);
            List<Resume> resumes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resumes.add(mapResume(rs));
                }
            }
            for (Resume resume : resumes) {
                resume.setSkills(new HashSet<>(readSkillNames(con, resume.getId())));
            }
            return resumes;
        } catch (SQLException e) {
            System.err.println("[ResumeDAO] findAllByUser(" + userId + ") failed: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * @param resumeId the resume id
     * @return names of the skills found in that resume, in dictionary order; empty if none
     * @throws DatabaseConnectionException if the query fails
     */
    public List<String> findSkillNames(int resumeId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection()) {
            return readSkillNames(con, resumeId);
        } catch (SQLException e) {
            System.err.println("[ResumeDAO] findSkillNames(" + resumeId + ") failed: " + e.getMessage());
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }

    private List<String> readSkillNames(Connection con, int resumeId) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(SELECT_SKILL_NAMES)) {
            ps.setInt(1, resumeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("skill_name"));
                }
            }
        }
        return names;
    }

    private Resume mapResume(ResultSet rs) throws SQLException {
        Timestamp uploaded = rs.getTimestamp("uploaded_at");
        return new Resume(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("resume_text"),
                rs.getInt("score"),
                uploaded == null ? null : uploaded.toLocalDateTime());
    }
}
