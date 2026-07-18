package com.spendwise.alerts;

import com.spendwise.transaction.RecurringCandidateTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Candidate generation for recurring-payment detection — pure logic, no Spring/DB dependency, so
 * it's unit-testable against synthetic fixtures independent of Epic 5's alert pipeline
 * (`docs/operations/testing.md` Alerts unit tests). Operates on one user's candidate transactions
 * at a time; {@link AlertEvaluatorJob} groups the cross-user bulk read by {@code userId} before
 * calling this.
 *
 * <p><b>Loosened for the ML strategy phase (2026-07-11):</b> this was E6-S1-T1's exact detection
 * rule (3+ transactions, ±10% amount tolerance, 60-day window) — that rule is now the bootstrap
 * label definition {@code ml/training/recurring_labels.py} uses to train the classifier, not the
 * production gate. Widened here to 2+ transactions, ±40% amount tolerance, a 400-day window
 * (mirroring {@code ml/training/recurring_features.py}'s {@code LOOSE_*} constants exactly, so the
 * Python-trained model's input distribution matches what this class actually produces), so a
 * genuinely-recurring-but-irregular pattern — a quarterly premium, a bill that drifts by more than
 * 10% — becomes a candidate the ML model ({@link
 * com.spendwise.categorization.CategorizationService#predictRecurring}) can judge, instead of
 * being silently rejected before any model ever sees it. This class only proposes candidates and
 * their statistics now; {@link AlertEvaluatorJob} owns the actual recurring/not-recurring decision.
 *
 * <p><b>Grouping key:</b> {@code upi_id} when non-blank, else {@code recipient_canonical} when the
 * canonicalization job has assigned one (ML strategy phase, 2026-07-13), else the raw {@code
 * recipient_name} (`docs/spec/requirements.md` "Recurring payment detection rule"). Preferring the
 * canonical name over the raw one collapses spelling variants of a single payee ("SWIGGY" vs
 * "Swiggy Bangalore") into one group, so a genuinely recurring charge that was splintered across
 * name variants — and thus fell below the occurrence threshold on each variant individually — is
 * now detected. Transactions with no usable key at all cannot be grouped and are ignored.
 *
 * <p><b>Amount tolerance:</b> anchored to the group's smallest amount, not pairwise-chained — a
 * cluster's members must all satisfy {@code amount <= clusterMin * 1.40}. Pairwise chaining would
 * let the effective band drift arbitrarily far across a long chain.
 *
 * <p><b>Rolling window:</b> for each amount cluster (sorted by date), every possible window start
 * is tried; a window qualifies if it contains 2+ non-excluded transactions within 400 days of its
 * earliest member. The first qualifying window per cluster is reported.
 *
 * <p><b>{@code emis} exclusion — deliberately the most conservative rule available:</b> a
 * transaction is excluded only if its id is an active {@code emis} row's {@code
 * source_transaction_id} (exact match). No label/amount correlation is attempted for
 * manually-entered EMIs (which have no {@code source_transaction_id} to match against) — a fuzzy
 * match risks a false-positive exclusion that silently hides a legitimate alert from the user,
 * which is worse than an occasional redundant alert for a charge they already track manually.
 */
public final class RecurringPaymentDetector {

    private static final BigDecimal TOLERANCE_MULTIPLIER = BigDecimal.valueOf(140, 2); // 1.40
    private static final long WINDOW_DAYS = 400;
    private static final int MIN_GROUP_SIZE = 2;

    private RecurringPaymentDetector() {}

    public static List<RecurringGroup> detect(List<RecurringCandidateTransaction> candidates, Set<UUID> excludedTransactionIds) {
        return detect(candidates, excludedTransactionIds, Instant.now());
    }

    /** Overload taking an explicit "now" so days_since_last_occurrence is deterministic in tests. */
    static List<RecurringGroup> detect(
            List<RecurringCandidateTransaction> candidates, Set<UUID> excludedTransactionIds, Instant asOf) {
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
                qualifyingWindow(cluster, excludedTransactionIds, asOf).ifPresent(groups::add);
            }
        }
        return groups;
    }

    private static String merchantKey(RecurringCandidateTransaction candidate) {
        if (candidate.upiId() != null && !candidate.upiId().isBlank()) {
            return candidate.upiId();
        }
        String name = canonicalOrRawName(candidate);
        return name != null && !name.isBlank() ? name : null;
    }

    private static String merchantLabel(RecurringCandidateTransaction candidate) {
        String name = canonicalOrRawName(candidate);
        return name != null && !name.isBlank() ? name : candidate.upiId();
    }

    /**
     * Canonical (deduplicated) recipient name when the canonicalization job has assigned one,
     * else the raw name — so grouping and the user-facing label both benefit from canonicalization
     * once it has run, and degrade gracefully to the raw name before it has.
     */
    private static String canonicalOrRawName(RecurringCandidateTransaction candidate) {
        if (candidate.recipientCanonical() != null && !candidate.recipientCanonical().isBlank()) {
            return candidate.recipientCanonical();
        }
        return candidate.recipientName();
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
     * contains 2+ non-excluded transactions within {@value #WINDOW_DAYS} days of its earliest
     * member.
     */
    private static Optional<RecurringGroup> qualifyingWindow(
            List<RecurringCandidateTransaction> cluster, Set<UUID> excludedTransactionIds, Instant asOf) {
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
                        nonExcluded.stream().map(RecurringCandidateTransaction::transactionId).toList(),
                        computeFeatures(nonExcluded, asOf)));
            }
        }
        return Optional.empty();
    }

    /**
     * Java mirror of {@code ml/training/recurring_features.py}'s {@code compute_features()} —
     * same statistics, same sample-standard-deviation-based coefficient of variation (Python's
     * {@code statistics.stdev}), so the classifier sees the same feature distribution regardless
     * of which side computed it.
     */
    static RecurringCandidateFeatures computeFeatures(List<RecurringCandidateTransaction> window, Instant asOf) {
        List<RecurringCandidateTransaction> sortedByDate = new ArrayList<>(window);
        sortedByDate.sort(Comparator.comparing(RecurringCandidateTransaction::transactionDate));

        double[] intervals = new double[sortedByDate.size() - 1];
        for (int i = 1; i < sortedByDate.size(); i++) {
            intervals[i - 1] = ChronoUnit.DAYS.between(sortedByDate.get(i - 1).transactionDate(), sortedByDate.get(i).transactionDate());
        }
        double intervalMean = intervals.length == 0 ? 0.0 : Arrays.stream(intervals).average().orElse(0.0);
        double intervalCv = coefficientOfVariation(intervals, intervalMean);

        double[] amounts = sortedByDate.stream().mapToDouble(t -> t.amount().abs().doubleValue()).toArray();
        double amountMean = Arrays.stream(amounts).average().orElse(0.0);
        double amountCv = coefficientOfVariation(amounts, amountMean);

        long spanDays =
                ChronoUnit.DAYS.between(sortedByDate.get(0).transactionDate(), sortedByDate.get(sortedByDate.size() - 1).transactionDate());
        long daysSinceLast = ChronoUnit.DAYS.between(sortedByDate.get(sortedByDate.size() - 1).transactionDate(), asOf);

        return new RecurringCandidateFeatures(
                sortedByDate.size(), intervalMean, intervalCv, amountMean, amountCv, (double) spanDays, (double) daysSinceLast);
    }

    /** Sample standard deviation (n-1 denominator, matching Python's statistics.stdev) over mean. */
    private static double coefficientOfVariation(double[] values, double mean) {
        if (values.length < 2 || mean == 0.0) {
            return 0.0;
        }
        double sumSquaredDiff = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum();
        double variance = sumSquaredDiff / (values.length - 1);
        return Math.sqrt(variance) / mean;
    }
}
