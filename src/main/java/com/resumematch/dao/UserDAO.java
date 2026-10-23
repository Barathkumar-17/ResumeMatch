package com.resumematch.dao;

import com.resumematch.exception.DatabaseConnectionException;
import com.resumematch.exception.DuplicateEmailException;
import com.resumematch.model.User;
import com.resumematch.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All SQL for the {@code users} table: creating an account, looking one up by
 * email or id, and updating its password hash. A second registration with the same email
 * is caught from the UNIQUE index and turned into a {@link DuplicateEmailException}.
 *
 * <p>Demonstrates: JDBC, {@code PreparedStatement}, generated keys, try-with-resources,
 * catching a specific SQL error and rethrowing it as a custom exception.</p>
 */
public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    /** MySQL error code for a duplicate value in a UNIQUE or PRIMARY KEY index. */
    private static final int MYSQL_DUPLICATE_KEY = 1062;

    private static final String INSERT_USER =
            "INSERT INTO users (name, email, password) VALUES (?, ?, ?)";
    private static final String SELECT_BY_EMAIL =
            "SELECT id, name, email, password FROM users WHERE email = ?";
    private static final String SELECT_BY_ID =
            "SELECT id, name, email, password FROM users WHERE id = ?";

    private static final String UPDATE_PASSWORD =
            "UPDATE users SET password = ? WHERE id = ?";

    /**
     * Creates a new account.
     *
     * @param name         the user's full name
     * @param email        login email, must not already be registered
     * @param passwordHash hash of the password (never the plain text)
     * @return the id MySQL generated for the new row
     * @throws DuplicateEmailException     if the email already has an account
     * @throws DatabaseConnectionException if MySQL cannot be reached or the insert fails
     */
    public int register(String name, String email, String passwordHash)
            throws DuplicateEmailException, DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            LOGGER.log(Level.SEVERE, "register: no generated id returned for the new user");
            throw new DatabaseConnectionException();
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                throw new DuplicateEmailException(e);
            }
            LOGGER.log(Level.SEVERE, "register failed", e);
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * Looks up an account by email, used at login.
     *
     * @return the user, or {@code null} if no account has this email
     * @throws DatabaseConnectionException if the query fails
     */
    public User findByEmail(String email) throws DatabaseConnectionException {
        return findOne(SELECT_BY_EMAIL, email, "findByEmail");
    }

    /**
     * Looks up an account by its id, used to reload the logged-in user.
     *
     * @return the user, or {@code null} if no account has this id
     * @throws DatabaseConnectionException if the query fails
     */
    public User findById(int id) throws DatabaseConnectionException {
        return findOne(SELECT_BY_ID, id, "findById");
    }

    /**
     * Replaces the stored password hash, used to upgrade an older hash after login.
     *
     * @param userId       the account to update
     * @param passwordHash the new hash (never the plain text)
     * @throws DatabaseConnectionException if the update fails
     */
    public void updatePassword(int userId, String passwordHash) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_PASSWORD)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updatePassword failed", e);
            throw new DatabaseConnectionException(e);
        }
    }

    /** Runs a single-parameter SELECT and maps the first row, if any, to a {@link User}. */
    private User findOne(String sql, Object param, String method) throws DatabaseConnectionException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(rs.getInt("id"), rs.getString("name"),
                            rs.getString("email"), rs.getString("password"));
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, method + " failed", e);
            throw new DatabaseConnectionException(e);
        }
    }

    /** True only for MySQL error 1062, a duplicate value in the UNIQUE index (not any other constraint error). */
    private static boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == MYSQL_DUPLICATE_KEY;
    }
}
