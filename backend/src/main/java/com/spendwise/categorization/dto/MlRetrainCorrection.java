package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Mirrors one entry of ml/api/schemas.py's {@code CorrectionExample}. */
public record MlRetrainCorrection(
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("upi_id") String upiId,
        String bank,
        @JsonProperty("transaction_mode") String transactionMode,
        BigDecimal amount,
        String note,
        @JsonProperty("category_id") int categoryId) {}
