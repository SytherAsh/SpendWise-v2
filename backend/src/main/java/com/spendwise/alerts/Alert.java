package com.spendwise.alerts;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Alert(
        UUID id,
        UUID userId,
        AlertType type,
        AlertPriority priority,
        Instant triggeredAt,
        Instant deliveredAt,
        boolean isRead,
        Map<String, Object> payload) {}
