package com.resumematch.web;

import com.resumematch.dao.ResumeDAO;
import com.resumematch.exception.AppException;
import com.resumematch.model.JobMatch;
import com.resumematch.model.Resume;
import com.resumematch.model.User;
import com.resumematch.service.ResumeAnalyzer;
import com.resumematch.service.ResumeScorer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the score of the logged-in user's latest resume: the total out of 100, the five
 * rule rows with their bars, the strengths and the areas to improve. The total is the score
 * stored at upload, the same number the dashboard shows. The stored text is read again for
 * the section facts, and the rows are scored from the stored skills, so every row carries
 * its rule text and the rows add up to that total; nothing is computed in the JSP.
 *
 * <p>Demonstrates: Servlets ({@code HttpServlet}, {@code doGet}, {@code @WebServlet}),
 * sessions, request attributes, forwarding to a JSP, catching custom exceptions.</p>
 */
@WebServlet("/score")
public class ScoreServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ScoreServlet.class.getName());

    private static final long serialVersionUID = 1L;

    private static final String VIEW = "/WEB-INF/views/score.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        request.setAttribute("activePage", "score");

        HttpSession session = request.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        PageSupport.loadMyJobsCount(request, user);

        try {
            Resume resume = new ResumeDAO().findLatestByUser(user.getId());
            if (resume != null) {
                ResumeScorer scorer = new ResumeScorer();
                // The section facts do not need the skill dictionary; the skills are the stored ones.
                resume.setInfo(new ResumeAnalyzer(Collections.emptyList()).readInfo(resume.getResumeText()));
                resume.setBreakdown(scorer.calculateScore(resume.getInfo(), resume.getSkills()));
                int score = resume.getScore();

                request.setAttribute("resume", resume);
                request.setAttribute("breakdown", resume.getBreakdown());
                request.setAttribute("score", score);
                request.setAttribute("strengths", scorer.findStrengths(resume.getInfo(), resume.getSkills()));
                request.setAttribute("weaknesses", scorer.findWeaknesses(resume.getInfo(), resume.getSkills()));
                request.setAttribute("ringClass", ringClass(score));
                request.setAttribute("verdict", verdict(score));
            }
        } catch (AppException e) {
            LOGGER.log(Level.SEVERE, "could not load the score", e);
            request.setAttribute("error", e.getUserMessage());
        }

        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /** CSS class for the score ring: hi (green), mid (amber) or lo (red). */
    static String ringClass(int score) {
        return JobMatch.levelFor(score);
    }

    /** One-line summary shown next to the ring. */
    static String verdict(int score) {
        if (score >= 80) {
            return "Strong resume for internship applications.";
        }
        if (score >= 60) {
            return "Solid base. A few additions would lift it above 80.";
        }
        return "Needs more detail in several sections.";
    }
}
