package com.spendwise.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so the RLS
 * session variable and the query that depends on it share the same connection.
 */
@Repository
public class AlertRepository {

    private static final String SELECT_COLUMNS = "id, user_id, type, priority, triggered_at, delivered_at, is_read, payload";

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final ObjectMapper objectMapper;
    private final RowMapper<Alert> rowMapper = (rs, rowNum) -> mapRow(rs);

    public AlertRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.objectMapper = objectMapper;
    }

    public Alert insert(UUID userId, AlertType type, AlertPriority priority, Map<String, Object> payload) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO alerts (id, user_id, type, priority, payload) VALUES (?, ?, ?::alert_type, ?, ?::jsonb) "
                        + "RETURNING " + SELECT_COLUMNS,
                rowMapper,
                id,
                userId,
                type.dbValue(),
                priority.dbValue(),
                toJson(payload));
    }

    /**
     * Suppression check (E5-S2-T3 DoD, applied uniformly to all three rules per the Epic 5
     * handoff decision — see implementation/tracking/STATUS.md) — has an alert of this exact
     * type (and, if {@code categoryId} is non-null, this exact category) already fired for this
     * user since {@code since}? {@code categoryId} is null for {@code mid_month_budget}, which
     * has no per-category scope.
     */
    public boolean existsSince(UUID userId, AlertType type, Integer categoryId, Instant since) {
        rlsSession.setCurrentUser(userId);
        Integer count;
        if (categoryId != null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alerts WHERE user_id = ? AND type = ?::alert_type AND triggered_at >= ? "
                            + "AND (payload->>'category_id')::int = ?",
                    Integer.class,
                    userId,
                    type.dbValue(),
                    Timestamp.from(since),
                    categoryId);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alerts WHERE user_id = ? AND type = ?::alert_type AND triggered_at >= ?",
                    Integer.class,
                    userId,
                    type.dbValue(),
                    Timestamp.from(since));
        }
        return count != null && count > 0;
    }

    public void markDelivered(UUID userId, UUID alertId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE alerts SET delivered_at = NOW() WHERE user_id = ? AND id = ?", userId, alertId);
    }

    public Optional<Alert> findById(UUID userId, UUID alertId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query("SELECT " + SELECT_COLUMNS + " FROM alerts WHERE user_id = ? AND id = ?", rowMapper, userId, alertId)
                .stream()
                .findFirst();
    }

    public Optional<Instant> findTriggeredAt(UUID userId, UUID alertId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query(
                        "SELECT triggered_at FROM alerts WHERE user_id = ? AND id = ?",
                        (rs, rowNum) -> rs.getTimestamp("triggered_at").toInstant(),
                        userId,
                        alertId)
                .stream()
                .findFirst();
    }

    /** Never touches {@code delivered_at} — a separate, earlier, server-set event (docs/api.md). */
    public void markRead(UUID userId, UUID alertId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE alerts SET is_read = TRUE WHERE user_id = ? AND id = ?", userId, alertId);
    }

    /**
     * Newest-first cursor page, mirroring {@code TransactionRepository.listPage}'s
     * {@code (triggered_at, id)} compound seek. {@code cursorDate}/{@code cursorId} must both be
     * null (first page) or both non-null.
     */
    public List<Alert> findPage(UUID userId, Boolean isRead, Instant cursorDate, UUID cursorId, int limitPlusOne) {
        rlsSession.setCurrentUser(userId);
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM alerts WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (isRead != null) {
            sql.append(" AND is_read = ?");
            args.add(isRead);
        }
        if (cursorDate != null && cursorId != null) {
            sql.append(" AND (triggered_at, id) < (?, ?::uuid)");
            args.add(Timestamp.from(cursorDate));
            args.add(cursorId.toString());
        }
        sql.append(" ORDER BY triggered_at DESC, id DESC LIMIT ?");
        args.add(limitPlusOne);
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    private Alert mapRow(ResultSet rs) throws SQLException {
        Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        return new Alert(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                AlertType.fromDbValue(rs.getString("type")),
                AlertPriority.fromDbValue(rs.getString("priority")),
                rs.getTimestamp("triggered_at").toInstant(),
                deliveredAt == null ? null : deliveredAt.toInstant(),
                rs.getBoolean("is_read"),
                fromJson(rs.getString("payload")));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize alert payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize alert payload", e);
        }
    }
}
