package com.spendwise.alerts;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One qualifying recurring-charge group produced by {@link RecurringPaymentDetector} (E6-S1-T1)
 * for a single user. {@code representativeTransactionId} is the most recent (max {@code
 * transactionDate}) non-excluded transaction in the group — it becomes {@code
 * emis.source_transaction_id} if the user confirms the resulting alert (E6-S2-T2).
 */
public record RecurringGroup(
        String merchantKey,
        String merchantLabel,
        BigDecimal representativeAmount,
        UUID representativeTransactionId,
        List<UUID> transactionIds) {}
