package com.spendwise.admin.dto;

import com.spendwise.admin.AdminUserDetail;
import com.spendwise.alerts.dto.AlertResponse;
import com.spendwise.budget.dto.BudgetResponse;
import com.spendwise.transaction.dto.TransactionResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /admin/users/:id} — {@code sms_raw_text} exclusion comes free from {@link TransactionResponse}. */
public record AdminUserDetailResponse(
        UUID id,
        String phone,
        String email,
        Instant createdAt,
        List<TransactionResponse> transactions,
        List<BudgetResponse> budgets,
        List<AlertResponse> alerts) {

    public static AdminUserDetailResponse from(AdminUserDetail detail) {
        return new AdminUserDetailResponse(
                detail.id(),
                detail.phone(),
                detail.email(),
                detail.createdAt(),
                detail.transactions().stream().map(TransactionResponse::from).toList(),
                detail.budgets().stream().map(BudgetResponse::from).toList(),
                detail.alerts().stream().map(AlertResponse::from).toList());
    }
}
