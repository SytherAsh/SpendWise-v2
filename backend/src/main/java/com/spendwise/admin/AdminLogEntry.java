package com.spendwise.admin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One row of {@code GET /admin/logs} (E11-S2-T3) — mirrors the {@code admin_logs} table shape. */
public record AdminLogEntry(UUID id, String eventType, UUID userId, Map<String, Object> payload, Instant createdAt) {}
