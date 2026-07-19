package com.spendwise.recommendations;

import com.spendwise.analytics.AnalyticsService;
import com.spendwise.analytics.CategoryMonthSpend;
import com.spendwise.common.db.AdminEventLog;
import com.spendwise.common.llm.LlmClient;
import com.spendwise.common.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Required test for E8-S2-T1 (docs/testing.md Recommendations unit tests). */
class RecommendationGeneratorJobTest {

    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final RecommendationsService recommendationsService = mock(RecommendationsService.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final AdminEventLog adminEventLog = mock(AdminEventLog.class);
    private final RecommendationGeneratorJob job =
            new RecommendationGeneratorJob(analyticsService, recommendationsService, llmClient, adminEventLog);

    private final YearMonth currentMonth = YearMonth.now();
    private final YearMonth previousMonth = currentMonth.minusMonths(1);

    @Test
    void aTwentyPercentIncreaseOverAQualifyingBaselineGeneratesOneRecommendation() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(
                List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(3200))),
                List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(2600))) // +23%
                );
        given(llmClient.complete(any(), any())).willReturn(new LlmResponse("You spent more on Food."));

        job.run();

        verify(recommendationsService)
                .recordIfNoActiveRecommendationExists(eq(userId), eq(1), eq("You spent more on Food."), eq(RecommendationPriority.MEDIUM));
        verify(adminEventLog).record(eq("recommendation_generation_run"), isNull(), eq(Map.of("status", "success", "candidatesEvaluated", 1)));
    }

    @Test
    void anIncreaseBelowTwentyPercentDoesNotGenerateARecommendation() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(
                List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(2800))),
                List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(2600))) // +7.7%
                );

        job.run();

        verify(recommendationsService, never()).recordIfNoActiveRecommendationExists(any(), anyInt(), any(), any());
    }

    @Test
    void aQualifyingIncreaseOnABaselineBelowTheMinimumIsIgnored() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(
                List.of(new CategoryMonthSpend(userId, 1, "Misc", BigDecimal.valueOf(100))),
                List.of(new CategoryMonthSpend(userId, 1, "Misc", BigDecimal.valueOf(50))) // +100%, but baseline < 200
                );

        job.run();

        verify(recommendationsService, never()).recordIfNoActiveRecommendationExists(any(), anyInt(), any(), any());
    }

    @Test
    void aCategoryWithNoPreviousMonthBaselineIsSkipped() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(3200))), List.of());

        job.run();

        verify(recommendationsService, never()).recordIfNoActiveRecommendationExists(any(), anyInt(), any(), any());
    }

    @Test
    void aSuppressedRecommendationIsStillJustSkipped() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(
                List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(3200))),
                List.of(new CategoryMonthSpend(userId, 1, "Food", BigDecimal.valueOf(2600))));
        given(llmClient.complete(any(), any())).willReturn(new LlmResponse("text"));
        given(recommendationsService.recordIfNoActiveRecommendationExists(any(), anyInt(), any(), any())).willReturn(Optional.empty());

        assertThatCode(job::run).doesNotThrowAnyException();
    }

    @Test
    void bulkLookupFailureDoesNotThrow() {
        given(analyticsService.findAllCategorySpendForMonth(anyInt(), anyInt())).willThrow(new RuntimeException("spendwise_jobs connection lost"));

        assertThatCode(job::run).doesNotThrowAnyException();
        verify(adminEventLog)
                .record(
                        eq("recommendation_generation_run"),
                        isNull(),
                        eq(Map.of("status", "failure", "stage", "lookup", "error", "spendwise_jobs connection lost")));
    }

    @Test
    void runNowDelegatesToRun() {
        stubBulkReads(List.of(), List.of());

        job.runNow();

        verify(adminEventLog).record(eq("recommendation_generation_run"), isNull(), eq(Map.of("status", "success", "candidatesEvaluated", 0)));
    }

    @Test
    void oneCategoryFailingDoesNotStopEvaluationOfTheRest() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        stubBulkReads(
                List.of(
                        new CategoryMonthSpend(userA, 1, "Food", BigDecimal.valueOf(3200)),
                        new CategoryMonthSpend(userB, 2, "Travel", BigDecimal.valueOf(3200))),
                List.of(
                        new CategoryMonthSpend(userA, 1, "Food", BigDecimal.valueOf(2600)),
                        new CategoryMonthSpend(userB, 2, "Travel", BigDecimal.valueOf(2600))));
        given(llmClient.complete(any(), any())).willReturn(new LlmResponse("text"));
        given(recommendationsService.recordIfNoActiveRecommendationExists(eq(userA), any(), any(), any()))
                .willThrow(new RuntimeException("unexpected"));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(recommendationsService).recordIfNoActiveRecommendationExists(eq(userB), eq(2), any(), eq(RecommendationPriority.MEDIUM));
    }

    private void stubBulkReads(List<CategoryMonthSpend> current, List<CategoryMonthSpend> previous) {
        given(analyticsService.findAllCategorySpendForMonth(currentMonth.getMonthValue(), currentMonth.getYear())).willReturn(current);
        given(analyticsService.findAllCategorySpendForMonth(previousMonth.getMonthValue(), previousMonth.getYear())).willReturn(previous);
    }
}
