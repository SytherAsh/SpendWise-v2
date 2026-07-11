package com.spendwise.transaction.dto;

import com.spendwise.transaction.Emi;

import java.math.BigDecimal;
import java.util.UUID;

public record EmiResponse(
        UUID id,
        String label,
        BigDecimal amount,
        Integer dueDay,
        boolean detectedFromSms,
        boolean isActive,
        UUID sourceTransactionId,
        String cadence,
        Double confidenceScore) {

    public static EmiResponse from(Emi emi) {
        return new EmiResponse(
                emi.id(),
                emi.label(),
                emi.amount(),
                emi.dueDay(),
                emi.detectedFromSms(),
                emi.isActive(),
                emi.sourceTransactionId(),
                emi.cadence(),
                emi.confidenceScore());
    }
}
