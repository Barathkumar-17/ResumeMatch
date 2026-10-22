package com.resumematch;

import com.resumematch.exception.AppException;
import com.resumematch.exception.EmptyResumeException;
import com.resumematch.exception.FileReadException;
import com.resumematch.exception.InvalidFileException;
import com.resumematch.exception.MissingFieldException;
import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.ScoreBreakdown;
import com.resumematch.model.Skill;
import com.resumematch.service.FileService;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.service.ResumeScorer;
import com.resumematch.service.SkillBasedJobMatcher;
import com.resumematch.util.ValidationUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Runs the unit checks without a database or a test framework.
 * Prints PASS or FAIL for every check and exits with status 1 if any failed.
 *
 * <p>Demonstrates: static helper methods, Collections, exception handling,
 * temporary files, System.exit status codes.</p>
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    private static final String SAMPLE = "sample-data/arun_kumar_resume.txt";

    public static void main(String[] args) {
        // The services log expected errors (for example a missing file); keep the test output readable.
        Logger.getLogger("com.resumematch").setLevel(Level.OFF);
        try {
            analyzerTests();
            scorerTests();
            matcherTests();
            validationTests();
            fileServiceTests();
            SecurityTests.run();
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL  unexpected exception: " + e);
            e.printStackTrace(System.out);
        }
        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- tiny assert helpers (also used by SecurityTests) ----

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    static void checkEquals(String name, Object expected, Object actual) {
        boolean same = (expected == null) ? actual == null : expected.equals(actual);
        if (same) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + " (expected <" + expected + "> but was <" + actual + ">)");
        }
    }

    interface Action {
        void run() throws Exception;
    }

    static void checkThrows(String name, Class<? extends Exception> type, Action action) {
        try {
            action.run();
            failed++;
            System.out.println("FAIL  " + name + " (nothing thrown)");
        } catch (Exception e) {
            check(name, type.isInstance(e));
        }
    }

    // ---- fixtures built from database/02_seed_data.sql ----

    private static List<Skill> dictionary() {
        List<Skill> list = new ArrayList<>();
        add(list, 1, "Java", "Language");
        add(list, 2, "C++", "Language", "CPP");
        add(list, 3, "Python", "Language");
        add(list, 4, "SQL", "Database");
        add(list, 5, "MySQL", "Database");
        add(list, 6, "JDBC", "Database");
        add(list, 7, "Git", "Tool", "GitHub");
        add(list, 8, "DSA", "Fundamentals", "Data Structures and Algorithms", "Data Structures");
        add(list, 9, "Spring Boot", "Framework", "SpringBoot");
        add(list, 10, "REST API", "Web", "REST APIs", "RESTful");
        add(list, 11, "Docker", "Tool");
        add(list, 12, "HTML", "Web", "HTML5");
        add(list, 13, "CSS", "Web", "CSS3");
        add(list, 14, "JavaScript", "Web", "JS");
        add(list, 15, "Flask", "Framework");
        add(list, 16, "Linux", "Tool");
        add(list, 17, "Excel", "Data", "MS Excel");
        add(list, 18, "Power BI", "Data", "PowerBI");
        return list;
    }

    private static void add(List<Skill> list, int id, String name, String category, String... aliases) {
        list.add(new Skill(id, name, category, new ArrayList<>(Arrays.asList(aliases))));
    }

    private static List<Job> jobs() {
        List<Job> list = new ArrayList<>();
        job(list, 101, "Java Backend Intern", "Java,SQL,JDBC,Spring Boot,Git", "REST API");
        job(list, 102, "Python Developer Intern", "Python,Flask,SQL", "Git,REST API");
        job(list, 103, "Software Engineer Intern", "Java,DSA,Git,Docker", "C++,Linux");
        job(list, 104, "Frontend Developer Intern", "HTML,CSS,JavaScript,Git", "REST API");
        job(list, 105, "Data Analyst Intern", "Python,SQL,Excel,Power BI", "Git");
        job(list, 106, "Junior Java Developer", "Java,Spring Boot,REST API,SQL,Docker", "MySQL,Git");
        job(list, 107, "Full Stack Intern", "Java,JavaScript,HTML,CSS,SQL,REST API", "Git");
        job(list, 108, "Systems Programming Intern", "C++,DSA,Git,Linux", "Python");
        return list;
    }

    private static void job(List<Job> list, int id, String title, String required, String preferred) {
        list.add(new Job(id, title, "Company", "City", "Internship", "Fresher", "", "",
                new ArrayList<>(Arrays.asList(required.split(","))),
                new ArrayList<>(Arrays.asList(preferred.split(",")))));
    }

    private static Resume analyzeSample() throws Exception {
        Resume resume = new Resume();
        resume.setFileName("arun_kumar_resume.txt");
        resume.setResumeText(new String(Files.readAllBytes(Paths.get(SAMPLE)), StandardCharsets.UTF_8));
        return new ResumeAnalyzer(dictionary()).analyze(resume);
    }

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    // ---- ResumeAnalyzer ----

    private static void analyzerTests() throws Exception {
        System.out.println("== ResumeAnalyzer ==");
        Resume resume = analyzeSample();
        checkEquals("sample resume has 7 skills", 7, resume.getSkills().size());
        checkEquals("sample skills are Java, C++, Python, SQL, JDBC, Git, DSA",
                set("Java", "C++", "Python", "SQL", "JDBC", "Git", "DSA"), resume.getSkills());

        ResumeAnalyzer analyzer = new ResumeAnalyzer(dictionary());
        check("Java does not match JavaScript",
                !analyzer.extractSkills("I know JavaScript well").contains("Java"));
        check("JavaScript is found in JavaScript text",
                analyzer.extractSkills("I know JavaScript well").contains("JavaScript"));
        check("C++ is detected", analyzer.extractSkills("Wrote C++ code.").contains("C++"));
        check("skill matching is case-insensitive", analyzer.extractSkills("python and GIT").size() == 2);
        check("alias CPP maps to C++", analyzer.extractSkills("cpp developer").contains("C++"));
        check("a skill repeated counts once", analyzer.extractSkills("Java Java java").size() == 1);
        check("null text gives no skills", analyzer.extractSkills(null).isEmpty());

        Map<String, List<String>> sections = analyzer.parseSections(
                "JANE DOE\nmail\n\nEDUCATION:\n- B.E. CSE\nSKILLS\n- Java\nProjects\n- not a heading");
        checkEquals("header section holds the lines before the first heading", 2,
                sections.get(ResumeAnalyzer.HEADER).size());
        checkEquals("EDUCATION: with colon is a heading", 1, sections.get(ResumeAnalyzer.EDUCATION).size());
        check("mixed-case 'Projects' is not a heading", sections.get(ResumeAnalyzer.PROJECTS) == null);

        checkThrows("empty resume text throws EmptyResumeException", EmptyResumeException.class, () -> {
            Resume empty = new Resume();
            empty.setResumeText("   \n ");
            analyzer.analyze(empty);
        });
    }

    // ---- ResumeScorer ----

    private static void scorerTests() throws Exception {
        System.out.println("== ResumeScorer ==");
        Resume resume = analyzeSample();
        List<ScoreBreakdown> rows = resume.getBreakdown();
        checkEquals("five score rows", 5, rows.size());
        int[] expected = {32, 16, 13, 7, 10};
        for (int i = 0; i < expected.length; i++) {
            checkEquals(rows.get(i).getLabel() + " points", expected[i], rows.get(i).getPoints());
        }
        checkEquals("total score is 78", 78, resume.getScore());
        checkEquals("CGPA is 8.2", Double.valueOf(8.2), resume.getInfo().getCgpa());
        check("degree detected", resume.getInfo().hasDegree());
        check("no metrics in sample", !resume.getInfo().hasMetrics());
        checkEquals("2 projects", 2, resume.getInfo().getProjects().size());
        checkEquals("2 certifications", 2, resume.getInfo().getCertifications().size());
        checkEquals("1 experience entry", 1, resume.getInfo().getExperience().size());
        checkEquals("education row max is 15", 15, rows.get(2).getMaxPoints());

        ResumeScorer scorer = new ResumeScorer();
        List<String> strengths = scorer.findStrengths(resume.getInfo(), resume.getSkills());
        check("strength mentions Java with JDBC",
                strengths.contains("Good Java knowledge, including database access with JDBC"));
        check("strength: multiple projects", strengths.contains("Multiple projects (2)"));
        List<String> weak = scorer.findWeaknesses(resume.getInfo(), resume.getSkills());
        check("weakness: learn Docker", weak.contains("Learn a deployment tool like Docker"));
        check("no CGPA weakness when CGPA present",
                !weak.contains("Mention your CGPA in the education section"));
    }

    // ---- SkillBasedJobMatcher ----

    private static void matcherTests() throws Exception {
        System.out.println("== SkillBasedJobMatcher ==");
        Set<String> skills = analyzeSample().getSkills();
        SkillBasedJobMatcher matcher = new SkillBasedJobMatcher();
        List<Job> jobs = jobs();
        Job job101 = jobs.get(0);

        checkEquals("job 101 match is 80%", 80, matcher.calculateMatchScore(skills, job101));
        checkEquals("job 101 matched skills", Arrays.asList("Java", "SQL", "JDBC", "Git"),
                matcher.findMatchingSkills(skills, job101));
        checkEquals("job 101 missing required", Arrays.asList("Spring Boot"),
                matcher.findMissingSkills(skills, job101));
        checkEquals("job 101 missing preferred", Arrays.asList("REST API"),
                matcher.findMissingPreferred(skills, job101));
        checkEquals("skill gap vs job 101 is 67%", 67, matcher.calculateSkillGapScore(skills, job101));

        List<JobMatch> ranked = matcher.rankJobs(skills, jobs);
        checkEquals("ranking has 8 jobs", 8, ranked.size());
        checkEquals("rank 1 is job 101", 101, ranked.get(0).getJob().getId());
        checkEquals("rank 2 is job 103", 103, ranked.get(1).getJob().getId());
        checkEquals("rank 2 score is 75", 75, ranked.get(1).getScore());
        checkEquals("tie at 75 broken by title (108 third)", 108, ranked.get(2).getJob().getId());

        List<Integer> javaScores = new ArrayList<>();
        List<Integer> javaIds = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (JobMatch m : ranked) {
            scores.add(m.getScore());
            if (m.getJob().getRequiredSkills().contains("Java")) {
                javaScores.add(m.getScore());
                javaIds.add(m.getJob().getId());
            }
        }
        checkEquals("Java jobs scores are 80, 75, 40, 33", Arrays.asList(80, 75, 40, 33), javaScores);
        checkEquals("Java jobs are 101, 103, 106, 107", Arrays.asList(101, 103, 106, 107), javaIds);

        List<Integer> sorted = new ArrayList<>(scores);
        sorted.sort(Comparator.reverseOrder());
        checkEquals("scores are sorted best first", sorted, scores);

        List<String> top = new ArrayList<>();
        for (Map.Entry<String, Integer> e : matcher.recommendSkills(skills, jobs)) {
            top.add(e.getKey());
        }
        check("recommendations include Spring Boot and Docker",
                top.contains("Spring Boot") && top.contains("Docker"));
        check("at most 5 recommendations", top.size() <= 5);

        Job noRequired = new Job(1, "t", "c", "l", "j", "e", "d", "s",
                new ArrayList<>(), new ArrayList<>());
        checkEquals("job with no required skills scores 0", 0, matcher.calculateMatchScore(skills, noRequired));
        checkEquals("null resume skills match nothing", 0, matcher.calculateMatchScore(null, job101));

        checkEquals("1 of 8 rounds half up to 13", 13, SkillBasedJobMatcher.percentOf(1, 8));
        checkEquals("1 of 3 rounds down to 33", 33, SkillBasedJobMatcher.percentOf(1, 3));
        checkEquals("2 of 3 rounds up to 67", 67, SkillBasedJobMatcher.percentOf(2, 3));
        checkEquals("0 of 0 is 0, not a division error", 0, SkillBasedJobMatcher.percentOf(0, 0));
        checkEquals("3 of 8 jobs is 38%", 38, SkillBasedJobMatcher.percentOf(3, 8));
        checkEquals("score 75 is hi", "hi", JobMatch.levelFor(75));
        checkEquals("score 74 is mid", "mid", JobMatch.levelFor(74));
        checkEquals("score 50 is mid", "mid", JobMatch.levelFor(50));
        checkEquals("score 49 is lo", "lo", JobMatch.levelFor(49));
        checkEquals("a JobMatch reports the level of its score", "hi",
                matcher.match(skills, job101).getLevel());
    }

    // ---- ValidationUtil ----

    private static void validationTests() throws Exception {
        System.out.println("== ValidationUtil ==");
        check("null is blank", ValidationUtil.isBlank(null));
        check("spaces are blank", ValidationUtil.isBlank("   "));
        check("text is not blank", !ValidationUtil.isBlank(" a "));
        check("valid email", ValidationUtil.isValidEmail("name@college.edu"));
        check("email without @ rejected", !ValidationUtil.isValidEmail("name.college.edu"));
        check("email without domain dot rejected", !ValidationUtil.isValidEmail("name@college"));
        check("email with space rejected", !ValidationUtil.isValidEmail("na me@college.edu"));
        check("6-char password ok", ValidationUtil.isValidPassword("abcdef"));
        check("5-char password rejected", !ValidationUtil.isValidPassword("abcde"));
        check("null password rejected", !ValidationUtil.isValidPassword(null));
        check(".txt accepted", ValidationUtil.isTxtFile("resume.txt"));
        check(".TXT accepted", ValidationUtil.isTxtFile("RESUME.TXT"));
        check(".pdf rejected", !ValidationUtil.isTxtFile("resume.pdf"));
        check("txt without dot rejected", !ValidationUtil.isTxtFile("resumetxt"));
        check("200 KB is within limit", ValidationUtil.isWithinSizeLimit(200L * 1024));
        check("200 KB + 1 is over limit", !ValidationUtil.isWithinSizeLimit(200L * 1024 + 1));
        checkEquals("blank name error", "Enter your full name.", ValidationUtil.nameError(""));
        check("valid name has no error", ValidationUtil.nameError("Arun") == null);
        checkEquals("blank email error", "Enter your email.", ValidationUtil.emailError(" "));
        check("valid email has no error", ValidationUtil.emailError("a@b.com") == null);
        checkEquals("mismatched passwords", "Passwords don't match.",
                ValidationUtil.confirmPasswordError("abcdef", "abcdeg"));
        check("matching passwords no error", ValidationUtil.confirmPasswordError("abcdef", "abcdef") == null);
        checkThrows("requireField throws on blank", MissingFieldException.class,
                () -> ValidationUtil.requireField(" ", "email", "Enter your email."));
        checkEquals("requireField trims", "x", ValidationUtil.requireField(" x ", "f", "m"));
    }

    // ---- FileService ----

    private static void fileServiceTests() throws Exception {
        System.out.println("== FileService ==");
        Path temp = Files.createTempDirectory("resumematch-test");
        try {
            FileService files = new FileService(temp);

            files.validate("resume.txt", 100);
            check("good name and size validate", true);
            checkThrows("pdf name rejected", InvalidFileException.class, () -> files.validate("resume.pdf", 100));
            checkThrows("zero bytes rejected", EmptyResumeException.class, () -> files.validate("resume.txt", 0));
            checkThrows("over 200 KB rejected", InvalidFileException.class,
                    () -> files.validate("resume.txt", 200L * 1024 + 1));
            files.validate("resume.txt", 200L * 1024);
            check("exactly 200 KB accepted", true);
            checkThrows("blank name rejected", MissingFieldException.class, () -> files.validate("  ", 10));

            try {
                files.validate("resume.pdf", 10);
            } catch (AppException e) {
                check("not-txt message names the file", e.getUserMessage().contains("resume.pdf")
                        && e.getUserMessage().contains("isn't a .txt file"));
            }
            try {
                files.validate("resume.txt", 300L * 1024);
            } catch (AppException e) {
                check("too-large message says 200 KB", e.getUserMessage().contains("larger than 200 KB"));
            }

            byte[] data = "Java and SQL resume".getBytes(StandardCharsets.UTF_8);
            Path saved = files.saveResume(7, "my resume.txt", data.length, new ByteArrayInputStream(data));
            check("saved file exists", Files.exists(saved));
            check("saved under user_7", saved.getParent().getFileName().toString().equals("user_7"));
            check("saved name is server-generated, not the submitted name",
                    saved.getFileName().toString().matches("[0-9]+_[0-9a-f]{12}[.]txt"));
            checkEquals("read returns the same text", "Java and SQL resume", files.readResume(saved));

            Path second = files.saveResume(7, "my resume.txt", data.length, new ByteArrayInputStream(data));
            check("second upload with same name is a separate file", !second.equals(saved) && Files.exists(saved));

            check("delete returns true", files.deleteResume(saved));
            check("file is gone after delete", !Files.exists(saved));
            check("deleting a missing file returns false", !files.deleteResume(saved));
            check("deleting null returns false", !files.deleteResume(null));

            // path traversal attempts must stay inside the base folder
            String[] evil = {"../x.txt", "..\\x.txt", "../../etc/passwd.txt", "a/../../x.txt"};
            Path realBase = temp.toRealPath();
            for (String name : evil) {
                Path p = files.saveResume(8, name, data.length, new ByteArrayInputStream(data));
                check("traversal name '" + name + "' stays inside the user folder",
                        p.toRealPath().getParent().equals(realBase.resolve("user_8")));
                check("traversal name '" + name + "' loses the folder part",
                        !p.getFileName().toString().contains(".."));
            }
            check("nothing written outside the base folder", !Files.exists(temp.resolveSibling("x.txt")));

            checkThrows("saving an empty stream throws EmptyResumeException", EmptyResumeException.class,
                    () -> files.saveResume(9, "e.txt", -1, new ByteArrayInputStream(new byte[0])));
            checkThrows("saving with a bad extension throws", InvalidFileException.class,
                    () -> files.saveResume(9, "e.pdf", 5, new ByteArrayInputStream(data)));
            check("rejected uploads leave no file",
                    !Files.exists(temp.resolve("user_9")) || listCount(temp.resolve("user_9")) == 0);

            Path blank = temp.resolve("blank.txt");
            Files.write(blank, "   \n".getBytes(StandardCharsets.UTF_8));
            checkThrows("reading a blank file throws EmptyResumeException", EmptyResumeException.class,
                    () -> files.readResume(blank));
            checkThrows("reading a missing file throws FileReadException", FileReadException.class,
                    () -> files.readResume(temp.resolve("nope.txt")));
        } finally {
            deleteTree(temp);
        }
    }

    private static long listCount(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of the temp folder
                }
            });
        }
    }
}
