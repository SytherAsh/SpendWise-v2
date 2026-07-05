package com.spendwise.admin;

import com.spendwise.alerts.Alert;
import com.spendwise.budget.Budget;
import com.spendwise.transaction.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /admin/users/:id} (E11-S2-T1) — full transaction/budget/alert data for one user, per
 * the epic's DoD. Composed from the existing per-module service interfaces
 * ({@code TransactionService}, {@code BudgetService}, {@code AlertsService}) called with the
 * target user's id — the same cross-module call shape every other module already uses, so
 * {@code sms_raw_text} exclusion (already enforced by {@code TransactionResponse}) and RLS scoping
 * (already enforced by each service, which scopes to whatever userId is passed in) both apply with
 * zero new code.
 */
public record AdminUserDetail(
        UUID id, String phone, String email, Instant createdAt, List<Transaction> transactions, List<Budget> budgets, List<Alert> alerts) {}
