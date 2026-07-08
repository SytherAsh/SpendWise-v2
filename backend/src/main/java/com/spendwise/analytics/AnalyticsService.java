package com.spendwise.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Service interface for the Analytics module — read-only, consumed only by {@link AnalyticsController}. */
public interface AnalyticsService {

    /** @throws InvalidAnalyticsQueryException if {@code from}/{@code to} are missing or {@code from} is after {@code to} */
    AnalyticsSummary summary(UUID userId, Instant from, Instant to);

    /** @throws InvalidAnalyticsQueryException if {@code from}/{@code to} are missing or {@code from} is after {@code to} */
    List<CategoryTotal> categoryBreakdown(UUID userId, Instant from, Instant to);

    /** @throws InvalidAnalyticsQueryException if {@code from}/{@code to} are missing or {@code from} is after {@code to} */
    UncategorizedTotal uncategorizedTotal(UUID userId, Instant from, Instant to);

    /**
     * Current calendar period (week/month/year, per {@code granularity}) vs. the immediately
     * preceding period of the same length, anchored to today (server clock, UTC).
     *
     * @throws InvalidAnalyticsQueryException if {@code granularity} isn't week/month/year
     */
    AnalyticsComparison comparison(UUID userId, String granularity);

    /** @throws InvalidAnalyticsQueryException if {@code granularity} isn't week/month/year, or {@code from}/{@code to} are missing/invalid */
    AnalyticsTrends trends(UUID userId, String granularity, Instant from, Instant to, Integer categoryId);

    /** Unpaginated rows for CSV/PDF export. @throws InvalidAnalyticsQueryException if {@code from}/{@code to} are missing or {@code from} is after {@code to} */
    List<AnalyticsExportRow> exportRows(UUID userId, Instant from, Instant to);

    /**
     * Cross-user (E8-S2-T1) — every user's per-category spend for one calendar month, backing the
     * Recommendation generator job. Bypasses RLS via the {@code spendwise_jobs} role, same pattern
     * as {@code TransactionService.findAllSpendForMonth}; never called from a per-request path.
     */
    List<CategoryMonthSpend> findAllCategorySpendForMonth(int month, int year);
}
