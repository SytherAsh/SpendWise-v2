package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code transaction_categories}' RLS policy (V5) is join-based (scoped via the owning
 * transaction's {@code user_id}), not a direct {@code user_id} column — {@link RlsSession}'s
 * session variable still governs it, so every method here still sets it first.
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
                        "SELECT category_id FROM transaction_categories WHERE transaction_id = ?",
                        (rs, rowNum) -> (Integer) rs.getObject("category_id"),
                        transactionId)
                .stream()
                .findFirst();
    }

    public void upsertUserAssignment(UUID userId, UUID transactionId, int categoryId) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by, assigned_at) "
                        + "VALUES (?, ?, NULL, 'user', NOW()) "
                        + "ON CONFLICT (transaction_id) DO UPDATE SET "
                        + "category_id = EXCLUDED.category_id, confidence_score = NULL, assigned_by = 'user', assigned_at = NOW()",
                transactionId,
                categoryId);
    }
}
