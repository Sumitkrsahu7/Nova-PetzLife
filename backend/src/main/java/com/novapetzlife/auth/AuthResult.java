package com.novapetzlife.auth;

/**
 * Internal result from authentication services containing the client-facing AuthResponse
 * and the newly generated raw refresh token meant exclusively for HttpOnly cookie delivery.
 */
public record AuthResult(
        AuthResponse response,
        String rawRefreshToken
) {}
