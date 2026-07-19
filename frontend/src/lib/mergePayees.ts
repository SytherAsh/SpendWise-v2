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
  /** The candidate identity this decision is about — only needed to fire the recategorize
   * follow-up below when `same` is true, but always passed through since the caller (
   * MergeGroupCard) already has it in hand for every candidate in the group. */
  candidateName: string;
  candidateUpiId: string | null;
}

/**
 * Confirms/rejects one group's decisions, then fires two independent, best-effort follow-up
 * requests so a confirmed merge's effects are visible right away instead of waiting on a
 * scheduled job:
 *
 * 1. `POST /alerts/reevaluate` — immediate per-user re-evaluation of recurring-payment detection
 *    and budget alerts.
 * 2. `POST /categorization/recategorize` (ADR-020), once per `same: true` decision — re-runs
 *    categorization for every transaction sharing that candidate's identity, now that its
 *    canonical name has changed, so the merge actually changes what category those transactions
 *    land in rather than only their display name. Fired from here (not from the backend) because
 *    Categorization already depends on Transaction; a call the other way would be a circular
 *    module dependency — see ADR-020 in docs/spec/decisions.md.
 *
 * Both follow-ups are best-effort: if either fails, the merge itself has already been saved, and
 * the next scheduled job (AlertEvaluatorJob, or a future canonicalization resweep) still catches
 * it — same graceful-degradation posture every background job in this app follows.
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
  await Promise.all(
    decisions
      .filter((d) => d.same)
      .map((d) =>
        apiClient
          .post("/categorization/recategorize", { recipient_name: d.candidateName, upi_id: d.candidateUpiId })
          .catch(() => {
            // Non-fatal — see docstring above.
          }),
      ),
  );
}
