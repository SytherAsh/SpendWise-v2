package com.spendwise.alerts;

import com.spendwise.transaction.RecurringCandidateTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Required tests for E6-S1-T1 (docs/testing.md Alerts unit tests — recurring-payment detection). */
class RecurringPaymentDetectorTest {

    private final Instant now = Instant.now();

    @Test
    void threeMatchingTransactionsWithinSixtyDaysAndTolerance_isFlagged() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(50), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(25), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(0), "205.00", "netflix@okicici", "Netflix")); // within 10% of 199

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).merchantKey()).isEqualTo("netflix@okicici");
        assertThat(groups.get(0).transactionIds()).hasSize(3);
    }

    @Test
    void onlyTwoMatchingTransactions_isNotFlagged() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(50), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void threeMatchingTransactionsSpanningMoreThanSixtyDays_isNotFlagged() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(70), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(35), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void groupAlreadyLinkedToAnActiveEmi_isNotFlaggedAgain() {
        RecurringCandidateTransaction linked = candidate(daysAgo(50), "199.00", "netflix@okicici", "Netflix");
        List<RecurringCandidateTransaction> txns = List.of(
                linked, candidate(daysAgo(25), "199.00", "netflix@okicici", "Netflix"), candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of(linked.transactionId()));

        assertThat(groups).isEmpty();
    }

    @Test
    void amountOutsideAnchoredTenPercentBand_startsANewCluster() {
        // 100 -> 110 -> 121: pairwise each within 10% of its neighbour, but 121 is 21% above the
        // cluster's anchor (100), so it must NOT join the first cluster (no pairwise chaining).
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(40), "100.00", "merchant@upi", "Merchant"),
                candidate(daysAgo(20), "110.00", "merchant@upi", "Merchant"),
                candidate(daysAgo(0), "121.00", "merchant@upi", "Merchant"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty(); // each cluster (100/110 vs. 121) has fewer than 3 members
    }

    @Test
    void manuallyEnteredEmiWithNoSourceTransaction_doesNotSuppressDetection() {
        // Conservative exclusion rule: only an exact source_transaction_id match excludes a
        // group. A manually-entered EMI with no linked transaction must not fuzzy-suppress a
        // genuinely new detection, even if its label/amount would plausibly correlate.
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(50), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(25), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        // excludedTransactionIds is empty here — a manual EMI never contributes an id to it, per
        // EmiRepository#findAllActiveSourceTransactionIds only reading non-null source_transaction_id.
        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
    }

    @Test
    void groupsFallBackToRecipientNameWhenUpiIdIsNull() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(50), "500.00", null, "Gym Membership"),
                candidate(daysAgo(25), "500.00", null, "Gym Membership"),
                candidate(daysAgo(0), "500.00", null, "Gym Membership"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).merchantKey()).isEqualTo("Gym Membership");
    }

    private Instant daysAgo(int days) {
        return now.minus(days, ChronoUnit.DAYS);
    }

    private RecurringCandidateTransaction candidate(Instant date, String amount, String upiId, String recipientName) {
        return new RecurringCandidateTransaction(UUID.randomUUID(), UUID.randomUUID(), date, new BigDecimal(amount), upiId, recipientName);
    }
}
