package com.spendwise.admin;

import java.time.Instant;
import java.util.UUID;

/** One row of {@code GET /admin/users} (E11-S2-T1) — a user plus basic cross-user stats. */
public record AdminUserSummary(UUID id, String phone, String email, Instant createdAt, long transactionCount, Instant lastActivity) {}
