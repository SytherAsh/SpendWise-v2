package com.spendwise.ingest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** Field names match docs/api.md "POST /ingest/transactions — Request" exactly (snake_case — the frozen Android contract). */
public record IngestTransactionItem(
        @JsonProperty("transaction_date") @NotNull Instant transactionDate,
        @JsonProperty("debit") @NotNull BigDecimal debit,
        @JsonProperty("credit") @NotNull BigDecimal credit,
        @JsonProperty("amount") @NotNull BigDecimal amount,
        @JsonProperty("balance") BigDecimal balance,
        @JsonProperty("dr_cr_indicator") @NotBlank String drCrIndicator,
        @JsonProperty("transaction_id") @NotBlank String transactionId,
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("upi_id") String upiId,
        @JsonProperty("bank") String bank,
        @JsonProperty("transaction_mode") String transactionMode,
        @JsonProperty("note") String note,
        // Accepted for wire-contract compatibility but ignored — the server always forces
        // source = 'sms' for this endpoint regardless of what the client sends (E3-S1-T2 DoD).
        @JsonProperty("source") String source) {}
