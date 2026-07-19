package com.spendwise.transaction;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code recipient_merge_suggestions} (Merge Payees feature, ML strategy phase,
 * 2026-07-19) — an anchor/candidate identity pair FastAPI's {@code /normalize-recipients}
 * clustering considered but did not confidently auto-merge, awaiting (or already given) a user
 * decision. {@code status} is one of {@code PENDING}, {@code CONFIRMED_SAME}, {@code
 * CONFIRMED_DIFFERENT} (see {@code V15__recipient_merge_suggestions.sql}'s check constraint).
 */
public record RecipientMergeSuggestion(
        UUID id,
        UUID userId,
        String anchorName,
        String anchorUpiId,
        String anchorCanonicalName,
        String candidateName,
        String candidateUpiId,
        int score,
        String reason,
        String status,
        Instant createdAt,
        Instant resolvedAt) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED_SAME = "CONFIRMED_SAME";
    public static final String STATUS_CONFIRMED_DIFFERENT = "CONFIRMED_DIFFERENT";
}
