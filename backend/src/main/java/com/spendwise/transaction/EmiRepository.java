package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so the RLS
 * session variable and the query that depends on it share the same connection.
 */
@Repository
public class EmiRepository {

    private static final RowMapper<Emi> ROW_MAPPER = (rs, rowNum) -> new Emi(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getString("label"),
            rs.getBigDecimal("amount"),
            (Integer) rs.getObject("due_day"),
            rs.getBoolean("detected_from_sms"),
            rs.getBoolean("is_active"),
            rs.getString("source_transaction_id") == null ? null : UUID.fromString(rs.getString("source_transaction_id")));

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public EmiRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public Emi insertManual(UUID userId, String label, BigDecimal amount, Integer dueDay) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO emis (id, user_id, label, amount, due_day, detected_from_sms, is_active, source_transaction_id) "
                        + "VALUES (?, ?, ?, ?, ?, FALSE, TRUE, NULL)",
                id,
                userId,
                label,
                amount,
                dueDay);
        return new Emi(id, userId, label, amount, dueDay, false, true, null);
    }

    /** Default list per docs/api.md "GET /emis" — active EMIs only. */
    public List<Emi> findActiveForUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT * FROM emis WHERE user_id = ? AND is_active = TRUE ORDER BY due_day NULLS LAST, label",
                ROW_MAPPER,
                userId);
    }

    /** "Show all" admin-style query (E3-S3-T2 DoD) — includes deactivated rows. Not wired to a controller yet. */
    public List<Emi> findAllForUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT * FROM emis WHERE user_id = ? ORDER BY due_day NULLS LAST, label", ROW_MAPPER, userId);
    }

    public Optional<Emi> findById(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query("SELECT * FROM emis WHERE user_id = ? AND id = ?", ROW_MAPPER, userId, id).stream()
                .findFirst();
    }

    /** Never hard-deletes — updates label/amount/due_day only (E3-S3-T2 DoD). */
    public void update(UUID userId, UUID id, String label, BigDecimal amount, Integer dueDay) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "UPDATE emis SET label = ?, amount = ?, due_day = ? WHERE user_id = ? AND id = ?",
                label,
                amount,
                dueDay,
                userId,
                id);
    }

    public void deactivate(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE emis SET is_active = FALSE WHERE user_id = ? AND id = ?", userId, id);
    }
}
