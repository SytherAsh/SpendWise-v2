package com.spendwise.alerts;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One candidate recurring-charge group produced by {@link RecurringPaymentDetector} for a single
 * user. {@code representativeTransactionId} is the most recent (max {@code transactionDate})
 * non-excluded transaction in the group — it becomes {@code emis.source_transaction_id} if the
 * user confirms the resulting alert (E6-S2-T2).
 *
 * <p>{@code features} (ML strategy phase, 2026-07-11) is the statistical summary {@link
 * com.spendwise.categorization.CategorizationService#predictRecurring} scores — a candidate
 * qualifying for this record no longer means "alert the user" by itself (the loosened thresholds
 * that produce it deliberately admit borderline cases); {@link AlertEvaluatorJob} only creates an
 * alert once the ML model judges {@code features} as actually recurring.
 */
public record RecurringGroup(
        String merchantKey,
        String merchantLabel,
        BigDecimal representativeAmount,
        UUID representativeTransactionId,
        List<UUID> transactionIds,
        RecurringCandidateFeatures features) {}
