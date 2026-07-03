package com.spendwise.analytics;

/** docs/api.md `GET /analytics/comparison` (E7-S1-T3) — current period vs. the immediately preceding one of the same length. */
public record AnalyticsComparison(String granularity, ComparisonPeriod current, ComparisonPeriod previous) {}
