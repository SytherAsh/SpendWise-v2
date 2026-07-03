package com.spendwise.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cross-user, one row per (user, category) for a given calendar month, with the category's
 * display name already joined in — backs the Recommendation generator job (E8-S2-T1), which reads
 * this in bulk via the {@code spendwise_jobs} role rather than looping per-user through the
 * RLS-scoped path. Mirrors {@code com.spendwise.transaction.UserCategorySpend}'s shape (duplicated
 * rather than reused: Recommendations may only call Analytics per docs/architecture.md, and
 * Analytics never calls Transaction — see {@code AnalyticsBoundaryTest}).
 */
public record CategoryMonthSpend(UUID userId, int categoryId, String categoryName, BigDecimal totalSpent) {}
