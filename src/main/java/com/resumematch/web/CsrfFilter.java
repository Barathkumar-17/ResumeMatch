package com.resumematch.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Blocks forged form posts. Every page load (GET or HEAD) makes sure the session
 * holds a random token under {@code "csrfToken"}; every POST must send the same
 * token back as {@code _csrf}, otherwise the answer is 403. For multipart uploads
 * the token is read from the query string only, so this filter never parses the file.
 *
 * <p>Demonstrates: the {@code Filter} interface, {@code HttpSession}, {@code SecureRandom},
 * Base64, {@code MessageDigest.isEqual}, string parsing, {@code URLDecoder}.</p>
 */
public class CsrfFilter implements Filter {

    static final String SESSION_ATTRIBUTE = "csrfToken";
    static final String PARAMETER = "_csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (isAsset(request)) {
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            ensureToken(request.getSession(true));
        } else if ("POST".equalsIgnoreCase(method) && !tokenMatches(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAsset(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/assets/");
    }

    private static void ensureToken(HttpSession session) {
        if (session.getAttribute(SESSION_ATTRIBUTE) == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            session.setAttribute(SESSION_ATTRIBUTE,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        }
    }

    private static boolean tokenMatches(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(expected instanceof String)) {
            return false;
        }
        String submitted = tokenFromQuery(request.getQueryString());
        String contentType = request.getContentType();
        boolean multipart = contentType != null
                && contentType.toLowerCase().startsWith("multipart/form-data");
        // Reading a parameter of a multipart request would parse the whole upload.
        if (submitted == null && !multipart) {
            submitted = request.getParameter(PARAMETER);
        }
        if (submitted == null) {
            return false;
        }
        return MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }

    /** Finds {@code _csrf=...} in the raw query string, or returns null. */
    private static String tokenFromQuery(String query) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && PARAMETER.equals(decode(pair.substring(0, eq)))) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return "";
        }
    }
}
