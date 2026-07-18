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
import static org.assertj.core.api.Assertions.within;

/**
 * Required tests for candidate generation (docs/operations/testing.md Alerts unit tests —
 * recurring-payment detection), updated for the ML strategy phase's loosened thresholds (2+
 * transactions, ±40% amount tolerance, 400-day window — see RecurringPaymentDetector's Javadoc for
 * why). The old strict thresholds (3+, ±10%, 60 days) now live only in
 * ml/training/recurring_labels.py's bootstrap-label definition, not here.
 */
class RecurringPaymentDetectorTest {

    private final Instant now = Instant.now();

    @Test
    void twoMatchingTransactionsWithinLooseWindowAndTolerance_isFlaggedAsACandidate() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(90), "199.00", "netflix@okicici", "Netflix"),
                candidate(daysAgo(0), "205.00", "netflix@okicici", "Netflix")); // within 40% of 199

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).merchantKey()).isEqualTo("netflix@okicici");
        assertThat(groups.get(0).transactionIds()).hasSize(2);
    }

    @Test
    void nameSpellingVariantsSharingACanonicalName_areGroupedTogether() {
        // Two spellings of one payee with no UPI id — without canonicalization they'd splinter
        // into two single-transaction groups and neither would qualify. The shared canonical name
        // collapses them into one qualifying group (ML strategy phase, 2026-07-13).
        List<RecurringCandidateTransaction> txns = List.of(
                candidateWithCanonical(daysAgo(60), "500.00", "SWIGGY", "SWIGGY"),
                candidateWithCanonical(daysAgo(0), "500.00", "Swiggy Bangalore", "SWIGGY"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).merchantKey()).isEqualTo("SWIGGY");
        assertThat(groups.get(0).transactionIds()).hasSize(2);
    }

    @Test
    void withoutACanonicalName_rawNameSpellingVariants_doNotGroup() {
        // Same two payments as above but with no canonical name assigned yet (job hasn't run) —
        // the raw-name fallback keeps them apart, confirming canonicalization is what merges them.
        List<RecurringCandidateTransaction> txns = List.of(
                candidateWithCanonical(daysAgo(60), "500.00", "SWIGGY", null),
                candidateWithCanonical(daysAgo(0), "500.00", "Swiggy Bangalore", null));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void aSingleTransaction_isNeverACandidate() {
        List<RecurringCandidateTransaction> txns = List.of(candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void matchingTransactionsSpanningMoreThanFourHundredDays_isNotACandidate() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(450), "199.00", "netflix@okicici", "Netflix"), candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void aQuarterlyPatternWellOutsideTheOldSixtyDayWindow_isNowACandidate() {
        // This is exactly the case the loosening exists to surface — the old strict rule (60-day
        // window) would never have flagged this at all.
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(270), "1200.00", "insurer@okhdfc", "Insurer"),
                candidate(daysAgo(180), "1200.00", "insurer@okhdfc", "Insurer"),
                candidate(daysAgo(90), "1200.00", "insurer@okhdfc", "Insurer"),
                candidate(daysAgo(0), "1200.00", "insurer@okhdfc", "Insurer"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).transactionIds()).hasSize(4);
    }

    @Test
    void groupAlreadyLinkedToAnActiveEmi_isNotFlaggedAgainOnceOnlyOneNonExcludedRemains() {
        RecurringCandidateTransaction linked = candidate(daysAgo(50), "199.00", "netflix@okicici", "Netflix");
        List<RecurringCandidateTransaction> txns = List.of(linked, candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of(linked.transactionId()));

        assertThat(groups).isEmpty(); // only 1 non-excluded transaction remains, below MIN_GROUP_SIZE
    }

    @Test
    void amountOutsideAnchoredFortyPercentBand_startsANewCluster() {
        // 100 -> 141: 141 is 41% above the anchor (100), just outside the ±40% band, so it must
        // start a new cluster rather than joining the first — each 1-member cluster is then below
        // MIN_GROUP_SIZE.
        List<RecurringCandidateTransaction> txns =
                List.of(candidate(daysAgo(40), "100.00", "merchant@upi", "Merchant"), candidate(daysAgo(0), "141.00", "merchant@upi", "Merchant"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).isEmpty();
    }

    @Test
    void manuallyEnteredEmiWithNoSourceTransaction_doesNotSuppressDetection() {
        // Conservative exclusion rule: only an exact source_transaction_id match excludes a
        // group. A manually-entered EMI with no linked transaction must not fuzzy-suppress a
        // genuinely new detection, even if its label/amount would plausibly correlate.
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(50), "199.00", "netflix@okicici", "Netflix"), candidate(daysAgo(0), "199.00", "netflix@okicici", "Netflix"));

        // excludedTransactionIds is empty here — a manual EMI never contributes an id to it, per
        // EmiRepository#findAllActiveSourceTransactionIds only reading non-null source_transaction_id.
        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
    }

    @Test
    void groupsFallBackToRecipientNameWhenUpiIdIsNull() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(50), "500.00", null, "Gym Membership"), candidate(daysAgo(0), "500.00", null, "Gym Membership"));

        List<RecurringGroup> groups = RecurringPaymentDetector.detect(txns, Set.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).merchantKey()).isEqualTo("Gym Membership");
    }

    @Test
    void computedFeaturesMatchTheKnownWindowStatistics() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(60), "1000.00", "gym@upi", "Gym"),
                candidate(daysAgo(30), "1000.00", "gym@upi", "Gym"),
                candidate(daysAgo(0), "1000.00", "gym@upi", "Gym"));

        RecurringCandidateFeatures features = RecurringPaymentDetector.computeFeatures(txns, now);

        assertThat(features.occurrenceCount()).isEqualTo(3);
        assertThat(features.intervalMeanDays()).isCloseTo(30.0, within(0.5));
        assertThat(features.intervalCv()).isCloseTo(0.0, within(0.01)); // identical 30-day gaps
        assertThat(features.amountMean()).isCloseTo(1000.0, within(0.01));
        assertThat(features.amountCv()).isCloseTo(0.0, within(0.01)); // identical amounts
        assertThat(features.spanDays()).isCloseTo(60.0, within(0.5));
        assertThat(features.daysSinceLastOccurrence()).isCloseTo(0.0, within(0.5));
    }

    @Test
    void daysSinceLastOccurrenceReflectsHowStaleTheGroupIsRelativeToAsOf() {
        List<RecurringCandidateTransaction> txns = List.of(
                candidate(daysAgo(90), "500.00", "gym@upi", "Gym"), candidate(daysAgo(60), "500.00", "gym@upi", "Gym"));

        Instant asOf = now.plus(10, ChronoUnit.DAYS); // 10 days after the "now" the fixture dates are relative to
        RecurringCandidateFeatures features = RecurringPaymentDetector.computeFeatures(txns, asOf);

        assertThat(features.daysSinceLastOccurrence()).isCloseTo(70.0, within(0.5)); // 60 days ago + 10 days forward
    }

    private Instant daysAgo(int days) {
        return now.minus(days, ChronoUnit.DAYS);
    }

    private RecurringCandidateTransaction candidate(Instant date, String amount, String upiId, String recipientName) {
        return new RecurringCandidateTransaction(
                UUID.randomUUID(), UUID.randomUUID(), date, new BigDecimal(amount), upiId, recipientName, null);
    }

    private RecurringCandidateTransaction candidateWithCanonical(
            Instant date, String amount, String recipientName, String recipientCanonical) {
        return new RecurringCandidateTransaction(
                UUID.randomUUID(), UUID.randomUUID(), date, new BigDecimal(amount), null, recipientName, recipientCanonical);
    }
}
