package com.resumematch.web;

import com.resumematch.dao.ResumeDAO;
import com.resumematch.dao.SkillDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.Resume;
import com.resumematch.model.ResumeInfo;
import com.resumematch.model.Skill;
import com.resumematch.model.User;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.util.HtmlUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Shows what was read from the latest resume ({@code GET /analysis}): the skills found,
 * grouped by category, an overview of the sections (candidate, education, projects,
 * certifications, experience), and the full resume text with every detected skill
 * highlighted. With no resume yet, {@code analysis.jsp} shows its empty state.
 *
 * <p>The section details are not stored in the database, so the stored text is analysed
 * again with the same rule-based {@link ResumeAnalyzer}; the skill list itself comes from
 * the saved {@code resume_skills} rows.</p>
 *
 * <p>Demonstrates: Servlets ({@code doGet}, request and session attributes, forward),
 * {@code LinkedHashMap<String, List<String>>} (skills grouped by category, in dictionary
 * order), {@code ArrayList} sorted with a {@code Comparator}, {@code java.util.regex}
 * ({@code Matcher}), {@code StringBuilder}, HTML escaping to prevent script injection,
 * catching the abstract {@link AppException}.</p>
 */
@WebServlet("/analysis")
public class AnalysisServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String VIEW = "/WEB-INF/views/analysis.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        request.setAttribute("activePage", "analysis");
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        // One-time message left by the upload servlet ("Resume analyzed. Score 78/100 ...").
        Object flash = session.getAttribute(ResumeUploadServlet.FLASH);
        if (flash != null) {
            session.removeAttribute(ResumeUploadServlet.FLASH);
            request.setAttribute("message", flash);
        }

        try {
            loadAnalysis(request, user);
        } catch (AppException e) {
            request.removeAttribute("resume"); // show the empty state, not half a page
            request.setAttribute("error", e.getUserMessage());
        }
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    private void loadAnalysis(HttpServletRequest request, User user) throws AppException {
        if (user == null) {
            return;
        }
        PageSupport.loadMyJobsCount(request, user);

        Resume resume = new ResumeDAO().findLatestByUser(user.getId());
        if (resume == null) {
            return; // empty state
        }
        List<Skill> dictionary = new SkillDAO().findAll(); // ordered by id

        // Sections, education, projects ... are worked out again from the stored text.
        ResumeInfo info = new ResumeAnalyzer(dictionary).readInfo(resume.getResumeText());

        Set<String> found = resume.getSkills();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        List<String> skillNames = new ArrayList<>();
        for (Skill skill : dictionary) {
            if (found.contains(skill.getName())) {
                groups.computeIfAbsent(skill.getCategory(), k -> new ArrayList<>()).add(skill.getName());
                skillNames.add(skill.getName());
            }
        }

        request.setAttribute("resume", resume);
        request.setAttribute("uploadedDate", DashboardServlet.formatDate(resume.getUploadedAt()));
        request.setAttribute("skillCount", found.size());
        request.setAttribute("skillGroups", groups);
        request.setAttribute("skillsLine", String.join(", ", skillNames));
        request.setAttribute("dictionarySize", dictionary.size());
        request.setAttribute("info", info);
        request.setAttribute("projectTitles", titles(info.getProjects()));
        request.setAttribute("experienceTitles", titles(info.getExperience()));
        request.setAttribute("highlightedText", highlight(resume.getResumeText(), dictionary));
    }

    /**
     * Builds the resume text as HTML with each dictionary skill wrapped in {@code <mark>}.
     *
     * <p>Why the JSP may print this without escaping it again: every piece of the resume
     * text, both between matches and the matched words themselves, goes through
     * {@link HtmlUtil#escape}, so a {@code <script>} in the file becomes harmless
     * {@code &lt;script&gt;}. The only markup added is the fixed {@code <mark>} and
     * {@code </mark>} written by this method.</p>
     *
     * <p>Matches use {@link ResumeAnalyzer#skillPattern}, the same whole-word rule as skill
     * extraction, so "Java" is not marked inside "JavaScript". When two spellings overlap,
     * the one that starts first wins, and at the same start the longer one wins
     * ("Data Structures and Algorithms" over "Data Structures").</p>
     */
    static String highlight(String text, List<Skill> dictionary) {
        if (text == null) {
            return "";
        }
        List<int[]> hits = new ArrayList<>(); // {start, end} of every match
        for (Skill skill : dictionary) {
            List<String> spellings = new ArrayList<>(skill.getAliases());
            spellings.add(skill.getName());
            for (String spelling : spellings) {
                if (spelling == null || spelling.trim().isEmpty()) {
                    continue;
                }
                Matcher m = ResumeAnalyzer.skillPattern(spelling.trim()).matcher(text);
                while (m.find()) {
                    hits.add(new int[] {m.start(), m.end()});
                }
            }
        }
        hits.sort(Comparator.<int[]>comparingInt(h -> h[0])
                .thenComparing(Comparator.<int[]>comparingInt(h -> h[1]).reversed()));

        StringBuilder html = new StringBuilder(text.length() + hits.size() * 13);
        int pos = 0;
        for (int[] hit : hits) {
            if (hit[0] < pos) {
                continue; // overlaps a match already marked
            }
            html.append(HtmlUtil.escape(text.substring(pos, hit[0])));
            html.append("<mark>").append(HtmlUtil.escape(text.substring(hit[0], hit[1]))).append("</mark>");
            pos = hit[1];
        }
        html.append(HtmlUtil.escape(text.substring(pos)));
        return html.toString();
    }

    /** "Library Management System: Built a ..." becomes "Library Management System". */
    private static List<String> titles(List<String> items) {
        List<String> titles = new ArrayList<>();
        for (String item : items) {
            int colon = item.indexOf(':');
            titles.add(colon >= 0 ? item.substring(0, colon) : item);
        }
        return titles;
    }
}
