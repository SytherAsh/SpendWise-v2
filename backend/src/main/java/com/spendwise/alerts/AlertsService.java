package com.spendwise.alerts;

import com.spendwise.transaction.Emi;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AlertsService {

    /**
     * Records a new alert unless one of the same {@code type} (and {@code categoryId}, when
     * non-null) already fired for this user this calendar month — the suppression rule (E5-S2-T3
     * DoD, applied to all three threshold rules per the Epic 5 handoff decision). Returns empty
     * when suppressed; the evaluator job only dispatches when a value is present.
     */
    Optional<Alert> recordIfNotAlreadyTriggeredThisMonth(
            UUID userId, AlertType type, Integer categoryId, AlertPriority priority, Map<String, Object> payload);

    /**
     * Records a {@code recurring_payment} alert (always {@link AlertPriority#MEDIUM}, in-app
     * only — no HIGH-priority dispatch) unless an equivalent group (same {@code merchantKey}, an
     * amount within the same ±10% band as {@code representativeAmount}) already fired for this
     * user this calendar month (E6-S2-T1 DoD). Calendar-month scoped, not indefinite: a
     * still-recurring charge is expected to alert again in a future month, so a user who dismissed
     * this month's alert without confirming it is notified again if the charge continues.
     * {@code payload} must include {@code merchant_key} and {@code representative_amount} (used
     * by the suppression check) in addition to any display fields.
     */
    Optional<Alert> recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(
            UUID userId, String merchantKey, BigDecimal representativeAmount, Map<String, Object> payload);

    /** Sets {@code delivered_at} on confirmed dispatch (E5-S3) — never called for a suppressed (absent) alert. */
    void markDelivered(UUID userId, UUID alertId);

    /** E5-S4-T1 — newest-first, optionally filtered to unread only. */
    AlertPage list(UUID userId, int limit, UUID cursor, Boolean isRead);

    /**
     * E5-S4-T2 — never touches {@code delivered_at}.
     *
     * @throws AlertNotFoundException if absent or owned by a different user
     */
    void markRead(UUID userId, UUID alertId);

    /**
     * Confirm-as-subscription flow (E6-S2-T2) — creates (or, on a second confirm of the same
     * group, returns the already-created) EMI linked to the alert's representative transaction,
     * via {@code EmiService.createFromDetection}, and marks the alert read. Dismiss reuses {@link
     * #markRead} directly rather than a dedicated method — no EMI is created either way, so
     * dismiss needs nothing beyond the existing mark-read behavior.
     *
     * @throws AlertNotFoundException if absent or owned by a different user
     * @throws InvalidAlertConfirmationException if the alert is not of type {@code recurring_payment}
     */
    Emi confirmRecurringPayment(UUID userId, UUID alertId);
}
