package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Same join-based RLS scoping note as {@link TransactionCategoryRepository}, including the explicit ownership guard. */
@Repository
public class MlCorrectionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final JdbcTemplate jobsJdbcTemplate;

    public MlCorrectionRepository(
            JdbcTemplate jdbcTemplate, RlsSession rlsSession, @Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.jobsJdbcTemplate = jobsJdbcTemplate;
    }

    /**
     * @param oldCategoryId null if the transaction had no prior category assignment.
     *     Callers must not invoke this when {@code oldCategoryId} equals {@code newCategoryId} —
     *     {@code chk_correction_different_category} rejects that as a useless labeled example
     *     (E3-S2-T4 treats a no-op correction as a no-op, not a write). No-op (zero rows
     *     affected) if {@code transactionId} isn't owned by {@code userId}.
     */
    public void insert(UUID userId, UUID transactionId, Integer oldCategoryId, int newCategoryId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO ml_corrections (id, transaction_id, old_category_id, new_category_id, corrected_at) "
                        + "SELECT ?, ?, ?, ?, NOW() "
                        + "WHERE EXISTS (SELECT 1 FROM transactions WHERE id = ? AND user_id = ?)",
                UUID.randomUUID(),
                transactionId,
                oldCategoryId,
                newCategoryId,
                transactionId,
                userId);
    }

    /**
     * Cross-user (E4-S3-T4) — every corrected transaction's feature fields, joined from {@code
     * transactions}, plus the corrected category. Reads via {@code spendwise_jobs}
     * ({@code BYPASSRLS}), never the RLS-scoped {@code jdbcTemplate} above. Reads ALL corrections
     * (not just since the last retrain) per ADR-003 — each retrain fits a fresh model on the
     * baseline dataset plus every accumulated correction, so a stale correction dropped here
     * would be silently forgotten by the new model.
     */
    public List<MlCorrectionRecord> findAllCorrections() {
        return jobsJdbcTemplate.query(
                "SELECT t.recipient_name, t.upi_id, t.bank, t.transaction_mode, t.amount, t.note, mc.new_category_id "
                        + "FROM ml_corrections mc JOIN transactions t ON t.id = mc.transaction_id",
                (rs, rowNum) -> new MlCorrectionRecord(
                        rs.getString("recipient_name"),
                        rs.getString("upi_id"),
                        rs.getString("bank"),
                        rs.getString("transaction_mode"),
                        rs.getBigDecimal("amount"),
                        rs.getString("note"),
                        rs.getInt("new_category_id")));
    }
}
