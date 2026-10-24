package com.resumematch.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Runs once when the web application starts and stops. On start it attaches a rolling
 * log file ({@code resumematch.<n>.log}, 1 MB each, 5 files) to the {@code com.resumematch}
 * logger so every error the application logs lands in one place; on stop it detaches
 * and closes that file again. It also creates the {@link DemoMode} switch.
 *
 * <p>Demonstrates: {@code ServletContextListener} (application life cycle), {@code java.util.logging}
 * ({@code Logger}, {@code FileHandler}, custom {@code Formatter}), File Handling, exception handling.</p>
 */
@WebListener
public class AppStartupListener implements ServletContextListener {

    private static final String LOGGER_NAME = "com.resumematch";
    private static final long MAX_LOG_BYTES = 1024L * 1024;
    private static final int LOG_FILE_COUNT = 5;

    /** Strong reference: the log manager only keeps loggers weakly, so settings would be lost. */
    private static final Logger APP_LOGGER = Logger.getLogger(LOGGER_NAME);

    private FileHandler handler;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        DemoMode.install(event.getServletContext(), baseFolder().resolve(DemoMode.FLAG_FILE_NAME));
        try {
            Path dir = logFolder();
            Files.createDirectories(dir);
            String pattern = dir.resolve("resumematch.%g.log").toString();
            handler = new FileHandler(pattern, MAX_LOG_BYTES, LOG_FILE_COUNT, true);
            handler.setEncoding(StandardCharsets.UTF_8.name());
            handler.setFormatter(new OneLineFormatter());
            handler.setLevel(Level.INFO);
            APP_LOGGER.addHandler(handler);
            APP_LOGGER.setLevel(Level.INFO);
            APP_LOGGER.log(Level.INFO, "ResumeMatch started, logging to {0}", dir);
        } catch (IOException | SecurityException e) {
            // Logging is optional: the application still runs, messages go to the console.
            handler = null;
            APP_LOGGER.log(Level.WARNING, "Could not open the log file; using the console only", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (handler != null) {
            APP_LOGGER.log(Level.INFO, "ResumeMatch stopped");
            APP_LOGGER.removeHandler(handler);
            handler.close();
            handler = null;
        }
    }

    /** {@code <catalina.base>}, or the system temp folder when Tomcat's property is not set. */
    private static Path baseFolder() {
        String base = System.getProperty("catalina.base");
        if (base != null && !base.trim().isEmpty()) {
            return Paths.get(base.trim());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    /** {@code <catalina.base>/logs}, or the system temp folder when Tomcat's property is not set. */
    private static Path logFolder() {
        String base = System.getProperty("catalina.base");
        if (base != null && !base.trim().isEmpty()) {
            return Paths.get(base.trim(), "logs");
        }
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    /** One line per record: date, level, logger, message, then the stack trace if any. */
    private static final class OneLineFormatter extends Formatter {
        private static final DateTimeFormatter TIME =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder();
            line.append(TIME.format(ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault())))
                .append(' ').append(record.getLevel().getName())
                .append(' ').append(record.getLoggerName())
                .append(' ').append(formatMessage(record))
                .append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter trace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(trace));
                line.append(trace);
            }
            return line.toString();
        }
    }
}
