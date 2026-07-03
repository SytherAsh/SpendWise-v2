package com.spendwise.alerts;

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
}
