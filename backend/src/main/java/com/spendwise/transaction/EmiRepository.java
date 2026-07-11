package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            rs.getString("source_transaction_id") == null ? null : UUID.fromString(rs.getString("source_transaction_id")),
            rs.getString("cadence"),
            (Double) rs.getObject("confidence_score"));

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final JdbcTemplate jobsJdbcTemplate;

    public EmiRepository(
            JdbcTemplate jdbcTemplate, RlsSession rlsSession, @Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.jobsJdbcTemplate = jobsJdbcTemplate;
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
        return new Emi(id, userId, label, amount, dueDay, false, true, null, null, null);
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

    /** Idempotency lookup for the confirm flow (E6-S2-T2) — {@code idx_emis_source_txn} is unique per transaction. */
    public Optional<Emi> findBySourceTransactionId(UUID userId, UUID sourceTransactionId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query("SELECT * FROM emis WHERE user_id = ? AND source_transaction_id = ?", ROW_MAPPER, userId, sourceTransactionId)
                .stream()
                .findFirst();
    }

    /**
     * Confirm flow (E6-S2-T2) — {@code due_day} is always {@code NULL} rather than inferred from
     * the source transaction's date; the user can set it afterwards via {@code PUT /emis/:id}.
     * {@code cadence}/{@code confidenceScore} (ML strategy phase, 2026-07-11) carry over the ML
     * prediction that produced the alert being confirmed — both nullable, since a pre-ML alert
     * confirmed after this migration lands would have neither.
     */
    public Emi insertFromDetection(
            UUID userId, String label, BigDecimal amount, UUID sourceTransactionId, String cadence, Double confidenceScore) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO emis (id, user_id, label, amount, due_day, detected_from_sms, is_active, source_transaction_id, cadence, confidence_score) "
                        + "VALUES (?, ?, ?, ?, NULL, TRUE, TRUE, ?, ?, ?)",
                id,
                userId,
                label,
                amount,
                sourceTransactionId,
                cadence,
                confidenceScore);
        return new Emi(id, userId, label, amount, null, true, true, sourceTransactionId, cadence, confidenceScore);
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

    /**
     * Cross-user (E6-S1-T1) — {@code source_transaction_id} of every active {@code emis} row that
     * has one, via the {@code spendwise_jobs} role, mirroring {@code TransactionRepository}'s bulk
     * job reads. Backs the recurring-payment detector's exclusion rule: deliberately exact-match
     * only (see {@code RecurringPaymentDetector}'s Javadoc for why label/amount correlation isn't
     * used) — never called from a per-request path.
     */
    public Set<UUID> findAllActiveSourceTransactionIds() {
        List<UUID> ids = jobsJdbcTemplate.query(
                "SELECT source_transaction_id FROM emis WHERE is_active = TRUE AND source_transaction_id IS NOT NULL",
                (rs, rowNum) -> UUID.fromString(rs.getString("source_transaction_id")));
        return new HashSet<>(ids);
    }
}
