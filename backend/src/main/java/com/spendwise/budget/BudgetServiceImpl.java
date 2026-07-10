package com.spendwise.budget;

import com.spendwise.common.demo.DemoUserRegistry;
import com.spendwise.transaction.Category;
import com.spendwise.transaction.CategoryService;
import com.spendwise.transaction.MonthlyCategorySpend;
import com.spendwise.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BudgetServiceImpl implements BudgetService {

    // Not specified in docs — how many trailing calendar months /budgets/suggestions averages
    // over. 6 months, per explicit product-owner request during the Planning page redesign
    // (was 3 originally; Epic 5's rationale for 3 no longer applies — the owner wants a longer
    // window to smooth seasonal categories like Travel).
    private static final int SUGGESTION_HISTORY_MONTHS = 6;

    private final BudgetRepository budgetRepository;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final DemoUserRegistry demoUserRegistry;

    // The demo account's transaction data is a static CSV that is never re-uploaded (see
    // DemoDataSeeder), so "the real current month" drifts away from it over time and every
    // budget would show zero spend. Pinning the demo user's notion of "now" to the CSV's last
    // populated month keeps its budget progress permanently non-blank. Unset (blank) for every
    // real user — see DemoUserRegistry for why this lives here instead of a User/Ingest
    // cross-module call.
    private final YearMonth demoFrozenMonth;

    public BudgetServiceImpl(
            BudgetRepository budgetRepository,
            TransactionService transactionService,
            CategoryService categoryService,
            DemoUserRegistry demoUserRegistry,
            @Value("${demo.frozen-month:}") String demoFrozenMonth) {
        this.budgetRepository = budgetRepository;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.demoUserRegistry = demoUserRegistry;
        this.demoFrozenMonth = demoFrozenMonth.isBlank() ? null : YearMonth.parse(demoFrozenMonth);
    }

    private YearMonth resolveMonth(UUID userId) {
        if (demoFrozenMonth != null && demoUserRegistry.isDemoUser(userId)) {
            return demoFrozenMonth;
        }
        return YearMonth.now();
    }

    @Override
    @Transactional
    public Budget upsert(UUID userId, int categoryId, BigDecimal monthlyLimit) {
        boolean categoryExists = categoryService.listAll().stream().anyMatch(category -> category.id() == categoryId);
        if (!categoryExists) {
            throw new InvalidCategoryException(categoryId);
        }
        YearMonth now = resolveMonth(userId);
        return budgetRepository.upsert(userId, categoryId, monthlyLimit, now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional
    public List<Budget> listForCurrentMonth(UUID userId) {
        YearMonth now = resolveMonth(userId);
        return budgetRepository.findForMonth(userId, now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional
    public List<BudgetProgress> progressForCurrentMonth(UUID userId) {
        YearMonth now = resolveMonth(userId);
        List<Budget> budgets = budgetRepository.findForMonth(userId, now.getMonthValue(), now.getYear());
        Map<Integer, BigDecimal> spendByCategory =
                transactionService.sumSpendByCategoryForMonth(userId, now.getMonthValue(), now.getYear());
        return budgets.stream()
                .map(budget -> {
                    BigDecimal spent = spendByCategory.getOrDefault(budget.categoryId(), BigDecimal.ZERO);
                    BigDecimal percentSpent = percentOf(spent, budget.monthlyLimit());
                    return new BudgetProgress(budget.categoryId(), budget.monthlyLimit(), spent, percentSpent);
                })
                .toList();
    }

    @Override
    @Transactional
    public List<BudgetSuggestion> suggestions(UUID userId) {
        List<MonthlyCategorySpend> history = transactionService.historicalMonthlySpend(userId, SUGGESTION_HISTORY_MONTHS);
        Map<Integer, List<BigDecimal>> spendByCategory =
                history.stream().collect(Collectors.groupingBy(MonthlyCategorySpend::categoryId,
                        Collectors.mapping(MonthlyCategorySpend::totalSpent, Collectors.toList())));

        return categoryService.listAll().stream()
                .map(category -> toSuggestion(category, spendByCategory.get(category.id())))
                .toList();
    }

    @Override
    public List<Budget> findAllForMonth(int month, int year) {
        // No @Transactional — reads via the spendwise_jobs DataSource (BYPASSRLS), same reasoning
        // as TransactionServiceImpl.findAllSpendForMonth.
        return budgetRepository.findAllForMonth(month, year);
    }

    private static BudgetSuggestion toSuggestion(Category category, List<BigDecimal> monthlySpendAmounts) {
        if (monthlySpendAmounts == null || monthlySpendAmounts.isEmpty()) {
            return new BudgetSuggestion(category.id(), null, false);
        }
        BigDecimal total = monthlySpendAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(monthlySpendAmounts.size()), 2, RoundingMode.HALF_UP);
        return new BudgetSuggestion(category.id(), average, true);
    }

    private static BigDecimal percentOf(BigDecimal spent, BigDecimal monthlyLimit) {
        // chk_budget_limit_positive guarantees monthlyLimit > 0 at the DB level, so no
        // divide-by-zero guard is needed here.
        return spent.divide(monthlyLimit, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
