package com.novapetzlife.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility for extracting and validating client IP addresses behind reverse proxies.
 *
 * <p>Render Deployment Architecture Notes:
 * <ul>
 *   <li>Render terminates TLS at its edge reverse proxies (Cloudflare/Envoy).</li>
 *   <li>The edge proxy forwards the original client IP in the {@code X-Forwarded-For} header.</li>
 *   <li>When multiple proxies exist, the client IP is the left-most comma-separated entry.</li>
 *   <li>Direct connections (e.g. local development) fall back to {@link HttpServletRequest#getRemoteAddr()}.</li>
 * </ul>
 */
public final class ClientIpUtil {

    private ClientIpUtil() {}

    /**
     * Extracts the client IP address from the request.
     * Sanitizes and validates the extracted IP to prevent header injection attacks.
     *
     * @param request the incoming HTTP servlet request
     * @return the sanitized client IP, or "unknown" if undetermined
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            String clientIp = ips[0].trim();
            if (isValidIp(clientIp)) {
                return clientIp;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank() && isValidIp(remoteAddr.trim())) {
            return remoteAddr.trim();
        }

        return "unknown";
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return false;
        }
        // Match standard IPv4, IPv6 (including compressed), or localhost
        return ip.matches("^[0-9a-fA-F:.]+$");
    }
}
