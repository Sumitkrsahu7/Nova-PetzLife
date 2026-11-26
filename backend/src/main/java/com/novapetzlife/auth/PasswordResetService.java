package com.novapetzlife.auth;

import com.novapetzlife.email.EmailService;
import com.novapetzlife.email.GoogleAppsScriptEmailService;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.auth.password-reset-token-expiration-minutes:${PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES:15}}")
    private long expirationMinutes;

    private final PasswordResetTokenRepository resetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final GoogleAppsScriptEmailService gmailBridgeEmailService;

    public PasswordResetService(
            PasswordResetTokenRepository resetTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            EmailService emailService) {
        this(resetTokenRepository, userRepository, passwordEncoder, refreshTokenService, emailService, null);
    }

    @Autowired
    public PasswordResetService(
            PasswordResetTokenRepository resetTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            EmailService emailService,
            @Qualifier("googleAppsScriptEmailService") GoogleAppsScriptEmailService gmailBridgeEmailService) {
        this.resetTokenRepository = resetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
        this.gmailBridgeEmailService = gmailBridgeEmailService;
    }

    @Transactional
    public String requestPasswordReset(ForgotPasswordRequest request, String clientIp) {
        String email = request.getSanitizedEmail();
        userRepository.findByEmail(email).ifPresent(user -> {
            // Customer/Admin separation: ensure customer password reset only applies to customer accounts
            boolean isCustomer = user.getRoles().stream()
                    .anyMatch(r -> "ROLE_CUSTOMER".equalsIgnoreCase(r.getName()));
            boolean isAdmin = user.getRoles().stream()
                    .anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()) || "ROLE_STAFF_ADMIN".equalsIgnoreCase(r.getName()));

            if (!isCustomer || isAdmin || !Boolean.TRUE.equals(user.getIsActive())) {
                log.info("Password reset skipped: user is not an active customer account. No token generated.");
                return;
            }

            // Invalidate any previously issued active tokens for this user
            resetTokenRepository.invalidateAllActiveTokensForUser(user, Instant.now());

            String rawToken = generateSecureToken();
            String tokenHash = hashToken(rawToken);
            Instant expiresAt = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);

            PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, expiresAt, clientIp);
            resetTokenRepository.save(resetToken);

            log.info("Generated password reset token for user id={}, expiresAt={}", user.getId(), expiresAt);
            if (gmailBridgeEmailService != null && gmailBridgeEmailService.isEnabled()) {
                gmailBridgeEmailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);
            } else {
                emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);
            }
        });

        // Always return generic confirmation to protect against email enumeration attacks
        return "If an account with that email exists, a password reset link has been sent.";
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Password reset token is required");
        }

        String hash = hashToken(rawToken.trim());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset link"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Password reset link has expired or has already been used");
        }

        return true;
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request, String clientIp) {
        String hash = hashToken(request.token().trim());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset link"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Password reset link has expired or has already been used");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        token.setUsedByIp(clientIp);
        resetTokenRepository.save(token);

        // Invalidate any other active tokens for this user
        resetTokenRepository.invalidateAllActiveTokensForUser(user, Instant.now());

        // Security requirement: Revoke all existing sessions so any hijacked sessions are immediately terminated
        refreshTokenService.revokeAllUserTokens(user.getId(), clientIp);
        log.info("Successfully reset password and terminated all active sessions for user id={}", user.getId());

        return "Password has been successfully reset. Please log in with your new credentials.";
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
