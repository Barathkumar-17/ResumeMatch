package com.resumematch.util;

import com.resumematch.exception.DatabaseConnectionException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The single place that opens JDBC connections. Settings are read once from
 * {@code db.properties} on the classpath (keys {@code db.url}, {@code db.user},
 * {@code db.password}); any missing key falls back to a localhost default so the
 * project still runs without the file. Callers close the connection with
 * try-with-resources.
 *
 * <p>Demonstrates: JDBC ({@code DriverManager}, {@code Connection}), {@code Properties},
 * static initialisation, class loading, wrapping {@code SQLException} in a custom exception.</p>
 */
public final class DatabaseConnection {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final String CONFIG_FILE = "db.properties";

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/resumematch?useSSL=false&serverTimezone=UTC"
            + "&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static final String url;
    private static final String user;
    private static final String password;

    static {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);
            } else {
                System.err.println("[DatabaseConnection] " + CONFIG_FILE
                        + " not found on the classpath; using localhost defaults.");
            }
        } catch (IOException e) {
            System.err.println("[DatabaseConnection] Could not read " + CONFIG_FILE
                    + "; using localhost defaults.");
            e.printStackTrace();
        }
        url = props.getProperty("db.url", DEFAULT_URL).trim();
        user = props.getProperty("db.user", DEFAULT_USER).trim();
        password = props.getProperty("db.password", DEFAULT_PASSWORD);
    }

    private DatabaseConnection() {
        // utility class, no instances
    }

    /**
     * Opens a new connection to the ResumeMatch database.
     *
     * @return an open connection; the caller must close it
     * @throws DatabaseConnectionException if the driver is missing, MySQL is down or the
     *         credentials are wrong. The raw error is printed to the server console only.
     */
    public static Connection getConnection() throws DatabaseConnectionException {
        try {
            Class.forName(DRIVER_CLASS);
            return DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseConnection] MySQL driver " + DRIVER_CLASS
                    + " not found. Put mysql-connector-j in the classpath.");
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Could not connect to " + url + " as " + user);
            e.printStackTrace();
            throw new DatabaseConnectionException(e);
        }
    }
}
