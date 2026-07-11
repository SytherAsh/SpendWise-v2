package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors the FastAPI {@code /predict-recurring} response body (ml/api/schemas.py RecurringPredictionResponse). */
public record MlRecurringPredictionResponse(@JsonProperty("is_recurring") boolean isRecurring, double confidence, String cadence) {}
