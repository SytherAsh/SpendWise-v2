package com.spendwise.alerts;

import com.spendwise.transaction.RecurringCandidateTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * E6-S1-T1 — pure logic, no Spring/DB dependency, so it's unit-testable against synthetic
 * fixtures independent of Epic 5's alert pipeline (`docs/testing.md` Alerts unit tests —
 * recurring-payment detection). Operates on one user's candidate transactions at a time; {@link
 * AlertEvaluatorJob} groups the cross-user bulk read by {@code userId} before calling this.
 *
 * <p><b>Grouping key:</b> {@code upi_id} when non-blank, else {@code recipient_name}
 * (`docs/requirements.md` "Recurring payment detection rule"). Transactions with both blank
 * cannot be grouped and are ignored.
 *
 * <p><b>Amount tolerance ("within ±10% of each other"):</b> anchored to the group's smallest
 * amount, not pairwise-chained — a cluster's members must all satisfy {@code amount <= clusterMin
 * * 1.10}. Pairwise chaining (100 → 110 → 121) would let the effective band drift past 10% across
 * a long chain, which the requirement doesn't intend.
 *
 * <p><b>Rolling 60-day window:</b> for each amount cluster (sorted by date), every possible
 * window start is tried; a window qualifies if it contains 3+ non-excluded transactions within 60
 * days of its earliest member. The first qualifying window per cluster is reported.
 *
 * <p><b>{@code emis} exclusion — deliberately the most conservative rule available:</b> a
 * transaction is excluded only if its id is an active {@code emis} row's {@code
 * source_transaction_id} (exact match). No label/amount correlation is attempted for
 * manually-entered EMIs (which have no {@code source_transaction_id} to match against) — a fuzzy
 * match risks a false-positive exclusion that silently hides a legitimate alert from the user,
 * which is worse than an occasional redundant alert for a charge they already track manually.
 * When confidence is insufficient to link a group to an existing EMI with certainty, this
 * detector does not attempt to classify it as already-tracked.
 */
public final class RecurringPaymentDetector {

    private static final BigDecimal TOLERANCE_MULTIPLIER = BigDecimal.valueOf(110, 2); // 1.10
    private static final long WINDOW_DAYS = 60;
    private static final int MIN_GROUP_SIZE = 3;

    private RecurringPaymentDetector() {}

    public static List<RecurringGroup> detect(List<RecurringCandidateTransaction> candidates, Set<UUID> excludedTransactionIds) {
        Map<String, List<RecurringCandidateTransaction>> byMerchant = new LinkedHashMap<>();
        for (RecurringCandidateTransaction candidate : candidates) {
            String key = merchantKey(candidate);
            if (key == null) {
                continue;
            }
            byMerchant.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
        }

        List<RecurringGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<RecurringCandidateTransaction>> entry : byMerchant.entrySet()) {
            if (entry.getValue().size() < MIN_GROUP_SIZE) {
                continue;
            }
            for (List<RecurringCandidateTransaction> cluster : anchoredAmountClusters(entry.getValue())) {
                qualifyingWindow(cluster, excludedTransactionIds).ifPresent(groups::add);
            }
        }
        return groups;
    }

    private static String merchantKey(RecurringCandidateTransaction candidate) {
        if (candidate.upiId() != null && !candidate.upiId().isBlank()) {
            return candidate.upiId();
        }
        if (candidate.recipientName() != null && !candidate.recipientName().isBlank()) {
            return candidate.recipientName();
        }
        return null;
    }

    private static String merchantLabel(RecurringCandidateTransaction candidate) {
        return candidate.recipientName() != null && !candidate.recipientName().isBlank()
                ? candidate.recipientName()
                : candidate.upiId();
    }

    /** Sequential clustering on amount-ascending order — each cluster anchored to its own minimum. */
    private static List<List<RecurringCandidateTransaction>> anchoredAmountClusters(
            List<RecurringCandidateTransaction> merchantTransactions) {
        List<RecurringCandidateTransaction> sortedByAmount = new ArrayList<>(merchantTransactions);
        sortedByAmount.sort(Comparator.comparing(RecurringCandidateTransaction::amount));

        List<List<RecurringCandidateTransaction>> clusters = new ArrayList<>();
        List<RecurringCandidateTransaction> current = new ArrayList<>();
        BigDecimal clusterMin = null;
        BigDecimal clusterCeiling = null;
        for (RecurringCandidateTransaction txn : sortedByAmount) {
            if (clusterMin == null || txn.amount().compareTo(clusterCeiling) > 0) {
                current = new ArrayList<>();
                clusters.add(current);
                clusterMin = txn.amount();
                clusterCeiling = clusterMin.multiply(TOLERANCE_MULTIPLIER).setScale(clusterMin.scale() + 2, RoundingMode.HALF_UP);
            }
            current.add(txn);
        }
        return clusters;
    }

    /**
     * Finds the first date-sorted window (of the cluster, trying every possible start) that
     * contains 3+ non-excluded transactions within {@value #WINDOW_DAYS} days of its earliest
     * member.
     */
    private static Optional<RecurringGroup> qualifyingWindow(
            List<RecurringCandidateTransaction> cluster, Set<UUID> excludedTransactionIds) {
        List<RecurringCandidateTransaction> sortedByDate = new ArrayList<>(cluster);
        sortedByDate.sort(Comparator.comparing(RecurringCandidateTransaction::transactionDate));

        for (int i = 0; i < sortedByDate.size(); i++) {
            List<RecurringCandidateTransaction> window = new ArrayList<>();
            for (int j = i; j < sortedByDate.size(); j++) {
                long daysFromStart = ChronoUnit.DAYS.between(sortedByDate.get(i).transactionDate(), sortedByDate.get(j).transactionDate());
                if (daysFromStart > WINDOW_DAYS) {
                    break;
                }
                window.add(sortedByDate.get(j));
            }
            List<RecurringCandidateTransaction> nonExcluded = window.stream()
                    .filter(txn -> !excludedTransactionIds.contains(txn.transactionId()))
                    .toList();
            if (nonExcluded.size() >= MIN_GROUP_SIZE) {
                RecurringCandidateTransaction representative =
                        nonExcluded.stream().max(Comparator.comparing(RecurringCandidateTransaction::transactionDate)).orElseThrow();
                return Optional.of(new RecurringGroup(
                        merchantKey(representative),
                        merchantLabel(representative),
                        representative.amount(),
                        representative.transactionId(),
                        nonExcluded.stream().map(RecurringCandidateTransaction::transactionId).toList()));
            }
        }
        return Optional.empty();
    }
}
