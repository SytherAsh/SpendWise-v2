package com.spendwise.admin.dto;

import com.spendwise.admin.AdminUserSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminUserSummaryResponse(UUID id, String phone, String email, Instant createdAt, long transactionCount, Instant lastActivity) {

    public static AdminUserSummaryResponse from(AdminUserSummary summary) {
        return new AdminUserSummaryResponse(
                summary.id(), summary.phone(), summary.email(), summary.createdAt(), summary.transactionCount(), summary.lastActivity());
    }
}
