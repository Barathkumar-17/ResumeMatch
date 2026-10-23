package com.resumematch.console;

import com.resumematch.dao.JobDAO;
import com.resumematch.dao.SkillDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.ScoreBreakdown;
import com.resumematch.model.Skill;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.service.SkillBasedJobMatcher;
import com.resumematch.service.TxtResumeReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs the whole analysis pipeline from the command line, with no web server.
 *
 * <p>Demonstrates: command-line arguments, file handling, JDBC through a DAO,
 * Collections, custom exception handling in main.</p>
 */
public class ConsoleDemo {

    private static final String DEFAULT_RESUME = "sample-data/arun_kumar_resume.txt";

    public static void main(String[] args) {
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
            System.out.println("Could not run the demo: " + e.getUserMessage());
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

    private static void printScore(Resume resume) {
        System.out.println("Score breakdown");
        for (ScoreBreakdown row : resume.getBreakdown()) {
            System.out.printf("  %-15s %2d / %-2d  %s%n",
                    row.getLabel(), row.getPoints(), row.getMaxPoints(), row.getReason());
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
