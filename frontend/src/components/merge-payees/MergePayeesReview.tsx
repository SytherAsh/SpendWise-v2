"use client";

import { useState } from "react";
import { Users } from "lucide-react";
import { Card, EmptyState, ErrorState, Spinner, StaleBanner, ProgressBar } from "@/components/shared/ui";
import { useMergeQueue, resolveMergeGroup, type MergeDecision } from "@/lib/mergePayees";
import { MergeGroupCard } from "@/components/merge-payees/MergeGroupCard";

export function MergePayeesReview() {
  const { queue, error, isLoading, isStale, refresh } = useMergeQueue();
  const [busy, setBusy] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Progress-bar denominator, derived without capturing a starting count during render (which
  // trips the react-hooks lint). Each confirmed group increments resolvedCount in the event
  // handler below while the queue's own remainingGroupCount shrinks by the same one, so
  // (remaining + resolved) stays pinned to the count at load time — no ref, no effect.
  const [resolvedCount, setResolvedCount] = useState(0);

  if (isLoading && !queue) return <Spinner label="Loading review queue…" />;
  if (error && !queue) return <ErrorState message="Could not load the payee review queue." onRetry={refresh} />;
  if (!queue) return null;

  async function handleConfirm(decisions: MergeDecision[]) {
    setBusy(true);
    setSubmitError(null);
    try {
      await resolveMergeGroup(decisions);
      setResolvedCount((n) => n + 1);
      refresh();
    } catch {
      setSubmitError("Could not save your decisions. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  const total = queue.remainingGroupCount + resolvedCount;

  return (
    <div className="max-w-lg space-y-4">
      {isStale && <StaleBanner onRetry={refresh} />}
      {submitError && <ErrorState message={submitError} onRetry={() => setSubmitError(null)} />}

      {queue.nextGroup ? (
        <>
          {total > 0 && (
            <div className="space-y-1.5">
              <p className="text-xs text-foreground-subtle">{queue.remainingGroupCount} left to review</p>
              <ProgressBar ratio={1 - queue.remainingGroupCount / total} />
            </div>
          )}
          <MergeGroupCard group={queue.nextGroup} onConfirm={handleConfirm} busy={busy} />
        </>
      ) : (
        <Card>
          <EmptyState
            icon={<Users className="size-6" />}
            title="You're all caught up"
            message="No payee names need review right now — check back after the next canonicalization sweep."
          />
        </Card>
      )}
    </div>
  );
}
