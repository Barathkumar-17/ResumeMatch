package com.resumematch.web;

import com.resumematch.dao.UserDAO;
import com.resumematch.exception.AppException;
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
import java.util.Locale;
import java.util.Map;

/**
 * Shows the register form (GET) and creates an account (POST). Each field is checked
 * separately and its message is put in a map keyed by the input name, so the page can
 * show it under the right box. A second account with the same email is reported with
 * the {@code DuplicateEmailException} message. On success the browser is redirected to
 * the login page with a one-time "Account created" message.
 *
 * <p>Demonstrates: {@code HttpServlet} doGet/doPost, {@code @WebServlet}, request
 * attributes, {@code LinkedHashMap}, catching custom exceptions, Post/Redirect/Get.</p>
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Session attribute holding a one-time message for the login page. */
    static final String FLASH_MESSAGE = "flashMessage";

    private static final String VIEW = "/WEB-INF/views/auth.jsp";

    private final transient UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession existing = request.getSession(false);
        if (existing != null && existing.getAttribute("user") != null) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }
        showForm(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String confirm = request.getParameter("confirmPassword");

        Map<String, String> errors = new LinkedHashMap<>();
        addIfPresent(errors, "name", ValidationUtil.nameError(name));
        addIfPresent(errors, "email", ValidationUtil.emailError(email));
        addIfPresent(errors, "password", ValidationUtil.newPasswordError(password));
        addIfPresent(errors, "confirmPassword", ValidationUtil.confirmPasswordError(password, confirm));

        if (!errors.isEmpty()) {
            request.setAttribute("errors", errors);
            showForm(request, response);
            return;
        }

        try {
            userDAO.register(name.trim(), email.trim().toLowerCase(Locale.ROOT), PasswordUtil.hash(password));
        } catch (AppException e) {
            // DuplicateEmailException or DatabaseConnectionException: both carry a safe message.
            request.setAttribute("error", e.getUserMessage());
            showForm(request, response);
            return;
        }

        HttpSession session = request.getSession();
        session.setAttribute(FLASH_MESSAGE, "Account created. Log in to continue.");
        response.sendRedirect(request.getContextPath() + "/login");
    }

    /** Forwards to the shared login/register page with the register tab open. */
    private void showForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("mode", "register");
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /** Puts the message in the map only when the field actually has an error. */
    private static void addIfPresent(Map<String, String> errors, String field, String message) {
        if (message != null) {
            errors.put(field, message);
        }
    }
}
