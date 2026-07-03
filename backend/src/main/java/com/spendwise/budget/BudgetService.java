package com.spendwise.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for the Budget module. Consumed cross-module by Alerts (via {@link
 * #findAllForMonth}, docs/architecture.md "Alerts → Budget (read limits)").
 */
public interface BudgetService {

    /** Idempotent upsert (E5-S1-T1) — always targets the current calendar month. */
    Budget upsert(UUID userId, int categoryId, BigDecimal monthlyLimit);

    /** E5-S1-T2 — all budgets for the current calendar month. */
    List<Budget> listForCurrentMonth(UUID userId);

    /** E5-S1-T3 — budget vs. actual spend per category for the current calendar month. */
    List<BudgetProgress> progressForCurrentMonth(UUID userId);

    /** E5-S1-T4 — history-derived suggested limit per category, or "no suggestion" if no history exists. */
    List<BudgetSuggestion> suggestions(UUID userId);

    /**
     * Cross-user (E5-S2-T4) — every budget row for one calendar month, across all users. Backs
     * the Alerts evaluator job; bypasses RLS via the {@code spendwise_jobs} role.
     */
    List<Budget> findAllForMonth(int month, int year);
}
