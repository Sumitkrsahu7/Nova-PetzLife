package com.novapetzlife.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final String configuredClientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthService(@Value("${app.oauth.google.client-id:}") String configuredClientId) {
        this.configuredClientId = configuredClientId != null ? configuredClientId.trim() : "";
        if (StringUtils.hasText(this.configuredClientId)) {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(this.configuredClientId))
                    .setIssuers(List.of("https://accounts.google.com", "accounts.google.com"))
                    .build();
            log.info("Initialized Google ID Token Verifier for audience: {}", this.configuredClientId);
        } else {
            this.verifier = null;
            log.warn("Google OAuth Client ID is not configured. Google Sign-In will operate in configuration seam mode.");
        }
    }

    public boolean isConfigured() {
        return verifier != null && StringUtils.hasText(configuredClientId);
    }

    /**
     * Verifies the provided Google ID token credential cryptographically and structurally.
     *
     * @param idTokenString JWT raw string received from Google Identity Services button
     * @return Extracted and validated GoogleTokenPayload
     */
    public GoogleTokenPayload verifyToken(String idTokenString) {
        if (!StringUtils.hasText(idTokenString)) {
            throw new BadCredentialsException("Google ID token credential must not be empty");
        }

        if (!isConfigured()) {
            throw new BadCredentialsException("Google authentication is not yet configured on this server (GOOGLE_CLIENT_ID missing)");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString.trim());
            if (idToken == null) {
                log.warn("Google ID token failed cryptographic verification or audience check");
                throw new BadCredentialsException("Invalid or expired Google authentication token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // 1. Validate subject (sub)
            String sub = payload.getSubject();
            if (!StringUtils.hasText(sub)) {
                throw new BadCredentialsException("Missing subject ('sub') claim in Google token");
            }

            // 2. Validate email verification
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                throw new BadCredentialsException("Google account email address is not verified");
            }

            String email = payload.getEmail();
            if (!StringUtils.hasText(email)) {
                throw new BadCredentialsException("Missing email claim in Google token");
            }

            String name = (String) payload.get("name");
            String givenName = (String) payload.get("given_name");
            String familyName = (String) payload.get("family_name");
            String picture = (String) payload.get("picture");

            log.info("Successfully verified Google ID token for sub: {}, email: {}", sub, email);
            return new GoogleTokenPayload(
                    sub,
                    email.toLowerCase().trim(),
                    true,
                    name != null ? name.trim() : "",
                    givenName != null ? givenName.trim() : "",
                    familyName != null ? familyName.trim() : "",
                    picture != null ? picture.trim() : ""
            );
        } catch (BadCredentialsException bce) {
            throw bce;
        } catch (Exception e) {
            log.error("Exception during Google ID token verification: {}", e.getMessage());
            throw new BadCredentialsException("Failed to verify Google authentication token: " + e.getMessage());
        }
    }
}
