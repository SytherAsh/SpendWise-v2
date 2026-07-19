package com.spendwise.admin;

import com.spendwise.admin.dto.JobScheduleResponse;
import com.spendwise.admin.dto.UpdateJobScheduleRequest;
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
     * Manual trigger for the weekly recipient-canonicalization sweep (ML strategy phase), so
     * testing/support doesn't have to wait for its next scheduled run (ADR-018: the schedule
     * itself is admin-configurable via {@link #listJobSchedules}/{@link #updateJobSchedule}, not
     * a fixed property anymore). Delegates to {@code CategorizationService}, same shape as
     * {@link #triggerRetrain}.
     */
    void triggerCanonicalization();

    /**
     * Manual trigger for {@code CategorizationRetryJob} (ML strategy phase, 2026-07-19) — the
     * "admin can run every scheduled job on demand" feature. Unlike {@link #triggerRetrain}/
     * {@link #triggerCanonicalization}, this delegates to a {@code ManuallyTriggerableJob} rather
     * than a module service method — see that interface's javadoc for why.
     */
    void triggerCategorizationRetry();

    /** Manual trigger for {@code AlertEvaluatorJob} (budget/overspend alerts + recurring-payment detection). */
    void triggerAlertEvaluation();

    /** Manual trigger for {@code RecommendationGeneratorJob} — makes a real (billed) LLM call per candidate. */
    void triggerRecommendationGeneration();

    /**
     * ADR-018 (2026-07-19) — every background job's current admin-configurable schedule, for the
     * admin portal's "Scheduled Jobs" page.
     */
    List<JobScheduleResponse> listJobSchedules();

    /**
     * Persists the new schedule for {@code jobKey} and forces it to take effect immediately
     * ({@code DynamicJobScheduler#reschedule}) rather than waiting for whichever run was already
     * locked in under the old schedule.
     *
     * @throws com.spendwise.common.schedule.InvalidJobScheduleException if request is malformed
     * @throws com.spendwise.common.schedule.JobScheduleNotFoundException if jobKey is unknown
     */
    void updateJobSchedule(String jobKey, UpdateJobScheduleRequest request);

    /**
     * E11-S2-T5 — irreversible. Hard-deletes the user (cascades to every dependent table) and
     * scrubs any identifying string left behind in {@code admin_logs} (DPDP erasure exception).
     *
     * @throws AdminUserNotFoundException if no user exists with this id
     */
    void deleteUser(UUID userId);
}
