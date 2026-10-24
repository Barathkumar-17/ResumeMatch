package com.resumematch.console;

import com.resumematch.dao.JobDAO;
import com.resumematch.dao.SkillDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.DatabaseConnectionException;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.ScoreBreakdown;
import com.resumematch.model.Skill;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.service.SkillBasedJobMatcher;
import com.resumematch.service.TxtResumeReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Runs the whole analysis pipeline from the command line, with no web server.
 *
 * <p>When something fails (the file is missing, MySQL is down, the password is wrong) it prints
 * one short message with a hint and exits with status 1. The technical details, including
 * the SQL error, go to a log file in the temp folder and are never printed on the console.</p>
 *
 * <p>Demonstrates: command-line arguments, file handling, JDBC through a DAO,
 * Collections, custom exception handling in main, exit status codes, {@code java.util.logging}
 * with a {@code FileHandler}.</p>
 */
public class ConsoleDemo {

    private static final String DEFAULT_RESUME = "sample-data/arun_kumar_resume.txt";

    /** Held in a field because the log manager keeps loggers only weakly and would forget the settings. */
    private static final Logger APP_LOGGER = Logger.getLogger("com.resumematch");

    public static void main(String[] args) {
        String logFile = sendLogsToFile();
        Path resumePath = Paths.get(args.length > 0 ? args[0] : DEFAULT_RESUME);
        try {
            String text = new TxtResumeReader().read(resumePath);
            List<Skill> dictionary = new SkillDAO().findAll();

            Resume resume = new Resume();
            resume.setFileName(resumePath.getFileName().toString());
            resume.setResumeText(text);
            new ResumeAnalyzer(dictionary).analyze(resume);

            printSkills(resume);
            printScore(resume);
            printJobMatches(resume);
        } catch (AppException e) {
            System.err.println(failureMessage(e));
            if (logFile != null) {
                System.err.println("Technical details: " + logFile);
            }
            System.exit(1);
        } catch (RuntimeException e) {
            APP_LOGGER.log(Level.SEVERE, "the console demo failed unexpectedly", e);
            System.err.println("Could not run the demo: something unexpected went wrong.");
            if (logFile != null) {
                System.err.println("Technical details: " + logFile);
            }
            System.exit(1);
        }
    }

    /**
     * The text printed when the demo cannot run: the friendly message of the exception and,
     * for a database problem, what to check. It never contains SQL or a stack trace.
     */
    public static String failureMessage(AppException e) {
        String text = "Could not run the demo: " + e.getUserMessage();
        if (e instanceof DatabaseConnectionException) {
            text += "\nCheck that MySQL is running and that src/main/resources/db.properties "
                    + "(or the RESUMEMATCH_DB_URL, RESUMEMATCH_DB_USER and RESUMEMATCH_DB_PASSWORD "
                    + "variables) has the right URL, user and password.";
        }
        return text;
    }

    /**
     * Moves the application log from the console to a file, so a failed connection prints one
     * friendly line instead of the driver's error and stack trace.
     *
     * @return the log file path, or {@code null} when the file cannot be opened
     */
    private static String sendLogsToFile() {
        APP_LOGGER.setUseParentHandlers(false);
        String path = Paths.get(System.getProperty("java.io.tmpdir"), "resumematch-console.log").toString();
        try {
            FileHandler handler = new FileHandler(path, true);
            handler.setFormatter(new SimpleFormatter());
            APP_LOGGER.addHandler(handler);
            return path;
        } catch (IOException | SecurityException e) {
            return null; // no handler at all: the details are dropped rather than printed
        }
    }

    private static void printSkills(Resume resume) {
        List<String> skills = new ArrayList<>(resume.getSkills());
        Collections.sort(skills);
        System.out.println("Resume: " + resume.getFileName());
        System.out.println();
        System.out.println("Skills extracted (" + skills.size() + "): " + String.join(", ", skills));
        System.out.println();
    }

    /** Console text is plain ASCII: the multiplication and greater-or-equal signs of the web page become x and >=. */
    public static String plain(String text) {
        return text.replace('×', 'x').replace("≥", ">=");
    }

    private static void printScore(Resume resume) {
        System.out.println("Score breakdown");
        for (ScoreBreakdown row : resume.getBreakdown()) {
            System.out.printf("  %-15s %2d / %-2d  %s%n",
                    row.getLabel(), row.getPoints(), row.getMaxPoints(), plain(row.getReason()));
        }
        System.out.println("  Total: " + resume.getScore() + "/100");
        System.out.println();
    }

    private static void printJobMatches(Resume resume) throws AppException {
        SkillBasedJobMatcher matcher = new SkillBasedJobMatcher();
        List<JobMatch> ranked = matcher.rankJobs(resume.getSkills(), new JobDAO().findAll());

        System.out.println("Jobs ranked by match");
        for (JobMatch m : ranked) {
            System.out.printf("  %3d%%  #%d %s (%s)%n", m.getScore(), m.getJob().getId(),
                    m.getJob().getTitle(), m.getJob().getCompany());
            System.out.println("        matched: " + String.join(", ", m.getMatched()));
            System.out.println("        missing: " + String.join(", ", m.getMissing()));
        }
    }
}
