package com.spendwise.admin.dto;

import com.spendwise.admin.AdminLogEntry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminLogResponse(UUID id, String eventType, UUID userId, Map<String, Object> payload, Instant createdAt) {

    public static AdminLogResponse from(AdminLogEntry entry) {
        return new AdminLogResponse(entry.id(), entry.eventType(), entry.userId(), entry.payload(), entry.createdAt());
    }
}
