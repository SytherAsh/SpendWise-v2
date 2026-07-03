package com.spendwise.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One CSV row for `GET /analytics/export/csv` (E7-S2-T1). Deliberately has no {@code smsRawText}
 * field, same discipline as {@link com.spendwise.transaction.Transaction} (CLAUDE.md security
 * invariants) — this type is Analytics' own, not a reuse of Transaction's domain model, per the
 * module-boundary decision that Analytics never depends on {@code com.spendwise.transaction}.
 */
public record AnalyticsExportRow(
        UUID id,
        Instant transactionDate,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal amount,
        BigDecimal balance,
        String transactionMode,
        String drCrIndicator,
        String bankTransactionId,
        String recipientName,
        String bank,
        String upiId,
        String note,
        String source,
        Integer categoryId,
        String categoryName) {}
