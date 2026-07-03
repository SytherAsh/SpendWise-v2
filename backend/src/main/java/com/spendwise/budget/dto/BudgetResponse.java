package com.spendwise.budget.dto;

import com.spendwise.budget.Budget;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetResponse(UUID id, int categoryId, BigDecimal monthlyLimit, int month, int year) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.id(), budget.categoryId(), budget.monthlyLimit(), budget.month(), budget.year());
    }
}
