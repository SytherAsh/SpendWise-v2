package com.spendwise.analytics.dto;

import com.spendwise.analytics.CategoryTotal;
import com.spendwise.analytics.UncategorizedTotal;

import java.math.BigDecimal;

/** {@code categoryId} is null only for the synthetic "Uncategorized" row (see {@link #uncategorized}). */
public record CategoryTotalResponse(Integer categoryId, String categoryName, BigDecimal totalSpend, BigDecimal totalIncome, long transactionCount) {

    public static CategoryTotalResponse from(CategoryTotal categoryTotal) {
        return new CategoryTotalResponse(
                categoryTotal.categoryId(),
                categoryTotal.categoryName(),
                categoryTotal.totalSpend(),
                categoryTotal.totalIncome(),
                categoryTotal.transactionCount());
    }

    public static CategoryTotalResponse uncategorized(UncategorizedTotal uncategorizedTotal) {
        return new CategoryTotalResponse(
                null,
                "Uncategorized",
                uncategorizedTotal.totalSpend(),
                uncategorizedTotal.totalIncome(),
                uncategorizedTotal.transactionCount());
    }
}
