"use client";

import { useApi } from "@/lib/useApi";
import { apiClient } from "@/lib/apiClient";

/** Matches MergeCandidateResponse (backend, com.spendwise.transaction.dto) — Merge Payees feature. */
export interface MergeCandidate {
  suggestionId: string;
  candidateName: string;
  candidateUpiId: string | null;
  score: number;
  reason: string;
}

/** Matches MergeGroupResponse — one anchor identity plus its still-pending candidates. */
export interface MergeGroup {
  anchorName: string;
  anchorUpiId: string | null;
  anchorCanonicalName: string;
  candidates: MergeCandidate[];
}

/** Matches MergeQueueResponse. `nextGroup` is null once every suggestion is resolved. */
export interface MergeQueue {
  nextGroup: MergeGroup | null;
  remainingGroupCount: number;
}

/** Cached by SWR — same thin-wrapper shape as lib/contacts.ts's useContacts(). */
export function useMergeQueue() {
  const { data, error, isLoading, isStale, refresh } = useApi<MergeQueue>("/payee-merge-queue");
  return { queue: data, error, isLoading, isStale, refresh };
}

export interface MergeDecision {
  suggestionId: string;
  same: boolean;
}

/**
 * Confirms/rejects one group's decisions, then triggers an immediate per-user re-evaluation of
 * recurring-payment detection and budget alerts (POST /alerts/reevaluate) so a confirmed merge's
 * effect is visible right away instead of waiting for the next scheduled sweep. The re-evaluation
 * call is best-effort: if it fails, the merge itself has already been saved, and the next
 * scheduled AlertEvaluatorJob run still catches it — same graceful-degradation posture every
 * background job in this app follows.
 */
export async function resolveMergeGroup(decisions: MergeDecision[]): Promise<void> {
  await apiClient.post("/payee-merge-queue/resolve", {
    decisions: decisions.map((d) => ({ suggestion_id: d.suggestionId, same: d.same })),
  });
  try {
    await apiClient.post("/alerts/reevaluate");
  } catch {
    // Non-fatal — see docstring above.
  }
}
