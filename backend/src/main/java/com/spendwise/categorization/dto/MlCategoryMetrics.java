package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors one entry of ml/api/schemas.py's {@code CategoryMetrics}. */
public record MlCategoryMetrics(
        @JsonProperty("category_id") int categoryId,
        @JsonProperty("category_name") String categoryName,
        double precision,
        double recall,
        @JsonProperty("f1_score") double f1Score,
        int support) {}
