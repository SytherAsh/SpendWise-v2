package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors the FastAPI {@code /predict} response body exactly (ml/api/schemas.py PredictionResponse). */
public record MlPredictionResponse(
        @JsonProperty("category_id") int categoryId,
        @JsonProperty("category_name") String categoryName,
        double confidence) {}
