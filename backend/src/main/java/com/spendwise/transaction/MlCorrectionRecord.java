package com.spendwise.transaction;

import java.math.BigDecimal;

/**
 * One row of {@code ml_corrections} joined with its transaction's feature fields (E4-S3-T4) —
 * everything the FastAPI {@code /retrain} payload needs for one corrected example. Produced by a
 * cross-user query ({@link MlCorrectionRepository#findAllCorrections}), so it carries no
 * {@code user_id} — the weekly retraining job trains one global model, not a per-user one.
 */
public record MlCorrectionRecord(
        String recipientName, String upiId, String bank, String transactionMode, BigDecimal amount, String note, int categoryId) {}
