package com.spendwise.alerts;

import com.spendwise.budget.Budget;
import com.spendwise.budget.BudgetService;
import com.spendwise.transaction.EmiService;
import com.spendwise.transaction.RecurringCandidateTransaction;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UserCategorySpend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

/** Required test for E5-S2-T4 (docs/testing.md Alerts evaluation engine unit tests). */
class AlertEvaluatorJobTest {

    private final BudgetService budgetService = mock(BudgetService.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final EmiService emiService = mock(EmiService.class);
    private final AlertsService alertsService = mock(AlertsService.class);
    private final AlertDispatchService alertDispatchService = mock(AlertDispatchService.class);
    private final AlertEvaluatorJob job =
            new AlertEvaluatorJob(budgetService, transactionService, emiService, alertsService, alertDispatchService);

    private final YearMonth currentMonth = YearMonth.now();

    @Test
    void categoryOverspendFiresAndDispatchesWhenAtOrAboveLimit() {
        UUID userId = UUID.randomUUID();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        stubBulkReads(List.of(budget), List.of(new UserCategorySpend(userId, 1, BigDecimal.valueOf(1500))));
        Alert alert = new Alert(UUID.randomUUID(), userId, AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH, Instant.now(), null, false, Map.of());
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(1), eq(AlertPriority.HIGH), any()))
                .willReturn(Optional.of(alert));

        job.run();

        verify(alertsService).recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(1), eq(AlertPriority.HIGH), any());
        verify(alertDispatchService).dispatch(alert);
    }

    @Test
    void categoryApproachingLimitFiresAtEightyPercentAndIsNotDispatchedAsHighPriority() {
        UUID userId = UUID.randomUUID();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        stubBulkReads(List.of(budget), List.of(new UserCategorySpend(userId, 1, BigDecimal.valueOf(800))));
        Alert alert = new Alert(UUID.randomUUID(), userId, AlertType.CATEGORY_APPROACHING_LIMIT, AlertPriority.MEDIUM, Instant.now(), null, false, Map.of());
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(
                        eq(userId), eq(AlertType.CATEGORY_APPROACHING_LIMIT), eq(1), eq(AlertPriority.MEDIUM), any()))
                .willReturn(Optional.of(alert));

        job.run();

        verify(alertsService)
                .recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.CATEGORY_APPROACHING_LIMIT), eq(1), eq(AlertPriority.MEDIUM), any());
        verify(alertDispatchService, never()).dispatch(any());
    }

    @Test
    void overspendTakesPrecedenceOverApproachingLimitForTheSameCategory() {
        UUID userId = UUID.randomUUID();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        stubBulkReads(List.of(budget), List.of(new UserCategorySpend(userId, 1, BigDecimal.valueOf(1000))));
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(any(), any(), anyInt(), any(), any())).willReturn(Optional.empty());

        job.run();

        verify(alertsService).recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(1), any(), any());
        verify(alertsService, never()).recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.CATEGORY_APPROACHING_LIMIT), anyInt(), any(), any());
    }

    @Test
    void suppressedAlertIsNeverDispatched() {
        UUID userId = UUID.randomUUID();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        stubBulkReads(List.of(budget), List.of(new UserCategorySpend(userId, 1, BigDecimal.valueOf(1500))));
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(any(), any(), anyInt(), any(), any())).willReturn(Optional.empty());

        job.run();

        verify(alertDispatchService, never()).dispatch(any());
    }

    @Test
    void aUserWithNoBudgetsIsSkippedEntirely() {
        stubBulkReads(List.of(), List.of());

        job.run();

        verify(alertsService, never()).recordIfNotAlreadyTriggeredThisMonth(any(), any(), anyInt(), any(), any());
    }

    @Test
    void bulkLookupFailureDoesNotThrow() {
        given(budgetService.findAllForMonth(anyInt(), anyInt())).willThrow(new RuntimeException("spendwise_jobs connection lost"));

        assertThatCode(job::run).doesNotThrowAnyException();
    }

    @Test
    void oneUserFailingDoesNotStopEvaluationOfTheRest() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Budget budgetA = new Budget(UUID.randomUUID(), userA, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        Budget budgetB = new Budget(UUID.randomUUID(), userB, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        stubBulkReads(
                List.of(budgetA, budgetB),
                List.of(new UserCategorySpend(userA, 1, BigDecimal.valueOf(1500)), new UserCategorySpend(userB, 1, BigDecimal.valueOf(1500))));
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(eq(userA), any(), anyInt(), any(), any()))
                .willThrow(new RuntimeException("unexpected"));
        Alert alertB =
                new Alert(UUID.randomUUID(), userB, AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH, Instant.now(), null, false, Map.of());
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(eq(userB), any(), anyInt(), any(), any())).willReturn(Optional.of(alertB));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(alertDispatchService).dispatch(alertB);
    }

    @Test
    void midMonthBudgetFiresAtFiftyPercentOfTotalBudgetOnOrAfterTheFifteenth() {
        UUID userId = UUID.randomUUID();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), currentMonth.getMonthValue(), currentMonth.getYear());
        stubBulkReads(List.of(budget), List.of(new UserCategorySpend(userId, 1, BigDecimal.valueOf(500))));
        boolean isOnOrAfterFifteenth = LocalDate.now().getDayOfMonth() >= 15;
        Alert alert = new Alert(UUID.randomUUID(), userId, AlertType.MID_MONTH_BUDGET, AlertPriority.HIGH, Instant.now(), null, false, Map.of());
        given(alertsService.recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.MID_MONTH_BUDGET), isNull(), eq(AlertPriority.HIGH), any()))
                .willReturn(Optional.of(alert));

        job.run();

        if (isOnOrAfterFifteenth) {
            verify(alertsService)
                    .recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.MID_MONTH_BUDGET), isNull(), eq(AlertPriority.HIGH), any());
        } else {
            verify(alertsService, never())
                    .recordIfNotAlreadyTriggeredThisMonth(eq(userId), eq(AlertType.MID_MONTH_BUDGET), any(), any(), any());
        }
    }

    private void stubBulkReads(List<Budget> budgets, List<UserCategorySpend> spend) {
        given(budgetService.findAllForMonth(currentMonth.getMonthValue(), currentMonth.getYear())).willReturn(budgets);
        given(transactionService.findAllSpendForMonth(currentMonth.getMonthValue(), currentMonth.getYear())).willReturn(spend);
    }

    @Test
    void qualifyingRecurringGroupRecordsAMediumPriorityAlertAndNeverDispatches() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(List.of(), List.of()); // no budgets — recurring pass runs independently
        given(transactionService.findAllForRecurringDetection(any())).willReturn(recurringGroupFixture(userId));
        given(emiService.findAllActiveSourceTransactionIds()).willReturn(Set.of());
        Alert alert = new Alert(
                UUID.randomUUID(), userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, Map.of());
        given(alertsService.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(eq(userId), eq("netflix@okicici"), any(), any()))
                .willReturn(Optional.of(alert));

        job.run();

        verify(alertsService)
                .recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(eq(userId), eq("netflix@okicici"), eq(BigDecimal.valueOf(199)), any());
        verify(alertDispatchService, never()).dispatch(any());
    }

    @Test
    void aTransactionAlreadyLinkedToAnActiveEmiIsExcludedFromRecurringDetection() {
        UUID userId = UUID.randomUUID();
        stubBulkReads(List.of(), List.of());
        List<RecurringCandidateTransaction> candidates = recurringGroupFixture(userId);
        given(transactionService.findAllForRecurringDetection(any())).willReturn(candidates);
        given(emiService.findAllActiveSourceTransactionIds()).willReturn(Set.of(candidates.get(0).transactionId()));

        job.run();

        verify(alertsService, never()).recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(any(), any(), any(), any());
    }

    @Test
    void recurringBulkLookupFailureDoesNotThrow() {
        stubBulkReads(List.of(), List.of());
        given(transactionService.findAllForRecurringDetection(any())).willThrow(new RuntimeException("spendwise_jobs connection lost"));

        assertThatCode(job::run).doesNotThrowAnyException();
    }

    private List<RecurringCandidateTransaction> recurringGroupFixture(UUID userId) {
        Instant now = Instant.now();
        BigDecimal amount = BigDecimal.valueOf(199);
        return List.of(
                new RecurringCandidateTransaction(userId, UUID.randomUUID(), now.minus(50, ChronoUnit.DAYS), amount, "netflix@okicici", "Netflix"),
                new RecurringCandidateTransaction(userId, UUID.randomUUID(), now.minus(25, ChronoUnit.DAYS), amount, "netflix@okicici", "Netflix"),
                new RecurringCandidateTransaction(userId, UUID.randomUUID(), now, amount, "netflix@okicici", "Netflix"));
    }
}
