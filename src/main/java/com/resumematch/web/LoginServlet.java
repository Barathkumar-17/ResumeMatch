package com.resumematch.web;

import com.resumematch.dao.UserDAO;
import com.resumematch.exception.AppException;
import com.resumematch.exception.InvalidLoginException;
import com.resumematch.model.User;
import com.resumematch.util.PasswordUtil;
import com.resumematch.util.ValidationUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows the login form (GET) and signs the student in (POST). A wrong email and a
 * wrong password give the same message so the page never reveals which emails are
 * registered. After a good login the old session is thrown away and a new one is
 * started (so a session id known before login is useless afterwards), and the user is
 * stored under the session attribute {@code "user"} without the password hash.
 *
 * <p>Demonstrates: {@code HttpSession}, session fixation protection, throwing and
 * catching a custom exception, {@code LinkedHashMap}, Post/Redirect/Get.</p>
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Session attribute that holds the logged-in {@link User}. */
    public static final String SESSION_USER = "user";

    private static final String VIEW = "/WEB-INF/views/auth.jsp";

    private final transient UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (isLoggedIn(request)) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }
        // One-time message left by register or logout; shown once, then removed.
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object flash = session.getAttribute(RegisterServlet.FLASH_MESSAGE);
            if (flash != null) {
                session.removeAttribute(RegisterServlet.FLASH_MESSAGE);
                request.setAttribute("message", flash);
            }
        }
        showForm(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        Map<String, String> errors = new LinkedHashMap<>();
        String emailError = ValidationUtil.emailError(email);
        if (emailError != null) {
            errors.put("email", emailError);
        }
        String passwordError = ValidationUtil.loginPasswordError(password);
        if (passwordError != null) {
            errors.put("password", passwordError);
        }
        if (!errors.isEmpty()) {
            request.setAttribute("errors", errors);
            showForm(request, response);
            return;
        }

        User user;
        try {
            user = authenticate(email.trim().toLowerCase(), password);
        } catch (AppException e) {
            request.setAttribute("error", e.getUserMessage());
            showForm(request, response);
            return;
        }

        // Session fixation: drop any session that existed before login, start a fresh one.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER, new User(user.getId(), user.getName(), user.getEmail(), null));
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    /**
     * Returns the account when the email exists and the password matches.
     *
     * @throws InvalidLoginException if the email is unknown or the password is wrong
     */
    private User authenticate(String email, String password) throws AppException {
        User user = userDAO.findByEmail(email);
        if (user == null || !PasswordUtil.matches(password, user.getPasswordHash())) {
            throw new InvalidLoginException();
        }
        return user;
    }

    /** True when the current request already has a logged-in user in its session. */
    static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(SESSION_USER) != null;
    }

    /** Forwards to the shared login/register page with the login tab open. */
    private void showForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("mode", "login");
        request.getRequestDispatcher(VIEW).forward(request, response);
    }
}
