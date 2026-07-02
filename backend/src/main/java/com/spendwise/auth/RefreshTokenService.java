package com.spendwise.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Refresh token generation, rotation, and replay-attack handling (E1-S1-T2, E1-S1-T5,
 * E1-S1-T6). Per docs/security.md: 30-day sliding expiry reset on every rotation; presenting
 * an already-rotated token revokes every refresh token for that user.
 */
@Service
public class RefreshTokenService {

    public static final long REFRESH_TOKEN_TTL_DAYS = 30;

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {}

    public record RotationResult(String newRawToken, Instant expiresAt, UUID userId) {}

    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);
        repository.insert(userId, hash(rawToken), expiresAt);
        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /**
     * @throws InvalidRefreshTokenException if the token is unknown or expired, or (after
     *     detecting and handling a replay) if the token had already been rotated once.
     *     {@code noRollbackFor} is required here: the replay branch below writes
     *     {@code revokeAllForUser} and then deliberately throws to reject the caller's
     *     request — without it, {@code @Transactional}'s default rollback-on-RuntimeException
     *     would silently undo the revocation, leaving every other session still valid.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String rawToken) {
        String incomingHash = hash(rawToken);
        RefreshToken existing = repository.findByTokenHash(incomingHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));

        if (existing.isRevoked()) {
            // Replay: this exact token was already rotated (or logged out) once before.
            // Treat as a compromise signal and force re-authentication on every device.
            repository.revokeAllForUser(existing.userId());
            throw new InvalidRefreshTokenException("Refresh token replay detected; all sessions revoked");
        }
        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        repository.revoke(existing.id(), existing.userId());
        String newRawToken = generateRawToken();
        Instant newExpiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);
        repository.insert(existing.userId(), hash(newRawToken), newExpiresAt);
        return new RotationResult(newRawToken, newExpiresAt, existing.userId());
    }

    /**
     * @throws InvalidRefreshTokenException if the token is not recognized, or does not
     *     belong to {@code expectedUserId} (the JWT-authenticated caller) — logout only
     *     revokes the caller's own session, never another user's
     */
    @Transactional
    public void logout(String rawToken, UUID expectedUserId) {
        String incomingHash = hash(rawToken);
        RefreshToken existing = repository.findByTokenHash(incomingHash)
                .filter(token -> token.userId().equals(expectedUserId))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));
        repository.revoke(existing.id(), existing.userId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
