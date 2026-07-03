package com.spendwise.budget.dto;

import com.spendwise.budget.BudgetSuggestion;

import java.math.BigDecimal;

public record BudgetSuggestionResponse(int categoryId, BigDecimal suggestedMonthlyLimit, boolean available) {

    public static BudgetSuggestionResponse from(BudgetSuggestion suggestion) {
        return new BudgetSuggestionResponse(suggestion.categoryId(), suggestion.suggestedMonthlyLimit(), suggestion.available());
    }
}
