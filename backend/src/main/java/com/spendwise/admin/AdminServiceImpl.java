package com.spendwise.admin;

import com.spendwise.alerts.AlertPage;
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
import com.spendwise.transaction.TransactionPage;
import com.spendwise.transaction.TransactionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminServiceImpl implements AdminService {

    /**
     * Not paginated (unlike the user-facing {@code /transactions}/{@code /alerts} endpoints) — a
     * generous cap on the detail view's "full data for one user", per the epic's DoD wording. Not
     * spec'd as a number; a reasonable default.
     */
    private static final int DETAIL_VIEW_LIMIT = 200;

    private final AdminRepository adminRepository;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final AlertsService alertsService;
    private final AnalyticsService analyticsService;
    private final CategorizationService categorizationService;

    public AdminServiceImpl(
            AdminRepository adminRepository,
            TransactionService transactionService,
            BudgetService budgetService,
            AlertsService alertsService,
            AnalyticsService analyticsService,
            CategorizationService categorizationService) {
        this.adminRepository = adminRepository;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.alertsService = alertsService;
        this.analyticsService = analyticsService;
        this.categorizationService = categorizationService;
    }

    @Override
    public List<AdminUserSummary> listUsers() {
        return adminRepository.findAllUsersWithStats();
    }

    @Override
    public AdminUserDetail getUserDetail(UUID userId) {
        AdminUserCore core = adminRepository.findUserCoreById(userId).orElseThrow(AdminUserNotFoundException::new);
        TransactionPage transactions = transactionService.list(userId, DETAIL_VIEW_LIMIT, null, null, false, null, null, null);
        List<Budget> budgets = budgetService.listForCurrentMonth(userId);
        AlertPage alerts = alertsService.list(userId, DETAIL_VIEW_LIMIT, null, null);
        return new AdminUserDetail(
                core.id(), core.phone(), core.email(), core.createdAt(), transactions.data(), budgets, alerts.data());
    }

    @Override
    public AnalyticsSummary getAggregateAnalytics(Instant from, Instant to) {
        List<UUID> userIds = adminRepository.findAllUserIds();
        List<AnalyticsSummary> perUser = userIds.stream().map(id -> analyticsService.summary(id, from, to)).toList();
        OverallTotals overall = sumOverallTotals(perUser.stream().map(AnalyticsSummary::overall).toList());
        List<CategoryTotal> categories = mergeCategoryTotals(perUser.stream().map(AnalyticsSummary::categories).toList());
        return new AnalyticsSummary(overall, categories);
    }

    @Override
    public AnalyticsComparison getAggregateComparison(String granularity) {
        List<UUID> userIds = adminRepository.findAllUserIds();
        if (userIds.isEmpty()) {
            OverallTotals zero = new OverallTotals(BigDecimal.ZERO, BigDecimal.ZERO);
            ComparisonPeriod empty = new ComparisonPeriod(null, null, zero, List.of());
            return new AnalyticsComparison(granularity == null || granularity.isBlank() ? "month" : granularity, empty, empty);
        }
        List<AnalyticsComparison> perUser = userIds.stream().map(id -> analyticsService.comparison(id, granularity)).toList();
        String normalizedGranularity = perUser.get(0).granularity();
        ComparisonPeriod current = mergePeriods(perUser.stream().map(AnalyticsComparison::current).toList());
        ComparisonPeriod previous = mergePeriods(perUser.stream().map(AnalyticsComparison::previous).toList());
        return new AnalyticsComparison(normalizedGranularity, current, previous);
    }

    @Override
    public List<AdminLogEntry> getLogs(String eventType) {
        return adminRepository.findLogs(eventType);
    }

    @Override
    public MlEvaluationResponse getMlAccuracy() {
        return categorizationService.getAccuracyMetrics();
    }

    @Override
    public void triggerRetrain() {
        categorizationService.triggerRetrain();
    }

    @Override
    public void deleteUser(UUID userId) {
        AdminUserCore core = adminRepository.findUserCoreById(userId).orElseThrow(AdminUserNotFoundException::new);
        List<UUID> logIdsToScrub = adminRepository.findAdminLogIdsForUser(userId);
        List<String> identifyingStrings = new ArrayList<>();
        identifyingStrings.add(core.phone());
        identifyingStrings.add(core.email());
        identifyingStrings.add(core.googleId());

        int deleted = adminRepository.deleteUserCascade(userId);
        if (deleted == 0) {
            // Race: the user existed at the findUserCoreById check above but is already gone —
            // extremely unlikely for a single-admin-operator portal, but handled rather than
            // silently no-op-ing the scrub step below.
            throw new AdminUserNotFoundException();
        }
        adminRepository.scrubAdminLogs(logIdsToScrub, identifyingStrings);
    }

    private static OverallTotals sumOverallTotals(List<OverallTotals> totals) {
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        for (OverallTotals total : totals) {
            spend = spend.add(total.totalSpend());
            income = income.add(total.totalIncome());
        }
        return new OverallTotals(spend, income);
    }

    private static List<CategoryTotal> mergeCategoryTotals(List<List<CategoryTotal>> perUserCategories) {
        Map<Integer, CategoryTotal> merged = new LinkedHashMap<>();
        for (List<CategoryTotal> categories : perUserCategories) {
            for (CategoryTotal categoryTotal : categories) {
                merged.merge(
                        categoryTotal.categoryId(),
                        categoryTotal,
                        (a, b) -> new CategoryTotal(
                                a.categoryId(),
                                a.categoryName(),
                                a.totalSpend().add(b.totalSpend()),
                                a.totalIncome().add(b.totalIncome()),
                                a.transactionCount() + b.transactionCount()));
            }
        }
        return List.copyOf(merged.values());
    }

    private static ComparisonPeriod mergePeriods(List<ComparisonPeriod> periods) {
        OverallTotals overall = sumOverallTotals(periods.stream().map(ComparisonPeriod::overall).toList());
        List<CategoryTotal> categories = mergeCategoryTotals(periods.stream().map(ComparisonPeriod::categories).toList());
        return new ComparisonPeriod(periods.get(0).from(), periods.get(0).to(), overall, categories);
    }
}
