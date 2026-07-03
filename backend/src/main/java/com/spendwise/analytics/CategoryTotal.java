package com.spendwise.analytics;

import java.math.BigDecimal;

/**
 * Per-category spend/income/count for a date range — backs both `/analytics/summary`'s category
 * breakdown and `/analytics/categories`' drilldown list. Only categories with at least one
 * transaction in the range are represented (no zero-filled rows for untouched categories).
 */
public record CategoryTotal(int categoryId, String categoryName, BigDecimal totalSpend, BigDecimal totalIncome, long transactionCount) {}
