package com.novapetzlife.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Strict fail-closed CSRF filter for cookie-authenticated auth endpoints:
 *   POST /api/v1/auth/refresh
 *   POST /api/v1/auth/logout
 *
 * <p>Fail-Closed Defense Strategy:
 * <ul>
 *   <li>Only applies to cookie-authenticated state-changing POST requests.</li>
 *   <li>Preflight (OPTIONS) and stateless Bearer-token endpoints bypass this check.</li>
 *   <li>Requires custom header {@code X-Requested-With: XMLHttpRequest}. Browsers forbid cross-origin
 *       HTML forms from setting custom headers, enforcing mandatory CORS preflight.</li>
 *   <li>Requires a verified Origin (or same-origin Referer). Rejects missing or blank Origins.</li>
 *   <li>Rejects requests if the trusted origin list is empty, null, or unconfigured (fail-closed).</li>
 * </ul>
 */
@Component
public class AuthCookieCsrfFilter extends OncePerRequestFilter {

    private final List<String> allowedOrigins;

    public AuthCookieCsrfFilter(
            @Value("${app.cors.allowed-origins:}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Enforce fail-closed CSRF validation on cookie-authenticated state-changing endpoints
        if ("POST".equalsIgnoreCase(method) && (path.endsWith("/api/v1/auth/refresh") || path.endsWith("/api/v1/auth/logout"))) {

            // 1. Validate X-Requested-With header (fail-closed)
            String requestedWith = request.getHeader("X-Requested-With");
            if (requestedWith == null || !"XMLHttpRequest".equalsIgnoreCase(requestedWith.trim())) {
                sendForbiddenResponse(response, "Cross-site request rejected: missing or invalid X-Requested-With header");
                return;
            }

            // 2. Extract Origin header; fall back to Referer origin if Origin header was omitted
            String origin = request.getHeader("Origin");
            if (origin == null || origin.isBlank()) {
                String referer = request.getHeader("Referer");
                if (referer != null && !referer.isBlank()) {
                    origin = extractOriginFromReferer(referer);
                }
            }

            // If neither Origin nor Referer is present, fail-closed
            if (origin == null || origin.isBlank()) {
                sendForbiddenResponse(response, "Cross-site request rejected: missing Origin header");
                return;
            }

            // 3. Verify that the origin is explicitly trusted (fail-closed if list is empty or origin untrusted)
            if (!isOriginAllowed(origin.trim())) {
                sendForbiddenResponse(response, "Cross-site request rejected: untrusted origin");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOriginAllowed(String origin) {
        // Fail-closed: If allowedOrigins is null or empty, reject all requests
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }

        boolean hasValidConfiguredOrigin = false;
        for (String allowed : allowedOrigins) {
            if (allowed != null) {
                String clean = allowed.replace("[", "").replace("]", "").trim();
                if (!clean.isEmpty()) {
                    hasValidConfiguredOrigin = true;
                    if (clean.equalsIgnoreCase(origin)) {
                        return true;
                    }
                }
            }
        }

        // If the allowed origins contained only empty/blank strings, fail-closed
        if (!hasValidConfiguredOrigin) {
            return false;
        }

        return false;
    }

    private String extractOriginFromReferer(String referer) {
        try {
            URI uri = URI.create(referer.trim());
            int port = uri.getPort();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme != null && host != null) {
                if (port == -1 || (port == 80 && "http".equalsIgnoreCase(scheme)) || (port == 443 && "https".equalsIgnoreCase(scheme))) {
                    return scheme + "://" + host;
                }
                return scheme + "://" + host + ":" + port;
            }
        } catch (Exception ignored) {
            // Malformed Referer URL
        }
        return null;
    }

    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"status\":403,\"message\":\"" + message + "\"}");
        response.getWriter().flush();
    }
}
