package com.spendwise.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately has no {@code smsRawText} field — the column exists in the {@code transactions}
 * table for debugging (docs/database.md) but must never be readable through the Transaction
 * module's domain model, let alone serialized in a response (CLAUDE.md security invariants;
 * E3-S1-T3). {@code categoryId}/{@code confidenceScore}/{@code assignedBy} are nullable: no row
 * in {@code transaction_categories} exists until ML categorization (Epic 4) or a user correction
 * (E3-S2-T4) assigns one — transactions ingested in Epic 3 land uncategorized.
 *
 * <p>{@code recipientCanonical} (ML strategy phase, 2026-07-13) is the deduplicated payee name
 * assigned by {@code RecipientCanonicalizationJob}; null until that batch job has run for the user,
 * so every read site falls back to {@code recipientName} when it is null. The raw
 * {@code recipientName} is never mutated.
 */
public record Transaction(
        UUID id,
        UUID userId,
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
        TransactionSource source,
        Instant parsedAt,
        Integer categoryId,
        Float confidenceScore,
        String assignedBy,
        String recipientCanonical) {}
