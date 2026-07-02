package com.spendwise.transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record Emi(
        UUID id,
        UUID userId,
        String label,
        BigDecimal amount,
        Integer dueDay,
        boolean detectedFromSms,
        boolean isActive,
        UUID sourceTransactionId) {}
