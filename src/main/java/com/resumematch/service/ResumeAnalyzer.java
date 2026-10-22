package com.resumematch.service;

import com.resumematch.exception.EmptyResumeException;
import com.resumematch.model.Resume;
import com.resumematch.model.ResumeInfo;
import com.resumematch.model.ScoreBreakdown;
import com.resumematch.model.Skill;
import com.resumematch.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a resume's text with fixed, rule-based steps: splits it into sections by their
 * uppercase headings, finds skills by literal text matching against the skill dictionary,
 * and pulls out the name, contact line, degree, CGPA and the bullet items of each section.
 *
 * <p>Skill matching is case-insensitive and only matches whole words: the characters
 * before and after a match must not be a letter, digit, '+' or '#'. So "Java" is not found
 * inside "JavaScript", "C++" is found, and names with dots like "Node.js" are matched
 * literally because every alias is quoted with {@link Pattern#quote(String)}.</p>
 *
 * <p>Demonstrates: {@code HashMap<String, Skill>} (the skill dictionary, keyed by name),
 * {@code HashSet<String>} (skills found, each counted once), {@code HashMap<String,
 * List<String>>} (sections), {@code ArrayList}, {@code java.util.regex} ({@code Pattern},
 * {@code Matcher}), String handling, custom checked exceptions.</p>
 */
public class ResumeAnalyzer {

    /** Section key for the lines before the first heading (name and contact line). */
    public static final String HEADER = "header";
    public static final String EDUCATION = "education";
    public static final String SKILLS = "skills";
    public static final String PROJECTS = "projects";
    public static final String CERTIFICATIONS = "certifications";
    public static final String EXPERIENCE = "experience";

    /** Uppercase heading text mapped to its section key. */
    private static final Map<String, String> SECTION_HEADINGS = new HashMap<>();

    static {
        SECTION_HEADINGS.put("EDUCATION", EDUCATION);
        SECTION_HEADINGS.put("ACADEMICS", EDUCATION);
        SECTION_HEADINGS.put("SKILLS", SKILLS);
        SECTION_HEADINGS.put("TECHNICAL SKILLS", SKILLS);
        SECTION_HEADINGS.put("PROJECTS", PROJECTS);
        SECTION_HEADINGS.put("ACADEMIC PROJECTS", PROJECTS);
        SECTION_HEADINGS.put("CERTIFICATIONS", CERTIFICATIONS);
        SECTION_HEADINGS.put("CERTIFICATES", CERTIFICATIONS);
        SECTION_HEADINGS.put("EXPERIENCE", EXPERIENCE);
        SECTION_HEADINGS.put("WORK EXPERIENCE", EXPERIENCE);
        SECTION_HEADINGS.put("INTERNSHIPS", EXPERIENCE);
    }

    /** A bullet line starts with '-', a bullet dot (U+2022) or '*'. */
    private static final Pattern BULLET = Pattern.compile("^[-•*]\\s*");

    /** A trailing colon and/or spaces after a heading, e.g. "SKILLS:". */
    private static final Pattern TRAILING_COLON = Pattern.compile("[:\\s]+$");

    private static final Pattern DEGREE = Pattern.compile(
            "\\b(B\\.?\\s?E|B\\.?\\s?Tech|M\\.?\\s?E|M\\.?\\s?Tech|B\\.?\\s?Sc|M\\.?\\s?Sc|BCA|MCA)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CGPA = Pattern.compile(
            "CGPA\\s*[:\\-]?\\s*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    /** A percentage, or a number followed by a unit that shows a measurable result. */
    private static final Pattern METRICS = Pattern.compile(
            "\\d+(\\.\\d+)?\\s*%|\\b\\d+\\+?\\s*(users|records|requests|ms|seconds|x faster|downloads)\\b",
            Pattern.CASE_INSENSITIVE);

    /** The skill dictionary keyed by canonical skill name. */
    private final Map<String, Skill> dictionary = new HashMap<>();

    /** For each canonical skill name, one compiled pattern per spelling (name + aliases). */
    private final Map<String, List<Pattern>> skillPatterns = new HashMap<>();

    /** Turns the extracted facts into the five score rows (composition). */
    private final ResumeScorer scorer = new ResumeScorer();

    /**
     * @param skills the skill dictionary, normally loaded by {@code SkillDAO.findAll()}
     */
    public ResumeAnalyzer(List<Skill> skills) {
        if (skills == null) {
            return;
        }
        for (Skill skill : skills) {
            if (skill == null || ValidationUtil.isBlank(skill.getName())) {
                continue;
            }
            String name = skill.getName().trim();
            dictionary.put(name, skill);

            List<Pattern> patterns = new ArrayList<>();
            patterns.add(skillPattern(name));
            for (String alias : skill.getAliases()) {
                if (!ValidationUtil.isBlank(alias) && !alias.trim().equalsIgnoreCase(name)) {
                    patterns.add(skillPattern(alias.trim()));
                }
            }
            skillPatterns.put(name, patterns);
        }
    }

    /**
     * Builds the whole-word, case-insensitive pattern for one spelling of a skill.
     * '+' and '#' count as word characters, so "C++" works and "Java" does not match
     * "JavaScript". {@code Pattern.quote} makes '.', '+' and other symbols literal.
     */
    public static Pattern skillPattern(String alias) {
        return Pattern.compile("(?<![A-Za-z0-9+#])" + Pattern.quote(alias) + "(?![A-Za-z0-9+#])",
                Pattern.CASE_INSENSITIVE);
    }

    /** The skill dictionary this analyzer matches against (read-only view). */
    public Collection<Skill> getDictionary() {
        return Collections.unmodifiableCollection(dictionary.values());
    }

    /**
     * Splits the text into sections. A line is a heading when, after trimming and dropping a
     * trailing colon, it is one of the known headings and is written in uppercase. Lines
     * before the first heading go to {@value #HEADER}. Blank lines are skipped.
     *
     * @return section key mapped to its lines, in file order; always contains {@value #HEADER}
     */
    public Map<String, List<String>> parseSections(String text) {
        Map<String, List<String>> sections = new HashMap<>();
        sections.put(HEADER, new ArrayList<>());
        if (text == null) {
            return sections;
        }
        String current = HEADER;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String key = TRAILING_COLON.matcher(line).replaceAll("").toUpperCase();
            String section = SECTION_HEADINGS.get(key);
            if (section != null && line.equals(line.toUpperCase())) {
                current = section;
                sections.computeIfAbsent(current, k -> new ArrayList<>());
                continue;
            }
            sections.computeIfAbsent(current, k -> new ArrayList<>()).add(line);
        }
        return sections;
    }

    /**
     * Finds every dictionary skill whose name or one of its aliases appears in the text as a
     * whole word, ignoring case. The canonical name is added once, however often it appears.
     *
     * @return the canonical names of the skills found ({@code HashSet}, O(1) {@code contains})
     */
    public Set<String> extractSkills(String text) {
        Set<String> found = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        for (Map.Entry<String, List<Pattern>> entry : skillPatterns.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(text).find()) {
                    found.add(entry.getKey());
                    break; // first spelling that hits is enough
                }
            }
        }
        return found;
    }

    /**
     * Pulls the facts the score needs out of the parsed sections: name (first header line,
     * in title case), contact (second header line), the bullet items of each section,
     * whether a degree is listed, the CGPA and whether projects or experience show a
     * measurable result.
     */
    public ResumeInfo extractInfo(Map<String, List<String>> sections, String text) {
        List<String> header = linesOf(sections, HEADER);
        String name = header.isEmpty() ? "Unknown candidate" : toTitleCase(header.get(0));
        String contact = (header.size() > 1) ? header.get(1) : "";

        boolean hasDegree = DEGREE.matcher(String.join(" ", linesOf(sections, EDUCATION))).find();

        Double cgpa = null;
        if (text != null) {
            Matcher m = CGPA.matcher(text);
            if (m.find()) {
                cgpa = Double.valueOf(m.group(1));
            }
        }

        List<String> projectAndExperience = new ArrayList<>(linesOf(sections, PROJECTS));
        projectAndExperience.addAll(linesOf(sections, EXPERIENCE));
        boolean hasMetrics = METRICS.matcher(String.join(" ", projectAndExperience)).find();

        return new ResumeInfo(name, contact, hasDegree, cgpa,
                bullets(sections, EDUCATION), bullets(sections, PROJECTS),
                bullets(sections, CERTIFICATIONS), bullets(sections, EXPERIENCE),
                hasMetrics);
    }

    /**
     * Runs the whole analysis on {@code resume.getResumeText()} and stores the results back on
     * the same object: the skills found, the extracted {@link ResumeInfo}, the five score rows
     * from {@link ResumeScorer} and the total score.
     *
     * @return the same resume, filled in
     * @throws EmptyResumeException when the text is missing or only whitespace
     */
    public Resume analyze(Resume resume) throws EmptyResumeException {
        if (resume == null || ValidationUtil.isBlank(resume.getResumeText())) {
            throw new EmptyResumeException();
        }
        String text = resume.getResumeText();
        Map<String, List<String>> sections = parseSections(text);
        resume.setSkills(extractSkills(text));
        resume.setInfo(extractInfo(sections, text));

        List<ScoreBreakdown> rows = scorer.calculateScore(resume.getInfo(), resume.getSkills());
        resume.setBreakdown(rows);
        resume.setScore(scorer.totalScore(rows));
        return resume;
    }

    /** The bullet items of a section with the bullet character removed. */
    private static List<String> bullets(Map<String, List<String>> sections, String key) {
        List<String> items = new ArrayList<>();
        for (String line : linesOf(sections, key)) {
            Matcher m = BULLET.matcher(line);
            if (m.find()) {
                items.add(line.substring(m.end()));
            }
        }
        return items;
    }

    private static List<String> linesOf(Map<String, List<String>> sections, String key) {
        if (sections == null) {
            return new ArrayList<>();
        }
        List<String> lines = sections.get(key);
        return (lines == null) ? new ArrayList<>() : lines;
    }

    /** "ARUN KUMAR" becomes "Arun Kumar": lower-case, then capitalise each word's first letter. */
    private static String toTitleCase(String line) {
        char[] chars = line.toLowerCase().toCharArray();
        boolean startOfWord = true;
        for (int i = 0; i < chars.length; i++) {
            boolean wordChar = Character.isLetterOrDigit(chars[i]) || chars[i] == '_';
            if (wordChar && startOfWord) {
                chars[i] = Character.toUpperCase(chars[i]);
            }
            startOfWord = !wordChar;
        }
        return new String(chars);
    }
}
