package com.spendwise.analytics.dto;

import com.spendwise.analytics.CategoryTotal;

import java.math.BigDecimal;

public record CategoryTotalResponse(int categoryId, String categoryName, BigDecimal totalSpend, BigDecimal totalIncome, long transactionCount) {

    public static CategoryTotalResponse from(CategoryTotal categoryTotal) {
        return new CategoryTotalResponse(
                categoryTotal.categoryId(),
                categoryTotal.categoryName(),
                categoryTotal.totalSpend(),
                categoryTotal.totalIncome(),
                categoryTotal.transactionCount());
    }
}
