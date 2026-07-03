package com.spendwise.budget;

import java.math.BigDecimal;

/** E5-S1-T3 `/budgets/progress` row — one category's budget vs. actual spend for the current month. */
public record BudgetProgress(int categoryId, BigDecimal monthlyLimit, BigDecimal spent, BigDecimal percentSpent) {}
