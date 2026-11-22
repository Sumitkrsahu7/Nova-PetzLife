package com.novapetzlife.auth;

public record GoogleTokenPayload(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String givenName,
        String familyName,
        String picture
) {}
