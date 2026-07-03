package com.spendwise.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** {@code POST /budgets} always targets the current calendar month (docs/api.md "Budget upsert"). */
public record CreateBudgetRequest(
        @NotNull Integer categoryId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal monthlyLimit) {}
