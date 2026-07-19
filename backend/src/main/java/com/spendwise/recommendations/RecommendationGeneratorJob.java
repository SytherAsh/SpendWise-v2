package com.spendwise.recommendations;

import com.spendwise.analytics.AnalyticsService;
import com.spendwise.analytics.CategoryMonthSpend;
import com.spendwise.common.db.AdminEventLog;
import com.spendwise.common.job.ManuallyTriggerableJob;
import com.spendwise.common.llm.LlmClient;
import com.spendwise.common.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E8-S2-T1 — runs across every user on a schedule, per docs/architecture.md's Background Jobs
 * table ("Recommendation generator — every 6 hours"). Cross-user by nature — {@link
 * AnalyticsService#findAllCategorySpendForMonth} reads via the {@code spendwise_jobs} role (see
 * {@code com.spendwise.common.db.JobsDataSourceConfig}), mirroring {@code AlertEvaluatorJob}.
 * Persisting a resulting recommendation (via {@link RecommendationsService}) then re-scopes to one
 * user at a time through the normal RLS-enforced path, exactly like {@code AlertEvaluatorJob} does
 * for alerts.
 *
 * <p>Fires a recommendation for a user+category when this month's spend is at least 20% higher
 * than last month's, provided last month's spend was at least ₹200 (avoids noise on trivial
 * categories) — neither number is specified in docs/requirements.md's illustrative-only "38% more
 * than last month" example; both were confirmed as explicit project-owner defaults during the
 * Epic 8 handoff review, the same "propose a default, document as an assumption" pattern Epic 5's
 * suggestion-averaging and Epic 7's comparison-anchoring already used.
 *
 * <p>Implements {@link ManuallyTriggerableJob} both for Admin's manual "run now" trigger and as
 * the scheduling entry point {@code com.spendwise.common.schedule.DynamicJobScheduler} calls on
 * whatever cadence the admin-configurable {@code job_schedules} row for
 * {@code "recommendation_generation"} currently says (ADR-018) — no more static {@code @Scheduled}
 * annotation. See {@link ManuallyTriggerableJob}'s own javadoc for why this shape rather than
 * routing through {@link RecommendationsService}. Note this makes a real (billed) LLM call per
 * candidate — a manual trigger from the admin portal has the same cost as a scheduled run, not a
 * free dry run.
 */
@Component
public class RecommendationGeneratorJob implements ManuallyTriggerableJob {

    private static final Logger log = LoggerFactory.getLogger(RecommendationGeneratorJob.class);
    private static final BigDecimal MIN_BASELINE_SPEND = BigDecimal.valueOf(200);
    private static final BigDecimal THRESHOLD_INCREASE_RATIO = BigDecimal.valueOf(20, 2); // 0.20
    private static final String PROMPT =
            "Generate a one-line savings recommendation for a month-over-month category spend increase.";

    private final AnalyticsService analyticsService;
    private final RecommendationsService recommendationsService;
    private final LlmClient llmClient;
    private final AdminEventLog adminEventLog;

    public RecommendationGeneratorJob(
            AnalyticsService analyticsService,
            RecommendationsService recommendationsService,
            LlmClient llmClient,
            AdminEventLog adminEventLog) {
        this.analyticsService = analyticsService;
        this.recommendationsService = recommendationsService;
        this.llmClient = llmClient;
        this.adminEventLog = adminEventLog;
    }

    @Override
    public void runNow() {
        run();
    }

    /** Package-visible so tests can invoke it directly rather than waiting on the real schedule. */
    void run() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        List<CategoryMonthSpend> currentSpend;
        List<CategoryMonthSpend> previousSpend;
        try {
            currentSpend = analyticsService.findAllCategorySpendForMonth(currentMonth.getMonthValue(), currentMonth.getYear());
            previousSpend = analyticsService.findAllCategorySpendForMonth(previousMonth.getMonthValue(), previousMonth.getYear());
        } catch (RuntimeException e) {
            // The next scheduled run retries -- a transient failure here (e.g. spendwise_jobs
            // connection issue) must not crash the scheduler thread.
            log.warn("Recommendation generator's bulk lookup failed: {}", e.getMessage());
            adminEventLog.record(
                    "recommendation_generation_run",
                    null,
                    Map.of("status", "failure", "stage", "lookup", "error", String.valueOf(e.getMessage())));
            return;
        }

        Map<UserCategoryKey, CategoryMonthSpend> previousByKey = new HashMap<>();
        for (CategoryMonthSpend previous : previousSpend) {
            previousByKey.put(new UserCategoryKey(previous.userId(), previous.categoryId()), previous);
        }

        for (CategoryMonthSpend current : currentSpend) {
            try {
                evaluate(current, previousByKey.get(new UserCategoryKey(current.userId(), current.categoryId())));
            } catch (RuntimeException e) {
                // One user+category's failure must not stop the rest.
                log.warn(
                        "Recommendation generation failed for user {} category {}: {}",
                        current.userId(),
                        current.categoryId(),
                        e.getMessage());
            }
        }
        adminEventLog.record("recommendation_generation_run", null, Map.of("status", "success", "candidatesEvaluated", currentSpend.size()));
    }

    private void evaluate(CategoryMonthSpend current, CategoryMonthSpend previous) {
        if (previous == null || previous.totalSpent().compareTo(MIN_BASELINE_SPEND) < 0) {
            return;
        }
        BigDecimal increaseRatio =
                current.totalSpent().subtract(previous.totalSpent()).divide(previous.totalSpent(), 4, RoundingMode.HALF_UP);
        if (increaseRatio.compareTo(THRESHOLD_INCREASE_RATIO) < 0) {
            return;
        }

        int percentIncrease = increaseRatio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        Map<String, Object> context = Map.of(
                "categoryName", current.categoryName(),
                "currentMonthSpend", current.totalSpent(),
                "previousMonthSpend", previous.totalSpent(),
                "percentIncrease", percentIncrease);
        LlmResponse response = llmClient.complete(PROMPT, context);

        recommendationsService.recordIfNoActiveRecommendationExists(
                current.userId(), current.categoryId(), response.text(), RecommendationPriority.MEDIUM);
    }

    private record UserCategoryKey(UUID userId, int categoryId) {}
}
