package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Same join-based RLS scoping note as {@link TransactionCategoryRepository}. */
@Repository
public class MlCorrectionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public MlCorrectionRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    /**
     * @param oldCategoryId null if the transaction had no prior category assignment.
     *     Callers must not invoke this when {@code oldCategoryId} equals {@code newCategoryId} —
     *     {@code chk_correction_different_category} rejects that as a useless labeled example
     *     (E3-S2-T4 treats a no-op correction as a no-op, not a write).
     */
    public void insert(UUID userId, UUID transactionId, Integer oldCategoryId, int newCategoryId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO ml_corrections (id, transaction_id, old_category_id, new_category_id, corrected_at) "
                        + "VALUES (?, ?, ?, ?, NOW())",
                UUID.randomUUID(),
                transactionId,
                oldCategoryId,
                newCategoryId);
    }
}
