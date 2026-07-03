package com.spendwise.budget;

import java.math.BigDecimal;

/**
 * E5-S1-T4 `/budgets/suggestions` row, one per category. {@code available = false} (with a null
 * {@code suggestedMonthlyLimit}) is the graceful "no history yet" case (E5-S1-T4 DoD) — not an
 * error, per docs/user_flows.md "First-Time User" edge case.
 */
public record BudgetSuggestion(int categoryId, BigDecimal suggestedMonthlyLimit, boolean available) {}
