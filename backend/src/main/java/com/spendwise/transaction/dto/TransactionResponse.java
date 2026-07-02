package com.spendwise.transaction.dto;

import com.spendwise.transaction.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Hand-built response DTO used by every Transaction-module controller — never the {@link
 * Transaction} record (which itself has no {@code smsRawText} field to begin with) and never a
 * raw JPA/entity object (CLAUDE.md security invariants; E3-S1-T3). This is the single mapping
 * point every transaction-returning endpoint must go through.
 */
public record TransactionResponse(
        UUID id,
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
        String source,
        Instant parsedAt,
        Integer categoryId,
        Float confidenceScore,
        String assignedBy) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.transactionDate(),
                transaction.debit(),
                transaction.credit(),
                transaction.amount(),
                transaction.balance(),
                transaction.transactionMode(),
                transaction.drCrIndicator(),
                transaction.transactionId(),
                transaction.recipientName(),
                transaction.bank(),
                transaction.upiId(),
                transaction.note(),
                transaction.source().dbValue(),
                transaction.parsedAt(),
                transaction.categoryId(),
                transaction.confidenceScore(),
                transaction.assignedBy());
    }
}
