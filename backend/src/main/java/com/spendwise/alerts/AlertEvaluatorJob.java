package com.spendwise.alerts;

import com.spendwise.budget.Budget;
import com.spendwise.budget.BudgetService;
import com.spendwise.transaction.EmiService;
import com.spendwise.transaction.RecurringCandidateTransaction;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UserCategorySpend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * E5-S2-T4 — runs the three threshold rules across every user on a schedule, per
 * docs/architecture.md's Background Jobs table ("Alert evaluator — every 30 minutes"). Scheduled
 * rather than event-driven per the Epic 5 handoff decision: the mid-month rule is inherently
 * calendar-bound (it must fire on the 15th even if the user made no transaction that day), the
 * module dependency table gives Alerts no path to be called from Ingest/Transaction without a
 * cycle, and docs/requirements.md's 1-hour alert SLA doesn't need event-driven latency. See
 * implementation/tracking/STATUS.md's Epic 5 close-out for the deferred event-driven optimization
 * this decision explicitly leaves on the table.
 *
 * <p>Cross-user by nature — {@link BudgetService#findAllForMonth} and {@link
 * TransactionService#findAllSpendForMonth} both read via the {@code spendwise_jobs} role (see
 * {@code com.spendwise.common.db.JobsDataSourceConfig}), mirroring {@code CategorizationRetryJob}.
 * Persisting a resulting alert (via {@link AlertsService}) and dispatching it (via {@link
 * AlertDispatchService}) both then re-scope to one user at a time through the normal RLS-enforced
 * path, exactly like {@code CategorizationService#categorize} does for the retry job.
 *
 * <p>Also runs {@link RecurringPaymentDetector} (E6-S2-T1) on the same 30-minute cadence, per the
 * epic's own text ("reusing the existing 30-minute schedule is the simplest option, since
 * recurring detection is not time-critical") — the same reasoning `docs/decisions.md` ADR-011
 * already gives for keeping the threshold rules scheduled rather than event-driven. It's a
 * separate pass over a separate cross-user bulk read ({@link
 * TransactionService#findAllForRecurringDetection}, {@link EmiService#findAllActiveSourceTransactionIds})
 * rather than reusing the budget-evaluation loop above, since it iterates a different user set
 * (any user with recent transactions, not just those with a budget).
 */
@Component
public class AlertEvaluatorJob {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluatorJob.class);
    private static final long RECURRING_LOOKBACK_DAYS = 60;

    private final BudgetService budgetService;
    private final TransactionService transactionService;
    private final EmiService emiService;
    private final AlertsService alertsService;
    private final AlertDispatchService alertDispatchService;

    public AlertEvaluatorJob(
            BudgetService budgetService,
            TransactionService transactionService,
            EmiService emiService,
            AlertsService alertsService,
            AlertDispatchService alertDispatchService) {
        this.budgetService = budgetService;
        this.transactionService = transactionService;
        this.emiService = emiService;
        this.alertsService = alertsService;
        this.alertDispatchService = alertDispatchService;
    }

    // initialDelay so this doesn't fire the instant the app starts — same reasoning as
    // CategorizationRetryJob.
    @Scheduled(initialDelay = 30, fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void evaluateAll() {
        run();
    }

    /** Package-visible so tests can invoke it directly rather than waiting on the real schedule. */
    void run() {
        YearMonth currentMonth = YearMonth.now();
        int dayOfMonth = LocalDate.now().getDayOfMonth();

        List<Budget> allBudgets;
        List<UserCategorySpend> allSpend;
        try {
            allBudgets = budgetService.findAllForMonth(currentMonth.getMonthValue(), currentMonth.getYear());
            allSpend = transactionService.findAllSpendForMonth(currentMonth.getMonthValue(), currentMonth.getYear());
        } catch (RuntimeException e) {
            // The next scheduled run retries — a transient failure here (e.g. spendwise_jobs
            // connection issue) must not crash the scheduler thread.
            log.warn("Alert evaluator's bulk lookup failed: {}", e.getMessage());
            return;
        }

        Map<UUID, List<Budget>> budgetsByUser = allBudgets.stream().collect(Collectors.groupingBy(Budget::userId));
        Map<UUID, Map<Integer, BigDecimal>> spendByUser = allSpend.stream()
                .collect(Collectors.groupingBy(
                        UserCategorySpend::userId, Collectors.toMap(UserCategorySpend::categoryId, UserCategorySpend::totalSpent)));

        // A user with no budgets set has nothing to evaluate against — only users with at least
        // one budget row can possibly trigger any of the three rules.
        for (Map.Entry<UUID, List<Budget>> entry : budgetsByUser.entrySet()) {
            UUID userId = entry.getKey();
            try {
                evaluateUser(userId, entry.getValue(), spendByUser.getOrDefault(userId, Map.of()), dayOfMonth);
            } catch (RuntimeException e) {
                // One user's failure (e.g. a dispatch error that somehow escaped
                // AlertDispatchService's own no-throw contract) must not stop the rest.
                log.warn("Alert evaluation failed for user {}: {}", userId, e.getMessage());
            }
        }

        evaluateRecurringPayments();
    }

    /**
     * Separate cross-user pass (E6-S2-T1) — iterates every user with a debit transaction in the
     * lookback window, not just users with a budget, so it runs independently of the loop above.
     */
    private void evaluateRecurringPayments() {
        List<RecurringCandidateTransaction> candidates;
        Set<UUID> excludedTransactionIds;
        try {
            Instant since = Instant.now().minus(RECURRING_LOOKBACK_DAYS, ChronoUnit.DAYS);
            candidates = transactionService.findAllForRecurringDetection(since);
            excludedTransactionIds = emiService.findAllActiveSourceTransactionIds();
        } catch (RuntimeException e) {
            // Same contract as the bulk lookup above — a transient failure here must not crash
            // the scheduler thread; the next scheduled run retries.
            log.warn("Recurring-payment evaluator's bulk lookup failed: {}", e.getMessage());
            return;
        }

        Map<UUID, List<RecurringCandidateTransaction>> candidatesByUser =
                candidates.stream().collect(Collectors.groupingBy(RecurringCandidateTransaction::userId));

        for (Map.Entry<UUID, List<RecurringCandidateTransaction>> entry : candidatesByUser.entrySet()) {
            UUID userId = entry.getKey();
            try {
                for (RecurringGroup group : RecurringPaymentDetector.detect(entry.getValue(), excludedTransactionIds)) {
                    Map<String, Object> payload = Map.of(
                            "merchant_key", group.merchantKey(),
                            "merchant_label", group.merchantLabel(),
                            "representative_amount", group.representativeAmount(),
                            "representative_transaction_id", group.representativeTransactionId().toString(),
                            "transaction_count", group.transactionIds().size());
                    // Always MEDIUM priority (in-app only) — never dispatched via
                    // AlertDispatchService, mirroring the approaching-limit rule above.
                    alertsService.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(
                            userId, group.merchantKey(), group.representativeAmount(), payload);
                }
            } catch (RuntimeException e) {
                log.warn("Recurring-payment evaluation failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    private void evaluateUser(UUID userId, List<Budget> budgets, Map<Integer, BigDecimal> spendByCategory, int dayOfMonth) {
        BigDecimal totalBudget = budgets.stream().map(Budget::monthlyLimit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = spendByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (MidMonthBudgetRule.triggers(totalSpent, totalBudget, dayOfMonth)) {
            recordAndDispatch(
                    userId,
                    AlertType.MID_MONTH_BUDGET,
                    null,
                    AlertPriority.HIGH,
                    Map.of("total_spent", totalSpent, "total_budget", totalBudget));
        }

        for (Budget budget : budgets) {
            BigDecimal spent = spendByCategory.getOrDefault(budget.categoryId(), BigDecimal.ZERO);
            Map<String, Object> payload =
                    Map.of("category_id", budget.categoryId(), "amount_spent", spent, "monthly_limit", budget.monthlyLimit());
            // Overspend takes precedence once >=100% — a category never fires both rules for the
            // same state in the same run (E5-S2-T2 DoD boundary rule).
            if (CategoryOverspendRule.triggers(spent, budget.monthlyLimit())) {
                recordAndDispatch(userId, AlertType.CATEGORY_OVERSPEND, budget.categoryId(), AlertPriority.HIGH, payload);
            } else if (CategoryApproachingLimitRule.triggers(spent, budget.monthlyLimit())) {
                recordAndDispatch(userId, AlertType.CATEGORY_APPROACHING_LIMIT, budget.categoryId(), AlertPriority.MEDIUM, payload);
            }
        }
    }

    private void recordAndDispatch(
            UUID userId, AlertType type, Integer categoryId, AlertPriority priority, Map<String, Object> payload) {
        alertsService
                .recordIfNotAlreadyTriggeredThisMonth(userId, type, categoryId, priority, payload)
                .filter(alert -> alert.priority() == AlertPriority.HIGH)
                .ifPresent(alertDispatchService::dispatch);
    }
}
