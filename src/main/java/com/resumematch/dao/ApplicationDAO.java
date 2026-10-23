package com.resumematch.dao;

import com.resumematch.exception.DatabaseConnectionException;
import com.resumematch.model.Application;
import com.resumematch.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks the jobs a student has saved ({@code saved_jobs}) and applied to
 * ({@code applications}). A bookmarked job lives in {@code saved_jobs}; marking it as
 * applied moves it into {@code applications} with status "Applied", which the student can
 * later change to Interview, Rejected or Selected.
 *
 * <p>Demonstrates: JDBC with {@code PreparedStatement}, try-with-resources, a small
 * transaction across two tables, {@code ON DUPLICATE KEY UPDATE} so repeated clicks never
 * create duplicates, mapping rows to {@code List<Application>}, wrapping
 * {@code SQLException} in a custom exception.</p>
 */
public class ApplicationDAO {

    private static final Logger LOGGER = Logger.getLogger(ApplicationDAO.class.getName());

    /** Status shown for a job that is only saved; it is not stored in the database. */
    public static final String STATUS_SAVED = "Saved";

    /** Saving twice keeps the one existing row and its original date. */
    private static final String INSERT_SAVED =
            "INSERT INTO saved_jobs (user_id, job_id) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE saved_at = saved_at";

    private static final String DELETE_SAVED =
            "DELETE FROM saved_jobs WHERE user_id = ? AND job_id = ?";

    private static final String DELETE_APPLICATION =
            "DELETE FROM applications WHERE user_id = ? AND job_id = ?";

    private static final String IS_SAVED =
            "SELECT 1 FROM saved_jobs WHERE user_id = ? AND job_id = ?"
            + " UNION SELECT 1 FROM applications WHERE user_id = ? AND job_id = ?";

    /** Applying twice keeps the existing row and its current status. */
    private static final String INSERT_APPLICATION =
            "INSERT INTO applications (user_id, job_id, status) VALUES (?, ?, 'Applied')"
            + " ON DUPLICATE KEY UPDATE status = status";

    private static final String UPDATE_STATUS =
            "UPDATE applications SET status = ? WHERE user_id = ? AND job_id = ?";

    private static final String SELECT_SAVED =
            "SELECT sj.user_id, sj.job_id, sj.saved_at FROM saved_jobs sj"
            + " WHERE sj.user_id = ? AND NOT EXISTS (SELECT 1 FROM applications a"
            + " WHERE a.user_id = sj.user_id AND a.job_id = sj.job_id)"
            + " ORDER BY sj.saved_at DESC, sj.job_id";

    private static final String SELECT_APPLICATIONS =
            "SELECT id, user_id, job_id, status, applied_at FROM applications"
            + " WHERE user_id = ? ORDER BY applied_at DESC, id DESC";

    /**
     * Bookmarks a job. Saving a job that is already saved does nothing.
     *
     * @throws DatabaseConnectionException if the insert fails
     */
    public void saveJob(int userId, int jobId) throws DatabaseConnectionException {
        executeUpdate(INSERT_SAVED, "saveJob", userId, jobId);
    }

    /**
     * Removes only the bookmark (the Saved tab entry). An application for the same job is
     * left alone, so the Save toggle on the job pages can never delete an application.
     *
     * @throws DatabaseConnectionException if the delete fails
     */
    public void removeBookmark(int userId, int jobId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection()) {
            runUpdate(con, DELETE_SAVED, userId, jobId);
        } catch (SQLException e) {
            throw wrap("removeBookmark", e);
        }
    }

    /**
     * Removes the job from the student's list entirely: the bookmark and, if they had
     * applied, the application too. Removing a job that is not there does nothing.
     *
     * @throws DatabaseConnectionException if the delete fails
     */
    public void removeSavedJob(int userId, int jobId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection()) {
            boolean oldAutoCommit = con.getAutoCommit();
            try {
                con.setAutoCommit(false);
                runUpdate(con, DELETE_SAVED, userId, jobId);
                runUpdate(con, DELETE_APPLICATION, userId, jobId);
                con.commit();
            } catch (SQLException e) {
                rollbackQuietly(con);
                throw e;
            } finally {
                restoreAutoCommit(con, oldAutoCommit);
            }
        } catch (SQLException e) {
            throw wrap("removeSavedJob", e);
        }
    }

    /**
     * @return true if the job is on the student's list, either saved or applied
     * @throws DatabaseConnectionException if the query fails
     */
    public boolean isSaved(int userId, int jobId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(IS_SAVED)) {
            ps.setInt(1, userId);
            ps.setInt(2, jobId);
            ps.setInt(3, userId);
            ps.setInt(4, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw wrap("isSaved", e);
        }
    }

    /**
     * Records an application with status "Applied" and removes the bookmark, so the job
     * moves from the Saved tab to the Applied tab. Both changes happen in one transaction.
     * Applying twice keeps the first application and its status.
     *
     * @throws DatabaseConnectionException if either statement fails
     */
    public void markApplied(int userId, int jobId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection()) {
            boolean oldAutoCommit = con.getAutoCommit();
            try {
                con.setAutoCommit(false);
                runUpdate(con, INSERT_APPLICATION, userId, jobId);
                runUpdate(con, DELETE_SAVED, userId, jobId);
                con.commit();
            } catch (SQLException e) {
                rollbackQuietly(con);
                throw e;
            } finally {
                restoreAutoCommit(con, oldAutoCommit);
            }
        } catch (SQLException e) {
            throw wrap("markApplied", e);
        }
    }

    /**
     * Changes the status of an existing application.
     *
     * @param status one of Applied, Interview, Rejected, Selected
     * @return true if the student had applied to that job and the status was changed,
     *         false if there was no such application
     * @throws DatabaseConnectionException if the update fails (including a status the
     *         database does not accept)
     */
    public boolean updateStatus(int userId, int jobId, String status) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.setInt(3, jobId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw wrap("updateStatus", e);
        }
    }

    /**
     * @return the jobs the student saved but has not applied to, newest first. Each entry
     *         has id 0, status "Saved" and the date it was saved.
     * @throws DatabaseConnectionException if the query fails
     */
    public List<Application> findSaved(int userId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_SAVED)) {
            ps.setInt(1, userId);
            List<Application> saved = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp savedAt = rs.getTimestamp("saved_at");
                    saved.add(new Application(0, rs.getInt("user_id"), rs.getInt("job_id"),
                            STATUS_SAVED, savedAt == null ? null : savedAt.toLocalDateTime().toLocalDate()));
                }
            }
            return saved;
        } catch (SQLException e) {
            throw wrap("findSaved", e);
        }
    }

    /**
     * @return the student's applications with their current status, newest first
     * @throws DatabaseConnectionException if the query fails
     */
    public List<Application> findApplications(int userId) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_APPLICATIONS)) {
            ps.setInt(1, userId);
            List<Application> applications = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp appliedAt = rs.getTimestamp("applied_at");
                    applications.add(new Application(rs.getInt("id"), rs.getInt("user_id"),
                            rs.getInt("job_id"), rs.getString("status"),
                            appliedAt == null ? null : appliedAt.toLocalDateTime().toLocalDate()));
                }
            }
            return applications;
        } catch (SQLException e) {
            throw wrap("findApplications", e);
        }
    }

    /** Runs a single (user_id, job_id) statement on its own connection. */
    private void executeUpdate(String sql, String operation, int userId, int jobId)
            throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection()) {
            runUpdate(con, sql, userId, jobId);
        } catch (SQLException e) {
            throw wrap(operation, e);
        }
    }

    private void runUpdate(Connection con, String sql, int userId, int jobId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, jobId);
            ps.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException rollbackError) {
            LOGGER.log(Level.WARNING, "rollback failed", rollbackError);
        }
    }

    private void restoreAutoCommit(Connection con, boolean autoCommit) {
        try {
            con.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // the connection is closed next anyway
        }
    }

    /** Logs the raw error to the server console and returns the friendly exception. */
    private DatabaseConnectionException wrap(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE, operation + " failed", e);
        return new DatabaseConnectionException(e);
    }
}
