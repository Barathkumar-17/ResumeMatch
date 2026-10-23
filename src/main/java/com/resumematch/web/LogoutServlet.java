package com.resumematch.web;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Logs the user out: the whole session is invalidated, then a new, empty session
 * carries a one-time "You have been logged out." message to the login page. Logout is a
 * POST from the sidebar form (with the CSRF token), so another site cannot log a user
 * out with a link or an image; a plain GET gets the friendly 405 page.
 *
 * <p>Demonstrates: {@code HttpSession.invalidate()}, {@code doPost}, redirects, {@code @WebServlet}.</p>
 */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        request.getSession(true).setAttribute(RegisterServlet.FLASH_MESSAGE, "You have been logged out.");
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
