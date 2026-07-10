package com.spendwise.budget;

import com.spendwise.common.demo.DemoUserRegistry;
import com.spendwise.transaction.Category;
import com.spendwise.transaction.CategoryService;
import com.spendwise.transaction.MonthlyCategorySpend;
import com.spendwise.transaction.TransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Required tests for E5-S1 (docs/testing.md Budget unit tests). */
class BudgetServiceImplTest {

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    // Real (unmocked) instance — a trivial value holder with no dependencies. Never registering
    // a demo user here means isDemoUser() always returns false, so resolveMonth() falls through
    // to YearMonth.now() for every test below, exactly as before this class existed.
    private final DemoUserRegistry demoUserRegistry = new DemoUserRegistry();
    private final BudgetServiceImpl service =
            new BudgetServiceImpl(budgetRepository, transactionService, categoryService, demoUserRegistry, "");
    private final UUID userId = UUID.randomUUID();

    @Test
    void upsertThrowsInvalidCategoryForUnknownCategoryId() {
        given(categoryService.listAll()).willReturn(List.of(new Category(1, "Food/Dine Out", "icon")));

        assertThrows(InvalidCategoryException.class, () -> service.upsert(userId, 999, BigDecimal.valueOf(2000)));
    }

    @Test
    void upsertTargetsTheCurrentCalendarMonth() {
        given(categoryService.listAll()).willReturn(List.of(new Category(1, "Food/Dine Out", "icon")));
        YearMonth now = YearMonth.now();
        given(budgetRepository.upsert(userId, 1, BigDecimal.valueOf(2000), now.getMonthValue(), now.getYear()))
                .willReturn(new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(2000), now.getMonthValue(), now.getYear()));

        Budget result = service.upsert(userId, 1, BigDecimal.valueOf(2000));

        assertThat(result.month()).isEqualTo(now.getMonthValue());
        assertThat(result.year()).isEqualTo(now.getYear());
    }

    @Test
    void repeatedUpsertCallsAreIdempotentAtTheRepositoryBoundary() {
        given(categoryService.listAll()).willReturn(List.of(new Category(1, "Food/Dine Out", "icon")));
        YearMonth now = YearMonth.now();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(2000), now.getMonthValue(), now.getYear());
        given(budgetRepository.upsert(userId, 1, BigDecimal.valueOf(2000), now.getMonthValue(), now.getYear())).willReturn(budget);

        Budget first = service.upsert(userId, 1, BigDecimal.valueOf(2000));
        Budget second = service.upsert(userId, 1, BigDecimal.valueOf(2000));

        assertThat(first).isEqualTo(second);
        verify(budgetRepository, org.mockito.Mockito.times(2)).upsert(userId, 1, BigDecimal.valueOf(2000), now.getMonthValue(), now.getYear());
    }

    @Test
    void progressCalculationMatchesHandComputedValue() {
        YearMonth now = YearMonth.now();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(2000), now.getMonthValue(), now.getYear());
        given(budgetRepository.findForMonth(userId, now.getMonthValue(), now.getYear())).willReturn(List.of(budget));
        given(transactionService.sumSpendByCategoryForMonth(userId, now.getMonthValue(), now.getYear()))
                .willReturn(Map.of(1, BigDecimal.valueOf(500)));

        List<BudgetProgress> progress = service.progressForCurrentMonth(userId);

        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).spent()).isEqualByComparingTo("500");
        // 500 / 2000 * 100 = 25.00
        assertThat(progress.get(0).percentSpent()).isEqualByComparingTo("25.00");
    }

    @Test
    void progressTreatsACategoryWithNoSpendThisMonthAsZero() {
        YearMonth now = YearMonth.now();
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), now.getMonthValue(), now.getYear());
        given(budgetRepository.findForMonth(userId, now.getMonthValue(), now.getYear())).willReturn(List.of(budget));
        given(transactionService.sumSpendByCategoryForMonth(userId, now.getMonthValue(), now.getYear())).willReturn(Map.of());

        List<BudgetProgress> progress = service.progressForCurrentMonth(userId);

        assertThat(progress.get(0).spent()).isEqualByComparingTo("0");
        assertThat(progress.get(0).percentSpent()).isEqualByComparingTo("0.00");
    }

    @Test
    void suggestionsReturnAverageWhenTwoOrMoreMonthsOfHistoryExist() {
        given(categoryService.listAll()).willReturn(List.of(new Category(1, "Food/Dine Out", "icon")));
        given(transactionService.historicalMonthlySpend(any(), anyInt())).willReturn(List.of(
                new MonthlyCategorySpend(1, 5, 2026, BigDecimal.valueOf(1000)),
                new MonthlyCategorySpend(1, 6, 2026, BigDecimal.valueOf(2000))));

        List<BudgetSuggestion> suggestions = service.suggestions(userId);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).available()).isTrue();
        assertThat(suggestions.get(0).suggestedMonthlyLimit()).isEqualByComparingTo("1500.00");
    }

    @Test
    void suggestionsAreGracefullyUnavailableWhenNoHistoryExists() {
        given(categoryService.listAll()).willReturn(List.of(new Category(1, "Food/Dine Out", "icon")));
        given(transactionService.historicalMonthlySpend(any(), anyInt())).willReturn(List.of());

        List<BudgetSuggestion> suggestions = service.suggestions(userId);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).available()).isFalse();
        assertThat(suggestions.get(0).suggestedMonthlyLimit()).isNull();
    }

    @Test
    void findAllForMonthDelegatesToRepositoryForTheAlertsEvaluator() {
        Budget budget = new Budget(UUID.randomUUID(), userId, 1, BigDecimal.valueOf(1000), 7, 2026);
        given(budgetRepository.findAllForMonth(7, 2026)).willReturn(List.of(budget));

        List<Budget> result = service.findAllForMonth(7, 2026);

        assertThat(result).containsExactly(budget);
    }
}
