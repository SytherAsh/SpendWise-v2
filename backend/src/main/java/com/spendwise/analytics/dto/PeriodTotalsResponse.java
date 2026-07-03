package com.spendwise.analytics.dto;

import com.spendwise.analytics.ComparisonPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PeriodTotalsResponse(
        Instant from, Instant to, BigDecimal totalSpend, BigDecimal totalIncome, List<CategoryTotalResponse> categories) {

    public static PeriodTotalsResponse from(ComparisonPeriod period) {
        return new PeriodTotalsResponse(
                period.from(),
                period.to(),
                period.overall().totalSpend(),
                period.overall().totalIncome(),
                period.categories().stream().map(CategoryTotalResponse::from).toList());
    }
}
