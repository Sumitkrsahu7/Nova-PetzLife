package com.novapetzlife.auth;

import java.util.List;

public record AuthResponse(
        String token,
        String tokenType,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        List<String> permissions
) {
    public static AuthResponse of(String token, String email, String firstName, String lastName, List<String> roles) {
        return new AuthResponse(token, "Bearer", email, firstName, lastName, roles, List.of());
    }

    public static AuthResponse of(String token, String email, String firstName, String lastName, List<String> roles, List<String> permissions) {
        return new AuthResponse(token, "Bearer", email, firstName, lastName, roles, permissions != null ? permissions : List.of());
    }
}
