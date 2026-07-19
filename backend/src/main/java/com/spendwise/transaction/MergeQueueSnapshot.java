package com.spendwise.transaction;

/**
 * One page of the Merge Payees review queue — the oldest still-unresolved anchor group (or
 * {@code null} if the user has cleared every suggestion), plus how many distinct anchor groups
 * (not raw candidate rows) remain, for the review page's "N left" progress indicator.
 */
public record MergeQueueSnapshot(MergeSuggestionGroup nextGroup, int remainingGroupCount) {}
