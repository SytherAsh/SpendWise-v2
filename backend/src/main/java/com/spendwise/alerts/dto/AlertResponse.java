package com.spendwise.alerts.dto;

import com.spendwise.alerts.Alert;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AlertResponse(
        UUID id, String type, String priority, Instant triggeredAt, Instant deliveredAt, boolean isRead, Map<String, Object> payload) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.id(),
                alert.type().dbValue(),
                alert.priority().dbValue(),
                alert.triggeredAt(),
                alert.deliveredAt(),
                alert.isRead(),
                alert.payload());
    }
}
