package com.spendwise.auth;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so that the
 * RLS session variable set by {@link RlsSession} and the query that depends on it share the
 * same connection.
 */
@Repository
public class RefreshTokenRepository {

    private static final RowMapper<RefreshToken> ROW_MAPPER = (rs, rowNum) -> new RefreshToken(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getString("token_hash"),
            rs.getTimestamp("issued_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    /** Caller must already know {@code userId} (post-authentication) — sets RLS context itself. */
    public RefreshToken insert(UUID userId, String tokenHash, Instant expiresAt) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                id,
                userId,
                tokenHash,
                Timestamp.from(expiresAt));
        return new RefreshToken(id, userId, tokenHash, Instant.now(), expiresAt, null);
    }

    /** Pre-authentication lookup — see docs/database.md "Auth login lookup addendum". */
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        rlsSession.setAuthLookupTokenHash(tokenHash);
        return jdbcTemplate.query("SELECT * FROM refresh_tokens WHERE token_hash = ?", ROW_MAPPER, tokenHash).stream()
                .findFirst();
    }

    /** Caller must already know the row's {@code userId} (from a prior lookup in this transaction). */
    public void revoke(UUID id, UUID userId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = NOW() WHERE id = ?", id);
    }

    public void revokeAllForUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL", userId);
    }
}
