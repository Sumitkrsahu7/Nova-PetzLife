package com.novapetzlife.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter protecting authentication and
 * geolocation endpoints from brute force and automated scraping attacks.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Limits: Max requests per window (ms)
    private static final int LOGIN_LIMIT = 15;
    private static final long LOGIN_WINDOW_MS = 60_000L; // 1 minute

    private static final int REFRESH_LIMIT = 30;
    private static final long REFRESH_WINDOW_MS = 60_000L; // 1 minute

    private static final int REGISTER_LIMIT = 10;
    private static final long REGISTER_WINDOW_MS = 300_000L; // 5 minutes

    private static final int FORGOT_PASSWORD_LIMIT = 5;
    private static final long FORGOT_PASSWORD_WINDOW_MS = 900_000L; // 15 minutes

    private static final int RESET_PASSWORD_LIMIT = 5;
    private static final long RESET_PASSWORD_WINDOW_MS = 900_000L; // 15 minutes

    private static final int DELIVERY_CHECK_LIMIT = 40;
    private static final long DELIVERY_CHECK_WINDOW_MS = 60_000L; // 1 minute

    @org.springframework.beans.factory.annotation.Value("${app.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!rateLimitingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only rate limit specific write/computation endpoints
        if ("POST".equalsIgnoreCase(method) && path.endsWith("/api/v1/auth/login")) {
            if (!isAllowed(ClientIpUtil.getClientIp(request) + ":login", LOGIN_LIMIT, LOGIN_WINDOW_MS)) {
                sendRateLimitResponse(response, "Too many login attempts. Please wait 1 minute before trying again.");
                return;
            }
        } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/api/v1/auth/refresh")) {
            if (!isAllowed(ClientIpUtil.getClientIp(request) + ":refresh", REFRESH_LIMIT, REFRESH_WINDOW_MS)) {
                sendRateLimitResponse(response, "Too many token refresh requests. Please wait 1 minute before trying again.");
                return;
            }
        } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/api/v1/auth/register")) {
            if (!isAllowed(ClientIpUtil.getClientIp(request) + ":register", REGISTER_LIMIT, REGISTER_WINDOW_MS)) {
                sendRateLimitResponse(response, "Too many registration attempts. Please wait before trying again.");
                return;
            }
        } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/api/v1/auth/forgot-password")) {
            if (!isAllowed(ClientIpUtil.getClientIp(request) + ":forgot-pwd", FORGOT_PASSWORD_LIMIT, FORGOT_PASSWORD_WINDOW_MS)) {
                sendRateLimitResponse(response, "Too many password reset requests. Please wait 15 minutes before trying again.");
                return;
            }
        } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/api/v1/auth/reset-password")) {
            if (!isAllowed(ClientIpUtil.getClientIp(request) + ":reset-pwd", RESET_PASSWORD_LIMIT, RESET_PASSWORD_WINDOW_MS)) {
                sendRateLimitResponse(response, "Too many password reset attempts. Please wait 15 minutes before trying again.");
                return;
            }
        } else if (path.contains("/api/v1/delivery/check-address")) {
            if (!isAllowed(ClientIpUtil.getClientIp(request) + ":delivery-check", DELIVERY_CHECK_LIMIT, DELIVERY_CHECK_WINDOW_MS)) {
                sendRateLimitResponse(response, "Too many address lookup requests. Please wait a moment.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private synchronized boolean isAllowed(String key, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestLogs.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Evict expired timestamps from the left
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        Map<String, Object> body = Map.of(
                "success", false,
                "status", 429,
                "message", message
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
