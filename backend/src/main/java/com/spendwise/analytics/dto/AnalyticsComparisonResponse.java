package com.spendwise.analytics.dto;

import com.spendwise.analytics.AnalyticsComparison;

/** docs/api.md `GET /analytics/comparison` (E7-S1-T3). */
public record AnalyticsComparisonResponse(String granularity, PeriodTotalsResponse current, PeriodTotalsResponse previous) {

    public static AnalyticsComparisonResponse from(AnalyticsComparison comparison) {
        return new AnalyticsComparisonResponse(
                comparison.granularity(),
                PeriodTotalsResponse.from(comparison.current()),
                PeriodTotalsResponse.from(comparison.previous()));
    }
}
