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

    public AlertsServiceImpl(AlertRepository alertRepository, EmiService emiService) {
        this.alertRepository = alertRepository;
        this.emiService = emiService;
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
        alertRepository.findById(userId, alertId).orElseThrow(AlertNotFoundException::new);
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

        Emi emi = emiService.createFromDetection(userId, label, amount, sourceTransactionId);
        alertRepository.markRead(userId, alertId);
        return emi;
    }
}
