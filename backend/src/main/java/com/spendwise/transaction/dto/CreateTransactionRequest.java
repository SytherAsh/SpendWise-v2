package com.spendwise.transaction.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Manual entry (E3-S2-T3). {@code debit}/{@code credit}/{@code drCrIndicator} are derived
 * server-side from the sign of {@code amount} — the client only supplies the signed total.
 * {@code transactionId} and {@code source} are never accepted from the client (see
 * {@link com.spendwise.transaction.TransactionServiceImpl#createManual}).
 */
public record CreateTransactionRequest(
        @NotNull Instant transactionDate,
        @NotNull BigDecimal amount,
        BigDecimal balance,
        String transactionMode,
        String recipientName,
        String bank,
        String upiId,
        String note) {}
