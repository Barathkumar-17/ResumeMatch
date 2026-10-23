package com.resumematch;

import static com.resumematch.TestRunner.check;
import static com.resumematch.TestRunner.checkEquals;

import com.resumematch.model.Job;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.ResumeInfo;
import com.resumematch.model.ScoreBreakdown;
import com.resumematch.model.User;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.service.ResumeScorer;
import com.resumematch.service.SkillBasedJobMatcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edge-case checks for the rule-based engine: section parsing with odd spacing and line
 * endings, skill matching at word boundaries, degree, CGPA and measurable-result detection,
 * the caps of every score row, ranking ties and recommendation order, and the rule that the
 * score page and the dashboard show the same number.
 *
 * <p>Demonstrates: plain-Java unit tests with fixed inputs (no database, no clock, no
 * randomness), {@code List}, {@code Set} and {@code Map} comparisons.</p>
 */
final class LogicTests {

    private static final String SAMPLE = "sample-data/arun_kumar_resume.txt";

    private LogicTests() {
        // test holder, no instances
    }

    static void run() throws Exception {
        sectionTests();
        skillMatchingTests();
        infoTests();
        scoreCapTests();
        consistencyTests();
        matcherTests();
        modelTests();
    }

    private static ResumeAnalyzer analyzer() {
        return new ResumeAnalyzer(TestRunner.dictionary());
    }

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    // ---- section parsing ----

    private static void sectionTests() throws Exception {
        System.out.println("== Section parsing edge cases ==");
        ResumeAnalyzer a = analyzer();

        String sample = new String(Files.readAllBytes(Paths.get(SAMPLE)), StandardCharsets.UTF_8);
        for (String eol : new String[] {"\r\n", "\r"}) {
            Resume resume = new Resume();
            resume.setResumeText(sample.replace("\n", eol));
            a.analyze(resume);
            String label = eol.equals("\r\n") ? "Windows (CRLF)" : "old Mac (CR)";
            checkEquals(label + " line endings still score 78", 78, resume.getScore());
            checkEquals(label + " line endings still find 7 skills", 7, resume.getSkills().size());
        }

        Map<String, List<String>> s = a.parseSections(
                "JANE\nmail\nTECHNICAL   SKILLS :\n- Java\nWORK EXPERIENCE\n- Intern\n﻿EDUCATION\n- B.E.");
        checkEquals("double space and a space before the colon still make a heading", 1,
                s.get(ResumeAnalyzer.SKILLS).size());
        checkEquals("a non-breaking space inside a heading is treated as a space", 1,
                s.get(ResumeAnalyzer.EXPERIENCE).size());
        checkEquals("a byte order mark before a heading is ignored", 1,
                s.get(ResumeAnalyzer.EDUCATION).size());
        checkEquals("header keeps only the lines before the first heading", 2,
                s.get(ResumeAnalyzer.HEADER).size());

        Map<String, List<String>> lower = a.parseSections("NAME\neducation\n- B.E.\nSKILLS\n- Java");
        check("a lowercase heading is not a heading (the rule needs capitals)",
                lower.get(ResumeAnalyzer.EDUCATION) == null);
        checkEquals("so its lines stay in the section above it", 3, lower.get(ResumeAnalyzer.HEADER).size());

        Map<String, List<String>> alias = a.parseSections("X\nACADEMICS\n- a\nINTERNSHIPS\n- b\nCERTIFICATES\n- c\nACADEMIC PROJECTS\n- d");
        check("ACADEMICS, INTERNSHIPS, CERTIFICATES and ACADEMIC PROJECTS are headings",
                alias.get(ResumeAnalyzer.EDUCATION).size() == 1 && alias.get(ResumeAnalyzer.EXPERIENCE).size() == 1
                && alias.get(ResumeAnalyzer.CERTIFICATIONS).size() == 1 && alias.get(ResumeAnalyzer.PROJECTS).size() == 1);

        check("null text gives only an empty header", a.parseSections(null).get(ResumeAnalyzer.HEADER).isEmpty());
        check("only blank lines give an empty header", a.parseSections("  \n\t\n").get(ResumeAnalyzer.HEADER).isEmpty());

        ResumeInfo info = a.readInfo("ARUN KUMAR\nmail\nPROJECTS\n- One: x\nplain line\n* Two: y\n• Three: z");
        checkEquals("bullets -, * and the bullet dot all count; plain lines do not", 3, info.getProjects().size());
        checkEquals("the bullet character is removed", "One: x", info.getProjects().get(0));
        checkEquals("the name is shown in title case", "Arun Kumar", info.getName());
        checkEquals("a resume with no text gets a placeholder name", "Unknown candidate",
                a.readInfo("").getName());
    }

    // ---- skill matching ----

    private static void skillMatchingTests() {
        System.out.println("== Skill matching edge cases ==");
        ResumeAnalyzer a = analyzer();

        check("Java/JavaScript: both found, each by its own word",
                a.extractSkills("Java and JavaScript").equals(set("Java", "JavaScript")));
        check("Java inside 'Java/Kotlin' is found", a.extractSkills("Java/Kotlin").contains("Java"));
        check("punctuation after a skill is fine: 'SQL.' 'SQL,' '(SQL)'",
                a.extractSkills("SQL. then (SQL), SQL;").contains("SQL"));
        check("C++ followed by a comma or full stop is found", a.extractSkills("C++, Java.").contains("C++"));
        check("C++11 is a different word, not C++", !a.extractSkills("uses C++11 only").contains("C++"));
        check("MySQL is not SQL", a.extractSkills("MySQL").equals(set("MySQL")));
        check("NoSQL and PostgreSQL are not SQL", a.extractSkills("NoSQL, PostgreSQL").isEmpty());
        check("SQLite is not SQL", a.extractSkills("SQLite").isEmpty());
        check("Python3 is a longer word, not Python", a.extractSkills("Python3").isEmpty());
        check("Git is not found inside GitHub's word, but the GitHub alias finds Git",
                a.extractSkills("GitHub").equals(set("Git")));
        check("digits and letters around a skill block it: 'xJava'", a.extractSkills("xJava").isEmpty());
        check("plural alias 'REST APIs' maps to REST API", a.extractSkills("built REST APIs").contains("REST API"));
        check("alias RESTful maps to REST API", a.extractSkills("RESTful services").contains("REST API"));
        check("alias 'Data Structures' maps to DSA", a.extractSkills("Data Structures").contains("DSA"));
        check("a skill split over a line break is found: 'Spring\\nBoot'",
                a.extractSkills("Spring\nBoot").contains("Spring Boot"));
        check("a skill with a double space is found: 'Power  BI'",
                a.extractSkills("Power  BI").contains("Power BI"));
        check("tab between words is found", a.extractSkills("MS\tExcel").contains("Excel"));
        check("a word that only starts like the skill is not found: 'Spring Boots'",
                !a.extractSkills("Spring Boots").contains("Spring Boot"));
        check("blank text gives no skills", a.extractSkills("   ").isEmpty());
        check("an empty dictionary finds nothing", new ResumeAnalyzer(null).extractSkills("Java").isEmpty());
        check("a blank dictionary name is ignored",
                new ResumeAnalyzer(Collections.singletonList(new com.resumematch.model.Skill(1, " ", "x", null)))
                        .extractSkills("anything").isEmpty());
    }

    // ---- degree, CGPA, measurable results ----

    private static void infoTests() {
        System.out.println("== Degree, CGPA and measurable results ==");
        ResumeAnalyzer a = analyzer();

        check("B.E. is a degree", a.readInfo("N\nEDUCATION\n- B.E. CSE").hasDegree());
        check("BE without dots is a degree", a.readInfo("N\nEDUCATION\n- BE Computer Science").hasDegree());
        check("b.tech in lowercase is a degree", a.readInfo("N\nEDUCATION\n- b.tech IT").hasDegree());
        check("M.Sc, MCA and BCA are degrees", a.readInfo("N\nEDUCATION\n- M.Sc Data").hasDegree()
                && a.readInfo("N\nEDUCATION\n- MCA").hasDegree() && a.readInfo("N\nEDUCATION\n- BCA").hasDegree());
        check("the plain words 'be' and 'me' are not a degree",
                !a.readInfo("N\nEDUCATION\n- Will be completed, ask me").hasDegree());
        check("a degree outside the EDUCATION section does not count",
                !a.readInfo("N\nSKILLS\n- B.E.").hasDegree());

        checkEquals("CGPA 8.2", Double.valueOf(8.2), a.readInfo("N\nEDUCATION\n- CGPA: 8.2 / 10").getCgpa());
        checkEquals("cgpa in lowercase with a dash", Double.valueOf(9.1),
                a.readInfo("N\nEDUCATION\n- cgpa-9.1").getCgpa());
        checkEquals("a whole-number CGPA", Double.valueOf(9), a.readInfo("N\nEDUCATION\n- CGPA 9").getCgpa());
        check("no CGPA gives null", a.readInfo("N\nEDUCATION\n- B.E.").getCgpa() == null);
        check("a CGPA anywhere in the text is found", a.readInfo("N CGPA 7.5\nEDUCATION\n- B.E.").getCgpa() != null);

        check("a percentage is a measurable result", a.readInfo("N\nPROJECTS\n- Cut load time by 30%").hasMetrics());
        check("'500+ users' is a measurable result", a.readInfo("N\nPROJECTS\n- Served 500+ users").hasMetrics());
        check("'99.9 %' with a space is a measurable result", a.readInfo("N\nPROJECTS\n- Uptime 99.9 %").hasMetrics());
        check("'3x faster' is a measurable result", a.readInfo("N\nEXPERIENCE\n- Made it 3x faster").hasMetrics());
        check("a bare number is not a measurable result", !a.readInfo("N\nPROJECTS\n- Built 2 modules").hasMetrics());
        check("a percentage outside projects and experience does not count",
                !a.readInfo("N\nEDUCATION\n- 90%\nPROJECTS\n- Built a tool").hasMetrics());
    }

    // ---- score caps and rounding ----

    private static ResumeInfo info(int projects, int certs, int exp, boolean degree, Double cgpa, boolean metrics) {
        return new ResumeInfo("N", "c", degree, cgpa, new ArrayList<>(), items(projects), items(certs),
                items(exp), metrics);
    }

    private static List<String> items(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add("item " + i);
        }
        return list;
    }

    private static Set<String> skills(int n) {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < n; i++) {
            s.add("skill" + i);
        }
        return s;
    }

    private static void scoreCapTests() {
        System.out.println("== Score caps and rounding ==");
        ResumeScorer scorer = new ResumeScorer();

        List<ScoreBreakdown> top = scorer.calculateScore(info(5, 5, 5, true, 9.5, true), skills(18));
        checkEquals("skills are capped at 40", 40, top.get(0).getPoints());
        checkEquals("projects are capped at 20 (16 + 4)", 20, top.get(1).getPoints());
        checkEquals("education is capped at 15", 15, top.get(2).getPoints());
        checkEquals("certifications are capped at 10", 10, top.get(3).getPoints());
        checkEquals("experience is capped at 15", 15, top.get(4).getPoints());
        checkEquals("the best possible resume scores exactly 100", 100, scorer.totalScore(top));

        List<ScoreBreakdown> empty = scorer.calculateScore(info(0, 0, 0, false, null, false), Collections.emptySet());
        checkEquals("an empty resume scores 0", 0, scorer.totalScore(empty));
        checkEquals("null skills count as none", 0,
                scorer.totalScore(scorer.calculateScore(info(0, 0, 0, false, null, false), null)));

        checkEquals("1 skill rounds 4.5 up to 5", 5, scorer.calculateScore(info(0, 0, 0, false, null, false), skills(1)).get(0).getPoints());
        checkEquals("9 skills give full 40 (40.5 is capped)", 40, scorer.calculateScore(info(0, 0, 0, false, null, false), skills(9)).get(0).getPoints());
        checkEquals("8 skills give 36", 36, scorer.calculateScore(info(0, 0, 0, false, null, false), skills(8)).get(0).getPoints());
        checkEquals("1 certification rounds 3.5 up to 4", 4, scorer.calculateScore(info(0, 1, 0, false, null, false), skills(0)).get(3).getPoints());
        checkEquals("2 certifications give 7", 7, scorer.calculateScore(info(0, 2, 0, false, null, false), skills(0)).get(3).getPoints());
        checkEquals("3 certifications round 10.5 to 11 and are capped at 10", 10, scorer.calculateScore(info(0, 3, 0, false, null, false), skills(0)).get(3).getPoints());
        checkEquals("1 experience entry gives 10", 10, scorer.calculateScore(info(0, 0, 1, false, null, false), skills(0)).get(4).getPoints());
        checkEquals("2 experience entries are capped at 15", 15, scorer.calculateScore(info(0, 0, 2, false, null, false), skills(0)).get(4).getPoints());
        checkEquals("metrics alone add 4 to projects", 4, scorer.calculateScore(info(0, 0, 0, false, null, true), skills(0)).get(1).getPoints());
        checkEquals("1 project gives 8", 8, scorer.calculateScore(info(1, 0, 0, false, null, false), skills(0)).get(1).getPoints());

        checkEquals("CGPA 8.5 gives +5", 5, scorer.calculateScore(info(0, 0, 0, false, 8.5, false), skills(0)).get(2).getPoints());
        checkEquals("CGPA 8.49 gives only +3", 3, scorer.calculateScore(info(0, 0, 0, false, 8.49, false), skills(0)).get(2).getPoints());
        checkEquals("degree alone gives 10", 10, scorer.calculateScore(info(0, 0, 0, true, null, false), skills(0)).get(2).getPoints());
        checkEquals("degree with a low CGPA gives 13", 13, scorer.calculateScore(info(0, 0, 0, true, 6.0, false), skills(0)).get(2).getPoints());
        checkEquals("education reason shows a whole CGPA without .0", "Degree found (+10); CGPA 9 ≥ 8.5 (+5)",
                scorer.calculateScore(info(0, 0, 0, true, 9.0, false), skills(0)).get(2).getReason());
        checkEquals("education reason for a low CGPA", "Degree found (+10); CGPA 8.2 listed (+3)",
                scorer.calculateScore(info(0, 0, 0, true, 8.2, false), skills(0)).get(2).getReason());
        checkEquals("singular wording for one project", "1 project × 8 points, max 16; +4 for measurable results (found)",
                scorer.calculateScore(info(1, 0, 0, false, null, true), skills(0)).get(1).getReason());

        checkEquals("a full bar is 100 percent", 100, top.get(0).getPercent());
        checkEquals("32 of 40 is 80 percent", 80, new ScoreBreakdown("x", 32, 40, "").getPercent());
        checkEquals("7 of 10 is 70 percent", 70, new ScoreBreakdown("x", 7, 10, "").getPercent());
        checkEquals("13 of 15 rounds to 87 percent", 87, new ScoreBreakdown("x", 13, 15, "").getPercent());
        checkEquals("a bar with max 0 is 0 percent, not a division error", 0,
                new ScoreBreakdown("x", 0, 0, "").getPercent());
    }

    // ---- score page and dashboard agree ----

    private static void consistencyTests() throws Exception {
        System.out.println("== Stored score and score page agree ==");
        ResumeAnalyzer a = analyzer();
        String text = new String(Files.readAllBytes(Paths.get(SAMPLE)), StandardCharsets.UTF_8);
        Resume resume = new Resume();
        resume.setResumeText(text);
        a.analyze(resume);

        // The score page reads the stored text again and scores it with the stored skills.
        ResumeInfo reread = a.readInfo(text);
        ResumeScorer scorer = new ResumeScorer();
        int rebuilt = scorer.totalScore(scorer.calculateScore(reread, resume.getSkills()));
        checkEquals("re-reading the stored text gives the stored score (78)", resume.getScore(), rebuilt);

        Resume priya = new Resume();
        priya.setResumeText(new String(Files.readAllBytes(Paths.get("sample-data/priya_sharma_resume.txt")),
                StandardCharsets.UTF_8));
        a.analyze(priya);
        checkEquals("Priya's resume scores 75", 75, priya.getScore());
        checkEquals("Priya's score is the same when rebuilt from stored skills", 75,
                scorer.totalScore(scorer.calculateScore(a.readInfo(priya.getResumeText()), priya.getSkills())));
    }

    // ---- matcher ----

    private static Job job(int id, String title, String required, String preferred) {
        return new Job(id, title, "Co", "City", "Internship", "Fresher", "", "",
                new ArrayList<>(required.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(required.split(","))),
                new ArrayList<>(preferred.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(preferred.split(","))));
    }

    private static void matcherTests() {
        System.out.println("== Ranking, ties and recommendations ==");
        SkillBasedJobMatcher m = new SkillBasedJobMatcher();
        Set<String> have = set("Java");

        List<Job> jobs = Arrays.asList(
                job(1, "zeta dev", "Java,SQL", ""),
                job(2, "Alpha dev", "Java,SQL", ""),
                job(3, "beta dev", "Java,SQL", ""),
                job(4, "Top", "Java", ""));
        List<JobMatch> ranked = m.rankJobs(have, jobs);
        checkEquals("the 100 percent job comes first", 4, ranked.get(0).getJob().getId());
        checkEquals("equal scores sort by title, ignoring case: Alpha", 2, ranked.get(1).getJob().getId());
        checkEquals("equal scores sort by title, ignoring case: beta", 3, ranked.get(2).getJob().getId());
        checkEquals("equal scores sort by title, ignoring case: zeta", 1, ranked.get(3).getJob().getId());
        check("ranking null jobs gives an empty list", m.rankJobs(have, null).isEmpty());
        check("ranking no jobs gives an empty list", m.rankJobs(have, new ArrayList<>()).isEmpty());

        Job none = job(9, "Nothing listed", "", "");
        checkEquals("a job with no skills has a skill gap of 0", 0, m.calculateSkillGapScore(have, none));
        checkEquals("a job with only preferred skills has a match score of 0", 0,
                m.calculateMatchScore(have, job(8, "P", "", "Java")));
        checkEquals("...but its skill gap counts the preferred skill", 100,
                m.calculateSkillGapScore(have, job(8, "P", "", "Java")));
        checkEquals("preferred skills never change the match score", 50,
                m.calculateMatchScore(have, job(7, "Q", "Java,SQL", "Java,Git")));

        check("no jobs give no recommendations", m.recommendSkills(have, null).isEmpty());
        check("no missing skills give no recommendations",
                m.recommendSkills(set("Java", "SQL"), jobs).isEmpty());

        // Docker is needed by two jobs, Linux by one; weights: Docker = 50 + 50, Linux = 50.
        List<Job> two = Arrays.asList(job(1, "A", "Java,Docker,Linux", ""), job(2, "B", "Java,Docker", ""));
        List<Map.Entry<String, Integer>> rec = m.recommendSkills(have, two);
        checkEquals("the skill needed by more jobs is recommended first", "Docker", rec.get(0).getKey());
        checkEquals("its count is the number of jobs that require it", Integer.valueOf(2), rec.get(0).getValue());

        // A skill of an almost-fitting job beats a skill of a job that barely fits.
        List<Job> weighted = Arrays.asList(job(1, "Near", "Java,Flask", ""), job(2, "Far", "SQL,Git,Docker,Linux,Excel", ""));
        List<Map.Entry<String, Integer>> rec2 = m.recommendSkills(have, weighted);
        checkEquals("skills of the job you nearly fit come first", "Flask", rec2.get(0).getKey());

        // Missing skills of equal weight and count are ordered by name.
        List<Map.Entry<String, Integer>> rec3 = m.recommendSkills(set("Java"),
                Collections.singletonList(job(1, "J", "Java,Zed,Abe", "")));
        checkEquals("equal weight and count are ordered by name", "Abe", rec3.get(0).getKey());

        List<Job> many = new ArrayList<>();
        many.add(job(1, "Big", "Java,A,B,C,D,E,F,G", ""));
        checkEquals("at most 5 recommendations", 5, m.recommendSkills(have, many).size());

        JobMatch match = m.match(set("Java"), job(1, "J", "Java,SQL,Git", "Docker,Linux"));
        checkEquals("missing required skills as one line", "SQL, Git", match.getMissingText());
        checkEquals("missing preferred skills as one line", "Docker, Linux", match.getMissingPreferredText());
        checkEquals("nothing missing gives an empty line", "",
                m.match(set("Java"), job(1, "J", "Java", "")).getMissingText());
    }

    // ---- model helpers shown in the pages ----

    private static void modelTests() {
        System.out.println("== Model helpers ==");
        String dbMessage = com.resumematch.console.ConsoleDemo.failureMessage(
                new com.resumematch.exception.DatabaseConnectionException(new java.sql.SQLException("Access denied for user 'root'")));
        check("console demo database failure names MySQL and db.properties",
                dbMessage.contains("MySQL is running") && dbMessage.contains("db.properties"));
        check("console demo database failure shows no SQL error text",
                !dbMessage.contains("Access denied") && !dbMessage.contains("SQLException"));
        check("console demo file failure has no database hint",
                !com.resumematch.console.ConsoleDemo.failureMessage(new com.resumematch.exception.FileReadException())
                        .contains("MySQL"));
        checkEquals("avatar initial is the first letter in capitals", "A", new User(1, "arun kumar", "e", null).getInitial());
        checkEquals("avatar initial ignores leading spaces", "A", new User(1, "  arun", "e", null).getInitial());
        checkEquals("avatar shows ? for a blank name", "?", new User(1, "   ", "e", null).getInitial());
        checkEquals("avatar shows ? for a missing name", "?", new User(1, null, "e", null).getInitial());
        checkEquals("avatar keeps a whole emoji character", "😀", new User(1, "😀 smile", "e", null).getInitial());
    }
}
