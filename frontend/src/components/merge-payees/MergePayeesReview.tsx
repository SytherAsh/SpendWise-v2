"use client";

import { useRef, useState } from "react";
import { Users } from "lucide-react";
import { Card, EmptyState, ErrorState, Spinner, StaleBanner, ProgressBar } from "@/components/shared/ui";
import { useMergeQueue, resolveMergeGroup } from "@/lib/mergePayees";
import { MergeGroupCard } from "@/components/merge-payees/MergeGroupCard";

export function MergePayeesReview() {
  const { queue, error, isLoading, isStale, refresh } = useMergeQueue();
  const [busy, setBusy] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Captured once, the first time real data arrives, purely for the progress bar's denominator —
  // the queue's own remainingGroupCount naturally shrinks as groups are cleared. A useState
  // initializer would run before the data has loaded (queue is still undefined on that first
  // render) and get stuck at null forever, so this uses a ref set lazily during render instead.
  const startingCountRef = useRef<number | null>(null);
  if (queue && startingCountRef.current === null) {
    startingCountRef.current = queue.remainingGroupCount;
  }

  if (isLoading && !queue) return <Spinner label="Loading review queue…" />;
  if (error && !queue) return <ErrorState message="Could not load the payee review queue." onRetry={refresh} />;
  if (!queue) return null;

  async function handleConfirm(decisions: { suggestionId: string; same: boolean }[]) {
    setBusy(true);
    setSubmitError(null);
    try {
      await resolveMergeGroup(decisions);
      refresh();
    } catch {
      setSubmitError("Could not save your decisions. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  const total = startingCountRef.current && startingCountRef.current > 0 ? startingCountRef.current : queue.remainingGroupCount;

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
