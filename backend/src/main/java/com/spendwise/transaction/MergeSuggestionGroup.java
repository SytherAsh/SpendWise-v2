package com.spendwise.transaction;

import java.util.List;

/**
 * One anchor identity plus every candidate still pending against it (Merge Payees feature) —
 * the unit the review queue shows the user one at a time, per {@link
 * TransactionService#getMergeQueueSnapshot}.
 */
public record MergeSuggestionGroup(
        String anchorName, String anchorUpiId, String anchorCanonicalName, List<RecipientMergeSuggestion> candidates) {}
