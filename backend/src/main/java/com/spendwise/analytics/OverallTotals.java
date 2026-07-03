package com.spendwise.analytics;

import java.math.BigDecimal;

/** Overall (not per-category) spend/income for a date range — docs/api.md `/analytics/summary`. */
public record OverallTotals(BigDecimal totalSpend, BigDecimal totalIncome) {}
