package com.spendwise.user;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so that the RLS
 * session variable set by {@link RlsSession} and the query that depends on it share the same
 * connection. Unlike the Auth-module bootstrap lookups, every caller here already knows
 * {@code userId} (from an authenticated JWT), so no pre-authentication lookup policy is needed.
 */
@Repository
public class DeviceApiKeyRepository {

    private static final RowMapper<DeviceApiKey> ROW_MAPPER = (rs, rowNum) -> new DeviceApiKey(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getString("key_hash"),
            rs.getTimestamp("registered_at").toInstant(),
            rs.getTimestamp("last_used_at") == null ? null : rs.getTimestamp("last_used_at").toInstant(),
            rs.getBoolean("is_active"));

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public DeviceApiKeyRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public DeviceApiKey insert(UUID userId, String keyHash) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO device_api_keys (id, user_id, key_hash) VALUES (?, ?, ?)", id, userId, keyHash);
        return new DeviceApiKey(id, userId, keyHash, java.time.Instant.now(), null, true);
    }

    /** Scoped to {@code userId}, which the caller already knows (e.g. from the ingest JWT). */
    public List<DeviceApiKey> findActiveForUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT * FROM device_api_keys WHERE user_id = ? AND is_active = TRUE", ROW_MAPPER, userId);
    }

    public void markLastUsed(UUID id, UUID userId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE device_api_keys SET last_used_at = NOW() WHERE id = ?", id);
    }
}
