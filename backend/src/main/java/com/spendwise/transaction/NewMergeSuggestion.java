package com.spendwise.transaction;

/**
 * One new {@code recipient_merge_suggestions} row to insert (Merge Payees feature) — no id,
 * status, or timestamps, since {@link RecipientMergeSuggestionRepository#insertPending} always
 * inserts as {@code PENDING} with a fresh id and {@code created_at = NOW()}. Produced by {@code
 * RecipientCanonicalizationSweep} from the ML service's {@code ambiguous_groups} response,
 * already filtered against {@link RecipientMergeSuggestionRepository#findExistingPairsForUser}
 * so a resweep never re-suggests a pair already suggested or resolved.
 */
public record NewMergeSuggestion(
        String anchorName,
        String anchorUpiId,
        String anchorCanonicalName,
        String candidateName,
        String candidateUpiId,
        int score,
        String reason) {}
