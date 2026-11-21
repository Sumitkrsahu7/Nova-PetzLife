package com.novapetzlife.auth;

import com.novapetzlife.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Grace period (in seconds) to accommodate legitimate race conditions from concurrent browser tabs
    public static final long REUSE_DETECTION_GRACE_PERIOD_SECONDS = 10L;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService revocationService;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenRevocationService revocationService,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationService = revocationService;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public record TokenPair(String rawToken, RefreshToken entity) {}

    @Transactional
    public TokenPair createRefreshToken(User user, String ipAddress) {
        String rawToken = generateSecureToken();
        String hash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plusMillis(refreshExpirationMs);

        RefreshToken refreshToken = new RefreshToken(user, hash, expiresAt, ipAddress);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token for user id={}, expiresAt={}", user.getId(), expiresAt);
        return new TokenPair(rawToken, saved);
    }

    @Transactional
    public TokenPair rotateRefreshToken(String rawToken, String ipAddress) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new BadCredentialsException("Refresh token is required");
        }

        String hash = hashToken(rawToken.trim());
        // Acquire pessimistic write lock to serialize concurrent rotation attempts for the same token
        RefreshToken token = refreshTokenRepository.findByTokenHashWithLock(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // Reuse detection check
        if (token.isRevoked()) {
            Instant revokedAt = token.getRevokedAt();
            boolean withinGracePeriod = revokedAt != null &&
                    Instant.now().isBefore(revokedAt.plusSeconds(REUSE_DETECTION_GRACE_PERIOD_SECONDS));

            // Safe Concurrent Refresh Recovery:
            // If the token was rotated < 10 seconds ago and has an active replacement token,
            // recover the concurrent request without rotating again or creating duplicate sessions.
            if (withinGracePeriod && token.getReplacedByTokenHash() != null) {
                Optional<RefreshToken> replacementOpt = refreshTokenRepository.findByTokenHash(token.getReplacedByTokenHash());
                if (replacementOpt.isPresent() && replacementOpt.get().isActive()) {
                    RefreshToken replacement = replacementOpt.get();
                    User user = replacement.getUser();
                    if (!Boolean.TRUE.equals(user.getIsActive())) {
                        log.warn("Concurrent refresh recovery rejected: user id={} is deactivated.", user.getId());
                        throw new BadCredentialsException("User account is deactivated.");
                    }
                    log.info("CONCURRENT REFRESH RECOVERY: Token id={} was already rotated < {}s ago. Synchronizing session for user id={}",
                            token.getId(), REUSE_DETECTION_GRACE_PERIOD_SECONDS, user.getId());
                    // Return rawToken=null to indicate no new cookie is generated, while providing the active entity
                    return new TokenPair(null, replacement);
                }
            }

            // Genuine reuse attack! Revoke all sessions in a committed independent transaction
            log.warn("SECURITY ALERT: Refresh token reuse detected for user id={}, token id={}. Revoking all sessions.",
                    token.getUser().getId(), token.getId());
            revocationService.revokeAllActiveSessions(token.getUser().getId(), ipAddress);
            throw new BadCredentialsException("Refresh token reuse detected. All active sessions have been invalidated.");
        }

        if (token.isExpired()) {
            log.info("Refresh token expired for user id={}, expired at={}", token.getUser().getId(), token.getExpiresAt());
            throw new BadCredentialsException("Refresh token has expired. Please log in again.");
        }

        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Refresh rejected: user id={} is deactivated.", user.getId());
            throw new BadCredentialsException("User account is deactivated.");
        }

        // Generate replacement token
        String newRawToken = generateSecureToken();
        String newHash = hashToken(newRawToken);
        Instant newExpiresAt = Instant.now().plusMillis(refreshExpirationMs);

        // Mark old token as revoked and replaced
        token.setRevokedAt(Instant.now());
        token.setRevokedByIp(ipAddress);
        token.setReplacedByTokenHash(newHash);
        refreshTokenRepository.save(token);

        RefreshToken newToken = new RefreshToken(user, newHash, newExpiresAt, ipAddress);
        RefreshToken savedNewToken = refreshTokenRepository.save(newToken);
        log.info("Rotated refresh token for user id={}", user.getId());

        return new TokenPair(newRawToken, savedNewToken);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken, String ipAddress) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return;
        }
        String hash = hashToken(rawToken.trim());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevokedAt(Instant.now());
                token.setRevokedByIp(ipAddress);
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token id={} for user id={}", token.getId(), token.getUser().getId());
            }
        });
    }

    @Transactional
    public void revokeAllUserTokens(Long userId, String ipAddress) {
        if (userId != null) {
            revocationService.revokeAllActiveSessions(userId, ipAddress);
        }
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String generateSecureToken() {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
