package com.resumematch.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds browser security headers to every response (and {@code Cache-Control: no-store} to every
 * page, so the Back button never shows a cached private page), and refuses oversized requests
 * (more than 1 MB announced in {@code Content-Length}, or 3 MB for the resume upload
 * at {@code /upload}) with a 413 error before any servlet reads the body. The upload
 * servlet and {@code FileService} keep their own per-type file-size limits.
 * Registered first in {@code web.xml} so it runs before every other filter.
 *
 * <p>Demonstrates: the {@code Filter} interface, response headers, {@code sendError},
 * constants, {@code FilterChain}.</p>
 */
public class SecurityHeadersFilter implements Filter {

    /** Largest request body accepted, in bytes (1 MB). */
    private static final long MAX_REQUEST_BYTES = 1024L * 1024L;

    /** Largest request body accepted for the resume upload, in bytes (3 MB). */
    private static final long MAX_UPLOAD_REQUEST_BYTES = 3L * 1024L * 1024L;

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; "
            + "base-uri 'self'; object-src 'none'";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        // Pages carry the CSRF token and personal data; only the fixed files under /assets/ may be cached.
        if (!request.getRequestURI().startsWith(request.getContextPath() + "/assets/")) {
            response.setHeader("Cache-Control", "no-store");
        }

        long limit = "/upload".equals(request.getServletPath()) ? MAX_UPLOAD_REQUEST_BYTES : MAX_REQUEST_BYTES;
        if (request.getContentLengthLong() > limit) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
