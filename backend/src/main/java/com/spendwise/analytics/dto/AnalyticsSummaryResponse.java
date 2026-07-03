package com.spendwise.analytics.dto;

import com.spendwise.analytics.AnalyticsSummary;

import java.math.BigDecimal;
import java.util.List;

/** docs/api.md `GET /analytics/summary` (E7-S1-T1). */
public record AnalyticsSummaryResponse(BigDecimal totalSpend, BigDecimal totalIncome, List<CategoryTotalResponse> categories) {

    public static AnalyticsSummaryResponse from(AnalyticsSummary summary) {
        return new AnalyticsSummaryResponse(
                summary.overall().totalSpend(),
                summary.overall().totalIncome(),
                summary.categories().stream().map(CategoryTotalResponse::from).toList());
    }
}
