package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code transaction_categories}' RLS policy (V5) is join-based (scoped via the owning
 * transaction's {@code user_id}), not a direct {@code user_id} column — {@link RlsSession}'s
 * session variable still governs it, so every method here still sets it first. Every query also
 * joins/guards on {@code transactions.user_id = ?} explicitly (CLAUDE.md: "RLS is a backstop,
 * not a substitute for explicit query scoping") rather than relying solely on RLS.
 */
@Repository
public class TransactionCategoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public TransactionCategoryRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public Optional<Integer> findCategoryId(UUID userId, UUID transactionId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query(
                        "SELECT tc.category_id FROM transaction_categories tc "
                                + "JOIN transactions t ON t.id = tc.transaction_id "
                                + "WHERE tc.transaction_id = ? AND t.user_id = ?",
                        (rs, rowNum) -> (Integer) rs.getObject("category_id"),
                        transactionId,
                        userId)
                .stream()
                .findFirst();
    }

    /** No-op (zero rows affected) if {@code transactionId} isn't owned by {@code userId} — the WHERE EXISTS guard below. */
    public void upsertUserAssignment(UUID userId, UUID transactionId, int categoryId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by, assigned_at) "
                        + "SELECT ?, ?, NULL, 'user', NOW() "
                        + "WHERE EXISTS (SELECT 1 FROM transactions WHERE id = ? AND user_id = ?) "
                        + "ON CONFLICT (transaction_id) DO UPDATE SET "
                        + "category_id = EXCLUDED.category_id, confidence_score = NULL, assigned_by = 'user', assigned_at = NOW()",
                transactionId,
                categoryId,
                transactionId,
                userId);
    }

    /**
     * Writes an ML-assigned category (E4-S3-T1) — the Categorization module's only write path
     * into this table, called after a confident {@code /predict} response. Never overwrites a
     * user's own correction: {@code DO UPDATE ... WHERE transaction_categories.assigned_by =
     * 'ml'} so a stale retry-job invocation can't clobber a manual override that landed in the
     * meantime. No-op if {@code transactionId} isn't owned by {@code userId}.
     */
    public void upsertMlAssignment(UUID userId, UUID transactionId, int categoryId, double confidence) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by, assigned_at) "
                        + "SELECT ?, ?, ?, 'ml', NOW() "
                        + "WHERE EXISTS (SELECT 1 FROM transactions WHERE id = ? AND user_id = ?) "
                        + "ON CONFLICT (transaction_id) DO UPDATE SET "
                        + "category_id = EXCLUDED.category_id, confidence_score = EXCLUDED.confidence_score, "
                        + "assigned_by = 'ml', assigned_at = NOW() "
                        + "WHERE transaction_categories.assigned_by = 'ml'",
                transactionId,
                categoryId,
                confidence,
                transactionId,
                userId);
    }
}
