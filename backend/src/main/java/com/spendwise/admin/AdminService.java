package com.spendwise.admin;

import com.spendwise.analytics.AnalyticsComparison;
import com.spendwise.analytics.AnalyticsSummary;
import com.spendwise.categorization.dto.MlEvaluationResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Service interface for the Admin module (Epic 11) — consumed only by {@link AdminController}. */
public interface AdminService {

    /** E11-S2-T1 — every user plus basic stats. */
    List<AdminUserSummary> listUsers();

    /** @throws AdminUserNotFoundException if no user exists with this id */
    AdminUserDetail getUserDetail(UUID userId);

    /**
     * E11-S2-T2 — sums every user's {@code AnalyticsService.summary} result for the same range, so
     * the numbers match a manual sum of each user's own {@code /analytics/summary} exactly (Epic 7
     * logic reused, not reimplemented).
     */
    AnalyticsSummary getAggregateAnalytics(Instant from, Instant to);

    /** E11-S2-T2 — sums every user's {@code AnalyticsService.comparison} result for the same granularity. */
    AnalyticsComparison getAggregateComparison(String granularity);

    /** E11-S2-T3 — optionally filtered by {@code event_type}, recency order. */
    List<AdminLogEntry> getLogs(String eventType);

    /** E11-S2-T4 — delegates to {@code CategorizationService}; never touches the FastAPI client directly. */
    MlEvaluationResponse getMlAccuracy();

    /** E11-S2-T4 — delegates to {@code CategorizationService}; never touches the FastAPI client directly. */
    void triggerRetrain();

    /**
     * E11-S2-T5 — irreversible. Hard-deletes the user (cascades to every dependent table) and
     * scrubs any identifying string left behind in {@code admin_logs} (DPDP erasure exception).
     *
     * @throws AdminUserNotFoundException if no user exists with this id
     */
    void deleteUser(UUID userId);
}
