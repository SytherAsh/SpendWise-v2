package com.spendwise.budget.dto;

import com.spendwise.budget.BudgetProgress;

import java.math.BigDecimal;

/** {@code percentSpent} is a percentage value (e.g. {@code 55.00} means 55%), not a 0–1 fraction. */
public record BudgetProgressResponse(int categoryId, BigDecimal monthlyLimit, BigDecimal spent, BigDecimal percentSpent) {

    public static BudgetProgressResponse from(BudgetProgress progress) {
        return new BudgetProgressResponse(progress.categoryId(), progress.monthlyLimit(), progress.spent(), progress.percentSpent());
    }
}
