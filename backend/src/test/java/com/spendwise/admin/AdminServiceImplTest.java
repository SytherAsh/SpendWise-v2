package com.spendwise.admin;

import com.spendwise.alerts.Alert;
import com.spendwise.alerts.AlertPage;
import com.spendwise.alerts.AlertPriority;
import com.spendwise.alerts.AlertType;
import com.spendwise.alerts.AlertsService;
import com.spendwise.analytics.AnalyticsComparison;
import com.spendwise.analytics.AnalyticsService;
import com.spendwise.analytics.AnalyticsSummary;
import com.spendwise.analytics.CategoryTotal;
import com.spendwise.analytics.ComparisonPeriod;
import com.spendwise.analytics.OverallTotals;
import com.spendwise.budget.Budget;
import com.spendwise.budget.BudgetService;
import com.spendwise.categorization.CategorizationService;
import com.spendwise.categorization.dto.MlEvaluationResponse;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionPage;
import com.spendwise.transaction.TransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

/** Unit tests for AdminServiceImpl (Epic 11) — covers the aggregate-sum logic and erasure orchestration in isolation. */
class AdminServiceImplTest {

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final BudgetService budgetService = mock(BudgetService.class);
    private final AlertsService alertsService = mock(AlertsService.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final CategorizationService categorizationService = mock(CategorizationService.class);
    private final AdminServiceImpl service = new AdminServiceImpl(
            adminRepository, transactionService, budgetService, alertsService, analyticsService, categorizationService);

    @Test
    void listUsersDelegatesToRepository() {
        AdminUserSummary summary = new AdminUserSummary(UUID.randomUUID(), "+911111111111", null, Instant.now(), 3L, Instant.now());
        given(adminRepository.findAllUsersWithStats()).willReturn(List.of(summary));

        assertThat(service.listUsers()).containsExactly(summary);
    }

    @Test
    void getUserDetailThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        given(adminRepository.findUserCoreById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserDetail(userId)).isInstanceOf(AdminUserNotFoundException.class);
    }

    @Test
    void getUserDetailComposesTransactionBudgetAndAlertDataForTheTargetUser() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        given(adminRepository.findUserCoreById(userId))
                .willReturn(Optional.of(new AdminUserCore(userId, "+911111111111", "a@b.com", null, createdAt)));
        Transaction transaction = new Transaction(
                UUID.randomUUID(), userId, Instant.now(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN.negate(), BigDecimal.ZERO,
                "UPI", "DR", "txn-1", "Swiggy", "swiggy@okhdfc", null, null,
                com.spendwise.transaction.TransactionSource.SMS, Instant.now(), null, null, null);
        given(transactionService.list(userId, 200, null, null, null, null)).willReturn(new TransactionPage(List.of(transaction), null, false));
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(2000), 7, 2026);
        given(budgetService.listForCurrentMonth(userId)).willReturn(List.of(budget));
        Alert alert = new Alert(UUID.randomUUID(), userId, AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH, Instant.now(), null, false, Map.of());
        given(alertsService.list(userId, 200, null, null)).willReturn(new AlertPage(List.of(alert), null, false));

        AdminUserDetail detail = service.getUserDetail(userId);

        assertThat(detail.id()).isEqualTo(userId);
        assertThat(detail.transactions()).containsExactly(transaction);
        assertThat(detail.budgets()).containsExactly(budget);
        assertThat(detail.alerts()).containsExactly(alert);
    }

    @Test
    void aggregateAnalyticsSumsEveryUsersSummary() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-31T23:59:59Z");
        given(adminRepository.findAllUserIds()).willReturn(List.of(userA, userB));
        given(analyticsService.summary(userA, from, to)).willReturn(new AnalyticsSummary(
                new OverallTotals(BigDecimal.valueOf(100), BigDecimal.valueOf(10)),
                List.of(new CategoryTotal(1, "Food/Dine Out", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2))));
        given(analyticsService.summary(userB, from, to)).willReturn(new AnalyticsSummary(
                new OverallTotals(BigDecimal.valueOf(50), BigDecimal.valueOf(5)),
                List.of(new CategoryTotal(1, "Food/Dine Out", BigDecimal.valueOf(50), BigDecimal.valueOf(5), 1))));

        AnalyticsSummary aggregate = service.getAggregateAnalytics(from, to);

        assertThat(aggregate.overall().totalSpend()).isEqualByComparingTo("150");
        assertThat(aggregate.overall().totalIncome()).isEqualByComparingTo("15");
        assertThat(aggregate.categories()).hasSize(1);
        assertThat(aggregate.categories().get(0).totalSpend()).isEqualByComparingTo("150");
        assertThat(aggregate.categories().get(0).transactionCount()).isEqualTo(3);
    }

    @Test
    void aggregateComparisonSumsEveryUsersComparison() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        given(adminRepository.findAllUserIds()).willReturn(List.of(userA, userB));
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-31T23:59:59Z");
        ComparisonPeriod currentA = new ComparisonPeriod(from, to, new OverallTotals(BigDecimal.valueOf(100), BigDecimal.ZERO), List.of());
        ComparisonPeriod previousA = new ComparisonPeriod(from, to, new OverallTotals(BigDecimal.valueOf(80), BigDecimal.ZERO), List.of());
        ComparisonPeriod currentB = new ComparisonPeriod(from, to, new OverallTotals(BigDecimal.valueOf(20), BigDecimal.ZERO), List.of());
        ComparisonPeriod previousB = new ComparisonPeriod(from, to, new OverallTotals(BigDecimal.valueOf(10), BigDecimal.ZERO), List.of());
        given(analyticsService.comparison(userA, "month")).willReturn(new AnalyticsComparison("month", currentA, previousA));
        given(analyticsService.comparison(userB, "month")).willReturn(new AnalyticsComparison("month", currentB, previousB));

        AnalyticsComparison aggregate = service.getAggregateComparison("month");

        assertThat(aggregate.current().overall().totalSpend()).isEqualByComparingTo("120");
        assertThat(aggregate.previous().overall().totalSpend()).isEqualByComparingTo("90");
    }

    @Test
    void aggregateComparisonIsGracefullyEmptyWithZeroUsers() {
        given(adminRepository.findAllUserIds()).willReturn(List.of());

        AnalyticsComparison aggregate = service.getAggregateComparison("week");

        assertThat(aggregate.granularity()).isEqualTo("week");
        assertThat(aggregate.current().overall().totalSpend()).isEqualByComparingTo("0");
    }

    @Test
    void getLogsDelegatesWithEventTypeFilter() {
        AdminLogEntry entry = new AdminLogEntry(UUID.randomUUID(), "parse_failure", null, Map.of(), Instant.now());
        given(adminRepository.findLogs("parse_failure")).willReturn(List.of(entry));

        assertThat(service.getLogs("parse_failure")).containsExactly(entry);
    }

    @Test
    void mlAccuracyAndRetrainDelegateToCategorizationService() {
        MlEvaluationResponse response = new MlEvaluationResponse("2026-07-04T00:00:00Z", 100, 0.9, List.of(), List.of(), List.of(), null, "path");
        given(categorizationService.getAccuracyMetrics()).willReturn(response);

        assertThat(service.getMlAccuracy()).isEqualTo(response);
        service.triggerRetrain();
        verify(categorizationService).triggerRetrain();
    }

    @Test
    void deleteUserThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        given(adminRepository.findUserCoreById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(userId)).isInstanceOf(AdminUserNotFoundException.class);
        verify(adminRepository, never()).deleteUserCascade(any());
    }

    @Test
    void deleteUserCapturesIdentifyingStringsBeforeCascadingAndScrubsLogsAfter() {
        UUID userId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        given(adminRepository.findUserCoreById(userId))
                .willReturn(Optional.of(new AdminUserCore(userId, "+911111111111", "a@b.com", "google-123", Instant.now())));
        given(adminRepository.findAdminLogIdsForUser(userId)).willReturn(List.of(logId));
        given(adminRepository.deleteUserCascade(userId)).willReturn(1);

        service.deleteUser(userId);

        verify(adminRepository).deleteUserCascade(userId);
        verify(adminRepository).scrubAdminLogs(
                org.mockito.ArgumentMatchers.eq(List.of(logId)),
                org.mockito.ArgumentMatchers.argThat(strings -> strings.containsAll(List.of("+911111111111", "a@b.com", "google-123"))));
    }
}
