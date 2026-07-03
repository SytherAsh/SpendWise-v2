package com.spendwise.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cross-user, one row per debit transaction within the recurring-detection lookback window —
 * backs the Alerts evaluator job's recurring-payment pass (E6-S2-T1), mirroring {@link
 * UserCategorySpend}'s bulk-read shape (see {@link TransactionService#findAllForRecurringDetection}).
 * {@code upiId}/{@code recipientName} are the merchant-identity fields E6-S1-T1's detector groups
 * on; rows where both are null are excluded at the query level since neither grouping key exists.
 */
public record RecurringCandidateTransaction(
        UUID userId, UUID transactionId, Instant transactionDate, BigDecimal amount, String upiId, String recipientName) {}
