package com.spendwise.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** Input to {@link TransactionService#persistFromIngest} and {@link TransactionService#createManual}. */
public record NewTransactionData(
        Instant transactionDate,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal amount,
        BigDecimal balance,
        String transactionMode,
        String drCrIndicator,
        String transactionId,
        String recipientName,
        String bank,
        String upiId,
        String note,
        TransactionSource source) {}
