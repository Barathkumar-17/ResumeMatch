package com.resumematch.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Guards every page that needs a logged-in user. With no {@code "user"} in the
 * session the browser is sent to {@code /login}. Protected responses are marked
 * {@code Cache-Control: no-store}, so after logout the Back button cannot show a
 * cached private page.
 *
 * <p>Demonstrates: the {@code Filter} interface, {@code @WebFilter} URL mapping,
 * {@code FilterChain}, casting to the HTTP request and response types.</p>
 */
@WebFilter(urlPatterns = {"/dashboard", "/analysis", "/score", "/skill-gap", "/jobs", "/job",
        "/my-jobs", "/upload", "/save-job", "/application", "/resume-file"})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        response.setHeader("Cache-Control", "no-store");

        if (!LoginServlet.isLoggedIn(request)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        chain.doFilter(request, response);
    }
}
