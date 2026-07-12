package com.spendwise.alerts;

import com.spendwise.transaction.Emi;
import com.spendwise.transaction.EmiService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Required tests for E5-S2-T3 (suppression) and E5-S4 (pagination, mark-read ownership). */
class AlertsServiceImplTest {

    private final AlertRepository alertRepository = mock(AlertRepository.class);
    private final EmiService emiService = mock(EmiService.class);
    private final RecurringCorrectionsRepository recurringCorrectionsRepository = mock(RecurringCorrectionsRepository.class);
    private final AlertsServiceImpl service = new AlertsServiceImpl(alertRepository, emiService, recurringCorrectionsRepository);
    private final UUID userId = UUID.randomUUID();

    @Test
    void suppressesAnAlertOfTheSameTypeAndCategoryAlreadyTriggeredThisMonth() {
        given(alertRepository.existsSince(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(7), any())).willReturn(true);

        Optional<Alert> result =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.CATEGORY_OVERSPEND, 7, AlertPriority.HIGH, Map.of());

        assertThat(result).isEmpty();
        verify(alertRepository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void runningTheEvaluatorTwiceDoesNotCreateASecondAlert() {
        given(alertRepository.existsSince(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(7), any()))
                .willReturn(false)
                .willReturn(true);
        given(alertRepository.insert(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(AlertPriority.HIGH), any()))
                .willReturn(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH));

        Optional<Alert> first =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.CATEGORY_OVERSPEND, 7, AlertPriority.HIGH, Map.of());
        Optional<Alert> second =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.CATEGORY_OVERSPEND, 7, AlertPriority.HIGH, Map.of());

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    void recordsANewAlertWhenNoneAlreadyTriggeredThisMonth() {
        given(alertRepository.existsSince(eq(userId), eq(AlertType.MID_MONTH_BUDGET), eq(null), any())).willReturn(false);
        given(alertRepository.insert(eq(userId), eq(AlertType.MID_MONTH_BUDGET), eq(AlertPriority.HIGH), any()))
                .willReturn(sampleAlert(AlertType.MID_MONTH_BUDGET, AlertPriority.HIGH));

        Optional<Alert> result =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.MID_MONTH_BUDGET, null, AlertPriority.HIGH, Map.of());

        assertThat(result).isPresent();
    }

    @Test
    void markReadThrowsNotFoundForMissingOrForeignAlert() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.markRead(userId, alertId));
    }

    @Test
    void markReadDoesNotTouchDeliveredAt() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH)));

        service.markRead(userId, alertId);

        verify(alertRepository).markRead(userId, alertId);
        verify(alertRepository, never()).markDelivered(any(), any());
    }

    @Test
    void listFirstPageDoesNotResolveACursor() {
        given(alertRepository.findPage(eq(userId), eq(null), eq(null), eq(null), eq(21)))
                .willReturn(List.of(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH)));

        AlertPage page = service.list(userId, 20, null, null);

        verify(alertRepository, never()).findTriggeredAt(any(), any());
        assertThat(page.hasMore()).isFalse();
        assertThat(page.data()).hasSize(1);
    }

    @Test
    void listUnreadFilterIsPassedThrough() {
        given(alertRepository.findPage(eq(userId), eq(true), eq(null), eq(null), anyInt())).willReturn(List.of());

        service.list(userId, 20, null, true);

        verify(alertRepository).findPage(userId, true, null, null, 21);
    }

    @Test
    void suppressesARecurringPaymentAlertForTheSameMerchantAndAmountBandThisMonth() {
        given(alertRepository.existsSinceForMerchant(eq(userId), any(), eq("netflix@okicici"), any(), any())).willReturn(true);

        Optional<Alert> result = service.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(
                userId, "netflix@okicici", BigDecimal.valueOf(199), Map.of("merchant_key", "netflix@okicici"));

        assertThat(result).isEmpty();
        verify(alertRepository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void recordsANewRecurringPaymentAlertAtMediumPriorityWhenNoneAlreadyTriggeredThisMonth() {
        given(alertRepository.existsSinceForMerchant(eq(userId), any(), eq("netflix@okicici"), any(), any())).willReturn(false);
        given(alertRepository.insert(eq(userId), eq(AlertType.RECURRING_PAYMENT), eq(AlertPriority.MEDIUM), any()))
                .willReturn(sampleAlert(AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM));

        Optional<Alert> result = service.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(
                userId, "netflix@okicici", BigDecimal.valueOf(199), Map.of("merchant_key", "netflix@okicici"));

        assertThat(result).isPresent();
        assertThat(result.get().priority()).isEqualTo(AlertPriority.MEDIUM);
    }

    @Test
    void confirmThrowsForANonRecurringPaymentAlert() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH)));

        assertThrows(InvalidAlertConfirmationException.class, () -> service.confirmRecurringPayment(userId, alertId));
        verify(emiService, never()).createFromDetection(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmThrowsNotFoundForMissingOrForeignAlert() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.confirmRecurringPayment(userId, alertId));
    }

    @Test
    void confirmCreatesTheLinkedEmiAndMarksTheAlertRead() {
        UUID alertId = UUID.randomUUID();
        UUID sourceTransactionId = UUID.randomUUID();
        Map<String, Object> payload = recurringPayload(sourceTransactionId);
        Alert alert = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, payload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alert));
        given(emiService.findBySourceTransaction(userId, sourceTransactionId)).willReturn(Optional.empty());
        Emi createdEmi = new Emi(
                UUID.randomUUID(), userId, "Netflix", BigDecimal.valueOf(199.0), null, true, true, sourceTransactionId, "monthly", 0.9);
        given(emiService.createFromDetection(userId, "Netflix", BigDecimal.valueOf(199.0), sourceTransactionId, "monthly", 0.9))
                .willReturn(createdEmi);

        Emi result = service.confirmRecurringPayment(userId, alertId);

        assertThat(result).isEqualTo(createdEmi);
        verify(alertRepository).markRead(userId, alertId);
    }

    @Test
    void confirmRecordsAPositiveRecurringCorrectionOnlyOnceForANewlyLinkedEmi() {
        UUID alertId = UUID.randomUUID();
        UUID sourceTransactionId = UUID.randomUUID();
        Map<String, Object> payload = recurringPayload(sourceTransactionId);
        Alert alert = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, payload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alert));
        given(emiService.findBySourceTransaction(userId, sourceTransactionId)).willReturn(Optional.empty());
        given(emiService.createFromDetection(any(), any(), any(), any(), any(), any())).willReturn(
                new Emi(UUID.randomUUID(), userId, "Netflix", BigDecimal.valueOf(199.0), null, true, true, sourceTransactionId, "monthly", 0.9));

        service.confirmRecurringPayment(userId, alertId);

        verify(recurringCorrectionsRepository)
                .insert(eq(userId), eq(sourceTransactionId), any(RecurringCandidateFeatures.class), eq(true));
    }

    @Test
    void confirmDoesNotDoubleRecordACorrectionWhenTheEmiAlreadyExists() {
        // Second confirm click on the same already-linked group (E6-S2-T2 idempotency) — a
        // correction was already recorded the first time; recording it again would double-count
        // this group in the next retrain.
        UUID alertId = UUID.randomUUID();
        UUID sourceTransactionId = UUID.randomUUID();
        Map<String, Object> payload = recurringPayload(sourceTransactionId);
        Alert alert = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, payload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alert));
        Emi existing =
                new Emi(UUID.randomUUID(), userId, "Netflix", BigDecimal.valueOf(199.0), null, true, true, sourceTransactionId, "monthly", 0.9);
        given(emiService.findBySourceTransaction(userId, sourceTransactionId)).willReturn(Optional.of(existing));
        given(emiService.createFromDetection(any(), any(), any(), any(), any(), any())).willReturn(existing);

        service.confirmRecurringPayment(userId, alertId);

        verify(recurringCorrectionsRepository, never()).insert(any(), any(), any(), anyBoolean());
    }

    @Test
    void dismissingAnUnreadRecurringPaymentAlertRecordsANegativeCorrection() {
        UUID alertId = UUID.randomUUID();
        UUID sourceTransactionId = UUID.randomUUID();
        Map<String, Object> payload = recurringPayload(sourceTransactionId);
        Alert alert = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, payload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alert));

        service.markRead(userId, alertId);

        verify(recurringCorrectionsRepository)
                .insert(eq(userId), eq(sourceTransactionId), any(RecurringCandidateFeatures.class), eq(false));
    }

    @Test
    void markReadOnAnAlreadyReadRecurringPaymentAlertDoesNotDoubleRecordACorrection() {
        UUID alertId = UUID.randomUUID();
        UUID sourceTransactionId = UUID.randomUUID();
        Map<String, Object> payload = recurringPayload(sourceTransactionId);
        Alert alreadyRead = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, true, payload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alreadyRead));

        service.markRead(userId, alertId);

        verify(recurringCorrectionsRepository, never()).insert(any(), any(), any(), anyBoolean());
    }

    @Test
    void markReadOnANonRecurringAlertNeverTouchesCorrections() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH)));

        service.markRead(userId, alertId);

        verify(recurringCorrectionsRepository, never()).insert(any(), any(), any(), anyBoolean());
    }

    @Test
    void confirmingAPreMlPhaseAlertSucceedsWithoutRecordingACorrection() {
        // Payload shape from before this session's AlertEvaluatorJob change -- no occurrence_count
        // or its siblings, no cadence/confidence. Must not throw a NullPointerException.
        UUID alertId = UUID.randomUUID();
        UUID sourceTransactionId = UUID.randomUUID();
        Map<String, Object> legacyPayload = Map.of(
                "merchant_key", "netflix@okicici",
                "merchant_label", "Netflix",
                "representative_amount", 199.0,
                "representative_transaction_id", sourceTransactionId.toString(),
                "transaction_count", 3);
        Alert alert = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, legacyPayload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alert));
        given(emiService.findBySourceTransaction(userId, sourceTransactionId)).willReturn(Optional.empty());
        given(emiService.createFromDetection(userId, "Netflix", BigDecimal.valueOf(199.0), sourceTransactionId, null, null))
                .willReturn(new Emi(UUID.randomUUID(), userId, "Netflix", BigDecimal.valueOf(199.0), null, true, true, sourceTransactionId, null, null));

        service.confirmRecurringPayment(userId, alertId);

        verify(recurringCorrectionsRepository, never()).insert(any(), any(), any(), anyBoolean());
    }

    @Test
    void dismissingAPreMlPhaseAlertSucceedsWithoutRecordingACorrection() {
        UUID alertId = UUID.randomUUID();
        Map<String, Object> legacyPayload = Map.of(
                "merchant_key", "netflix@okicici",
                "merchant_label", "Netflix",
                "representative_amount", 199.0,
                "representative_transaction_id", UUID.randomUUID().toString(),
                "transaction_count", 3);
        Alert alert = new Alert(alertId, userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, Instant.now(), null, false, legacyPayload);
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(alert));

        service.markRead(userId, alertId);

        verify(recurringCorrectionsRepository, never()).insert(any(), any(), any(), anyBoolean());
        verify(alertRepository).markRead(userId, alertId);
    }

    private Alert sampleAlert(AlertType type, AlertPriority priority) {
        return new Alert(UUID.randomUUID(), userId, type, priority, Instant.now(), null, false, Map.of());
    }

    // Payload values are Double/String here, not BigDecimal/UUID — mirroring what
    // AlertRepository#fromJson actually hands back after a JSON round-trip.
    private Map<String, Object> recurringPayload(UUID sourceTransactionId) {
        return Map.ofEntries(
                Map.entry("merchant_key", "netflix@okicici"),
                Map.entry("merchant_label", "Netflix"),
                Map.entry("representative_amount", 199.0),
                Map.entry("representative_transaction_id", sourceTransactionId.toString()),
                Map.entry("transaction_count", 3),
                Map.entry("occurrence_count", 3),
                Map.entry("interval_mean_days", 30.0),
                Map.entry("interval_cv", 0.05),
                Map.entry("amount_mean", 199.0),
                Map.entry("amount_cv", 0.02),
                Map.entry("span_days", 60.0),
                Map.entry("days_since_last_occurrence", 2.0),
                Map.entry("confidence", 0.9),
                Map.entry("cadence", "monthly"));
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
