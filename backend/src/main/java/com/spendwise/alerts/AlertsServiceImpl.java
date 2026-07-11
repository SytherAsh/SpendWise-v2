package com.spendwise.alerts;

import com.spendwise.transaction.Emi;
import com.spendwise.transaction.EmiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertsServiceImpl implements AlertsService {

    private static final BigDecimal TOLERANCE_MULTIPLIER = BigDecimal.valueOf(110, 2); // 1.10

    private final AlertRepository alertRepository;
    private final EmiService emiService;
    private final RecurringCorrectionsRepository recurringCorrectionsRepository;

    public AlertsServiceImpl(
            AlertRepository alertRepository, EmiService emiService, RecurringCorrectionsRepository recurringCorrectionsRepository) {
        this.alertRepository = alertRepository;
        this.emiService = emiService;
        this.recurringCorrectionsRepository = recurringCorrectionsRepository;
    }

    @Override
    @Transactional
    public Optional<Alert> recordIfNotAlreadyTriggeredThisMonth(
            UUID userId, AlertType type, Integer categoryId, AlertPriority priority, Map<String, Object> payload) {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (alertRepository.existsSince(userId, type, categoryId, startOfMonth)) {
            return Optional.empty();
        }
        return Optional.of(alertRepository.insert(userId, type, priority, payload));
    }

    @Override
    @Transactional
    public Optional<Alert> recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(
            UUID userId, String merchantKey, BigDecimal representativeAmount, Map<String, Object> payload) {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal amountLow = representativeAmount.divide(TOLERANCE_MULTIPLIER, 4, RoundingMode.HALF_UP);
        BigDecimal amountHigh = representativeAmount.multiply(TOLERANCE_MULTIPLIER);
        if (alertRepository.existsSinceForMerchant(userId, startOfMonth, merchantKey, amountLow, amountHigh)) {
            return Optional.empty();
        }
        return Optional.of(alertRepository.insert(userId, AlertType.RECURRING_PAYMENT, AlertPriority.MEDIUM, payload));
    }

    @Override
    @Transactional
    public void markDelivered(UUID userId, UUID alertId) {
        alertRepository.markDelivered(userId, alertId);
    }

    @Override
    @Transactional
    public AlertPage list(UUID userId, int limit, UUID cursor, Boolean isRead) {
        Instant cursorDate = cursor == null ? null : alertRepository.findTriggeredAt(userId, cursor).orElse(null);
        UUID effectiveCursorId = cursorDate == null ? null : cursor;
        List<Alert> rows = alertRepository.findPage(userId, isRead, cursorDate, effectiveCursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Alert> page = hasMore ? rows.subList(0, limit) : rows;
        UUID nextCursor = hasMore ? page.get(page.size() - 1).id() : null;
        return new AlertPage(page, nextCursor, hasMore);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID alertId) {
        Alert alert = alertRepository.findById(userId, alertId).orElseThrow(AlertNotFoundException::new);
        // Dismiss path for a recurring_payment alert (E6-S2-T2 — no dedicated dismiss endpoint,
        // this IS dismiss). Only write a correction the first time: markRead is otherwise called
        // repeatedly (any alert type, already-read alerts included) without this side effect, and
        // a second markRead on the same alert must not double-write recurring_corrections.
        if (alert.type() == AlertType.RECURRING_PAYMENT && !alert.isRead()) {
            recordRecurringCorrection(userId, alert.payload(), false);
        }
        alertRepository.markRead(userId, alertId);
    }

    @Override
    @Transactional
    public Emi confirmRecurringPayment(UUID userId, UUID alertId) {
        Alert alert = alertRepository.findById(userId, alertId).orElseThrow(AlertNotFoundException::new);
        if (alert.type() != AlertType.RECURRING_PAYMENT) {
            throw new InvalidAlertConfirmationException();
        }
        Map<String, Object> payload = alert.payload();
        String label = (String) payload.get("merchant_label");
        // payload round-trips through JSON (see AlertRepository#toJson/#fromJson), so the
        // representative_amount Jackson handed back here is a Double, not a BigDecimal — parse
        // via toString() rather than casting.
        BigDecimal amount = new BigDecimal(payload.get("representative_amount").toString());
        UUID sourceTransactionId = UUID.fromString((String) payload.get("representative_transaction_id"));
        String cadence = (String) payload.get("cadence");
        Double confidence = payload.get("confidence") == null ? null : ((Number) payload.get("confidence")).doubleValue();

        // Idempotent confirm (E6-S2-T2 DoD: confirming the same group twice is a no-op) — only
        // record a correction the first time an EMI is actually created from this alert, not on
        // the second confirm click that returns the already-created EMI unchanged.
        boolean alreadyLinked = emiService.findBySourceTransaction(userId, sourceTransactionId).isPresent();
        if (!alreadyLinked) {
            recordRecurringCorrection(userId, payload, true);
        }

        Emi emi = emiService.createFromDetection(userId, label, amount, sourceTransactionId, cadence, confidence);
        alertRepository.markRead(userId, alertId);
        return emi;
    }

    private void recordRecurringCorrection(UUID userId, Map<String, Object> payload, boolean wasRecurring) {
        UUID sourceTransactionId = UUID.fromString((String) payload.get("representative_transaction_id"));
        RecurringCandidateFeatures features = new RecurringCandidateFeatures(
                ((Number) payload.get("occurrence_count")).intValue(),
                ((Number) payload.get("interval_mean_days")).doubleValue(),
                ((Number) payload.get("interval_cv")).doubleValue(),
                ((Number) payload.get("amount_mean")).doubleValue(),
                ((Number) payload.get("amount_cv")).doubleValue(),
                ((Number) payload.get("span_days")).doubleValue(),
                ((Number) payload.get("days_since_last_occurrence")).doubleValue());
        recurringCorrectionsRepository.insert(userId, sourceTransactionId, features, wasRecurring);
    }
}
