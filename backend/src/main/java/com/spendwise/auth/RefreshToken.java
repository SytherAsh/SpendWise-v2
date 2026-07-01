package com.spendwise.auth;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(UUID id, UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt, Instant revokedAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
