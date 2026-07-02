package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Mirrors the FastAPI {@code /predict} request body exactly — snake_case field names, per
 * docs/deployment.md "Backend Service Communication" example. The service's Pydantic model
 * (ml/api/schemas.py PredictionRequest) is the source of truth for this shape.
 */
public record MlPredictionRequest(
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("upi_id") String upiId,
        String bank,
        @JsonProperty("transaction_mode") String transactionMode,
        BigDecimal amount,
        String note) {}
