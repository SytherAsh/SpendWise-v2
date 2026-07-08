package com.spendwise.analytics;

import java.math.BigDecimal;

/**
 * Spend/income/count for transactions with no {@code transaction_categories} row in a date range
 * — the "Uncategorized" bucket that {@link CategoryTotal}'s inner-join query never represents.
 * Always returned as a single zero-or-more aggregate row (never absent), unlike {@link
 * CategoryTotal} rows which are only emitted per-category when at least one transaction exists.
 */
public record UncategorizedTotal(BigDecimal totalSpend, BigDecimal totalIncome, long transactionCount) {}
