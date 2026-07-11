package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the FastAPI {@code /predict-recurring} request body exactly (ml/api/schemas.py
 * RecurringPredictionRequest) — one candidate group's statistics, computed by {@code
 * RecurringPaymentDetector} from a loosened candidate window.
 */
public record MlRecurringPredictionRequest(
        @JsonProperty("occurrence_count") int occurrenceCount,
        @JsonProperty("interval_mean_days") double intervalMeanDays,
        @JsonProperty("interval_cv") double intervalCv,
        @JsonProperty("amount_mean") double amountMean,
        @JsonProperty("amount_cv") double amountCv,
        @JsonProperty("span_days") double spanDays,
        @JsonProperty("days_since_last_occurrence") double daysSinceLastOccurrence) {}
