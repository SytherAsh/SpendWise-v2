package com.spendwise.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code cadence}/{@code confidenceScore} (ML strategy phase, 2026-07-11) are populated only when
 * this EMI was created from an ML-confirmed recurring detection ({@link
 * EmiService#createFromDetection}) — null for manual entries, same nullability story as {@code
 * sourceTransactionId}.
 */
public record Emi(
        UUID id,
        UUID userId,
        String label,
        BigDecimal amount,
        Integer dueDay,
        boolean detectedFromSms,
        boolean isActive,
        UUID sourceTransactionId,
        String cadence,
        Double confidenceScore) {}
