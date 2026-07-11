package com.spendwise.alerts;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * ML strategy phase (2026-07-11) — mirrors {@code ml_corrections}' role for categorization: every
 * confirm/dismiss of a {@code recurring_payment} alert becomes a labeled training example for the
 * recurring-payment classifier's retrain cycle ({@code ml/api/retrain_recurring.py}), read by
 * {@code CategorizationServiceImpl#triggerRetrain}'s eventual recurring-retrain counterpart.
 *
 * <p>Must be called from within a {@code @Transactional} method, same requirement as {@code
 * EmiRepository}.
 */
@Repository
public class RecurringCorrectionsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public RecurringCorrectionsRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public void insert(
            UUID userId, UUID representativeTransactionId, RecurringCandidateFeatures features, boolean wasRecurring) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO recurring_corrections (id, user_id, representative_transaction_id, occurrence_count, "
                        + "interval_mean_days, interval_cv, amount_mean, amount_cv, span_days, days_since_last_occurrence, was_recurring) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                representativeTransactionId,
                features.occurrenceCount(),
                features.intervalMeanDays(),
                features.intervalCv(),
                features.amountMean(),
                features.amountCv(),
                features.spanDays(),
                features.daysSinceLastOccurrence(),
                wasRecurring);
    }
}
