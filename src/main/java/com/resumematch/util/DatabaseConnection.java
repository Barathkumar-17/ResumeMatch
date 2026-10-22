package com.resumematch.util;

import com.resumematch.exception.DatabaseConnectionException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single place that opens JDBC connections. Settings are read once from
 * {@code db.properties} on the classpath (keys {@code db.url}, {@code db.user},
 * {@code db.password}); any missing key falls back to a localhost default so the
 * project still runs without the file. The environment variables {@code RESUMEMATCH_DB_URL},
 * {@code RESUMEMATCH_DB_USER} and {@code RESUMEMATCH_DB_PASSWORD}, when set, take priority
 * over the file (the Docker setup uses them). A connect timeout (5 s) and a socket timeout
 * (30 s) are always part of the URL, so a database that is down or stuck can never
 * hang a page forever. Callers close the connection with try-with-resources.
 *
 * <p>Demonstrates: JDBC ({@code DriverManager}, {@code Connection}), {@code Properties},
 * {@code System.getenv}, static initialisation, class loading, {@code java.util.logging}, wrapping
 * {@code SQLException} in a custom exception.</p>
 */
public final class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final String CONFIG_FILE = "db.properties";

    private static final String CONNECT_TIMEOUT = "connectTimeout=5000";
    private static final String SOCKET_TIMEOUT = "socketTimeout=30000";

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/resumematch?useSSL=false"
            + "&allowPublicKeyRetrieval=true&" + CONNECT_TIMEOUT + "&" + SOCKET_TIMEOUT;
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static final String url;
    private static final String user;
    private static final String password;
    private static final boolean driverLoaded;

    static {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);
            } else {
                LOGGER.log(Level.WARNING, CONFIG_FILE
                        + " not found on the classpath; using localhost defaults.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read " + CONFIG_FILE
                    + "; using localhost defaults.", e);
        }
        // Environment variables win over the file, so a container can be configured without a rebuild.
        url = withTimeouts(setting("RESUMEMATCH_DB_URL", props, "db.url", DEFAULT_URL).trim());
        user = setting("RESUMEMATCH_DB_USER", props, "db.user", DEFAULT_USER).trim();
        password = setting("RESUMEMATCH_DB_PASSWORD", props, "db.password", DEFAULT_PASSWORD);

        boolean loaded = false;
        try {
            Class.forName(DRIVER_CLASS);
            loaded = true;
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL driver " + DRIVER_CLASS
                    + " not found. Put mysql-connector-j in the classpath.", e);
        }
        driverLoaded = loaded;
    }

    private DatabaseConnection() {
        // utility class, no instances
    }

    /** Returns the environment variable when it is set and not blank, otherwise the property or its default. */
    private static String setting(String envName, Properties props, String key, String fallback) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return props.getProperty(key, fallback);
    }

    /** Adds the two timeouts to a configured URL, but only the ones it does not already set. */
    private static String withTimeouts(String configured) {
        StringBuilder result = new StringBuilder(configured);
        for (String setting : new String[] {CONNECT_TIMEOUT, SOCKET_TIMEOUT}) {
            String key = setting.substring(0, setting.indexOf('='));
            if (!configured.contains(key + "=")) {
                result.append(result.indexOf("?") >= 0 ? '&' : '?').append(setting);
            }
        }
        return result.toString();
    }

    /**
     * Opens a new connection to the ResumeMatch database.
     *
     * @return an open connection; the caller must close it
     * @throws DatabaseConnectionException if the driver is missing, MySQL is down or the
     *         credentials are wrong. The raw error goes to the application log only.
     */
    public static Connection getConnection() throws DatabaseConnectionException {
        if (!driverLoaded) {
            throw new DatabaseConnectionException();
        }
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            // The password is never logged.
            LOGGER.log(Level.SEVERE, "MySQL is unreachable or refused the login: url=" + url
                    + ", user=" + user, e);
            throw new DatabaseConnectionException(e);
        }
    }
}
