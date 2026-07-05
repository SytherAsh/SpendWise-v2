package com.spendwise.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every method here reads (or, for the erasure flow, writes) across every user, so every query
 * goes through {@code jobsJdbcTemplate} — the {@code spendwise_jobs} (BYPASSRLS) role
 * (docs/security.md "Cross-user reads for background jobs", broadened in Epic 11 to also cover
 * Admin's request-scoped cross-user reads, not just {@code @Scheduled} jobs). Never uses the
 * RLS-scoped {@code jdbcTemplate} — there is no per-request "current user" for Admin to scope to.
 */
@Repository
public class AdminRepository {

    private final JdbcTemplate jobsJdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminRepository(@Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate, ObjectMapper objectMapper) {
        this.jobsJdbcTemplate = jobsJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** {@code GET /admin/users} — every user plus basic stats, oldest-first. */
    public List<AdminUserSummary> findAllUsersWithStats() {
        return jobsJdbcTemplate.query(
                "SELECT u.id, u.phone, u.email, u.created_at, "
                        + "COUNT(t.id) AS transaction_count, MAX(t.transaction_date) AS last_activity "
                        + "FROM users u LEFT JOIN transactions t ON t.user_id = u.id "
                        + "GROUP BY u.id, u.phone, u.email, u.created_at "
                        + "ORDER BY u.created_at",
                (rs, rowNum) -> {
                    Timestamp lastActivity = rs.getTimestamp("last_activity");
                    return new AdminUserSummary(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getLong("transaction_count"),
                            lastActivity == null ? null : lastActivity.toInstant());
                });
    }

    /** Every user id — backs the cross-user analytics aggregation loop (E11-S2-T2). */
    public List<UUID> findAllUserIds() {
        return jobsJdbcTemplate.query("SELECT id FROM users", (rs, rowNum) -> UUID.fromString(rs.getString("id")));
    }

    /** Admin may look up any user by id, not just the caller's own — see class javadoc. */
    public Optional<AdminUserCore> findUserCoreById(UUID userId) {
        return jobsJdbcTemplate
                .query(
                        "SELECT id, phone, email, google_id, created_at FROM users WHERE id = ?",
                        (rs, rowNum) -> new AdminUserCore(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("phone"),
                                rs.getString("email"),
                                rs.getString("google_id"),
                                rs.getTimestamp("created_at").toInstant()),
                        userId)
                .stream()
                .findFirst();
    }

    /** {@code GET /admin/logs} — optionally filtered by {@code event_type} (uses {@code idx_admin_logs_event_type}), recency order. */
    public List<AdminLogEntry> findLogs(String eventType) {
        String sql = "SELECT id, event_type, user_id, payload, created_at FROM admin_logs";
        if (eventType == null || eventType.isBlank()) {
            return jobsJdbcTemplate.query(sql + " ORDER BY created_at DESC", this::mapLogEntry);
        }
        return jobsJdbcTemplate.query(sql + " WHERE event_type = ? ORDER BY created_at DESC", this::mapLogEntry, eventType);
    }

    /** Erasure step 1 — ids of every {@code admin_logs} row referencing this user, captured before the cascade nulls them. */
    public List<UUID> findAdminLogIdsForUser(UUID userId) {
        return jobsJdbcTemplate.query(
                "SELECT id FROM admin_logs WHERE user_id = ?", (rs, rowNum) -> UUID.fromString(rs.getString("id")), userId);
    }

    /**
     * Erasure step 2 — cascades to every dependent table (see docs/database.md) and sets
     * {@code admin_logs.user_id = NULL} for the rows captured by {@link #findAdminLogIdsForUser}.
     * Returns the number of {@code users} rows deleted (0 or 1) so the caller can 404 on a
     * nonexistent id.
     */
    public int deleteUserCascade(UUID userId) {
        return jobsJdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    /**
     * Erasure step 3 — for each given {@code admin_logs} id, replaces every occurrence of any of
     * {@code identifyingStrings} (phone/email/google_id) in {@code event_type} and {@code payload}
     * with a redaction marker. Must run after {@link #deleteUserCascade} has already nulled
     * {@code user_id} on these rows (docs/security.md's DPDP erasure exception).
     */
    public void scrubAdminLogs(List<UUID> logIds, List<String> identifyingStrings) {
        List<String> nonBlank = identifyingStrings.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (logIds.isEmpty() || nonBlank.isEmpty()) {
            return;
        }
        for (UUID logId : logIds) {
            List<Map<String, String>> rows = jobsJdbcTemplate.query(
                    "SELECT event_type, payload FROM admin_logs WHERE id = ?",
                    (rs, rowNum) -> Map.of(
                            "eventType", rs.getString("event_type"), "payload", rs.getString("payload") == null ? "" : rs.getString("payload")),
                    logId);
            if (rows.isEmpty()) {
                continue;
            }
            String scrubbedEventType = redact(rows.get(0).get("eventType"), nonBlank);
            String scrubbedPayload = redact(rows.get(0).get("payload"), nonBlank);
            jobsJdbcTemplate.update(
                    "UPDATE admin_logs SET event_type = ?, payload = ?::jsonb WHERE id = ?",
                    scrubbedEventType,
                    scrubbedPayload.isEmpty() ? null : scrubbedPayload,
                    logId);
        }
    }

    private static String redact(String text, List<String> identifyingStrings) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (String identifying : identifyingStrings) {
            result = result.replace(identifying, "[REDACTED]");
        }
        return result;
    }

    private AdminLogEntry mapLogEntry(ResultSet rs, int rowNum) throws SQLException {
        String userId = rs.getString("user_id");
        return new AdminLogEntry(
                UUID.fromString(rs.getString("id")),
                rs.getString("event_type"),
                userId == null ? null : UUID.fromString(userId),
                parsePayload(rs.getString("payload")),
                rs.getTimestamp("created_at").toInstant());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize admin_logs payload", e);
        }
    }
}
