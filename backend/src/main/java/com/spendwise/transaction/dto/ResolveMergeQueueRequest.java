package com.spendwise.transaction.dto;

import java.util.List;

/** Matches docs/spec/api.md "POST /payee-merge-queue/resolve — Request" (Merge Payees feature):
 * {@code {"decisions": [{"suggestion_id": "...", "same": true}, ...]}}. */
public record ResolveMergeQueueRequest(List<MergeDecisionRequest> decisions) {}
