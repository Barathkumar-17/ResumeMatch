package com.resumematch.web;

import com.resumematch.dao.ResumeDAO;
import com.resumematch.dao.SkillDAO;
import com.resumematch.exception.AppException;
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

/**
 * Shows the score of the logged-in user's latest resume: the total out of 100, the five
 * rule rows with their bars, the strengths and the areas to improve. The stored text is
 * analysed again so every row carries its rule text; nothing is computed in the JSP.
 *
 * <p>Demonstrates: Servlets ({@code HttpServlet}, {@code doGet}, {@code @WebServlet}),
 * sessions, request attributes, forwarding to a JSP, catching custom exceptions.</p>
 */
@WebServlet("/score")
public class ScoreServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String VIEW = "/WEB-INF/views/score.jsp";

    /** A score at or above this is shown in green. */
    static final int HIGH_SCORE = 75;

    /** A score at or above this (and below {@link #HIGH_SCORE}) is shown in amber. */
    static final int MID_SCORE = 50;

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

        try {
            Resume resume = new ResumeDAO().findLatestByUser(user.getId());
            if (resume != null) {
                new ResumeAnalyzer(new SkillDAO().findAll()).analyze(resume);
                ResumeScorer scorer = new ResumeScorer();
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
            System.err.println("[ScoreServlet] could not load the score: " + e.getMessage());
            request.setAttribute("error", e.getUserMessage());
        }

        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /** CSS class for the score ring: hi (green), mid (amber) or lo (red). */
    static String ringClass(int score) {
        if (score >= HIGH_SCORE) {
            return "hi";
        }
        return (score >= MID_SCORE) ? "mid" : "lo";
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
