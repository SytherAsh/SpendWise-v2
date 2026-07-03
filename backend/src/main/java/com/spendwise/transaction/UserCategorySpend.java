package com.spendwise.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cross-user, one row per (user, category) for a given month — backs the Alerts evaluator job
 * (E5-S2-T4), which reads this in bulk via the {@code spendwise_jobs} role rather than looping
 * per-user through the RLS-scoped path (see {@link TransactionService#findAllSpendForMonth}).
 */
public record UserCategorySpend(UUID userId, int categoryId, BigDecimal totalSpent) {}
