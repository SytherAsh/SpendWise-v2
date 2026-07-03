package com.spendwise.transaction;

import java.math.BigDecimal;

/** One user's spend in one category for one calendar month — backs `/budgets/suggestions` (E5-S1-T4). */
public record MonthlyCategorySpend(int categoryId, int month, int year, BigDecimal totalSpent) {}
