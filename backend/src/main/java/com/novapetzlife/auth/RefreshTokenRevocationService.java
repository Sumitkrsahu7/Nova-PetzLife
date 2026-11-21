package com.novapetzlife.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Dedicated service for handling refresh token revocations in an independent physical transaction.
 * Uses {@link Propagation#REQUIRES_NEW} across Spring proxy boundaries to guarantee that revocations
 * (especially during reuse attacks) are committed to the database even if the calling method throws
 * an authentication exception.
 */
@Service
public class RefreshTokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRevocationService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Revokes all active refresh tokens for the given user in an independent transaction.
     * Committed immediately to prevent rollback when an authentication exception is raised.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllActiveSessions(Long userId, String ipAddress) {
        if (userId == null) {
            return 0;
        }
        int revokedCount = refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now(), ipAddress);
        log.warn("COMMITTED SECURITY REVOCATION: Revoked {} active refresh tokens for user id={} from ip={}",
                revokedCount, userId, ipAddress);
        return revokedCount;
    }
}
