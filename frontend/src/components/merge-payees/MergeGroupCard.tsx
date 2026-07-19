"use client";

import { useState } from "react";
import { motion, type PanInfo } from "framer-motion";
import { Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/shared/ui";
import { cn } from "@/lib/cn";
import { diffTokens } from "@/lib/nameDiff";
import type { MergeGroup, MergeCandidate, MergeDecision } from "@/lib/mergePayees";

type Decision = "same" | "different" | null;

/** Swipe distance (px) past which a drag counts as a decision — matched to framer-motion's
 * `dragElastic`, generous enough to avoid accidental triggers from a small scroll-adjacent
 * gesture. Buttons remain the primary, always-available control; drag is a progressive
 * enhancement on top, never a replacement. */
const DRAG_THRESHOLD = 120;

export function MergeGroupCard({
  group,
  onConfirm,
  busy,
}: {
  group: MergeGroup;
  onConfirm: (decisions: MergeDecision[]) => void;
  busy: boolean;
}) {
  const [decisions, setDecisions] = useState<Record<string, Decision>>({});

  function setDecision(suggestionId: string, decision: Decision) {
    setDecisions((prev) => ({ ...prev, [suggestionId]: decision }));
  }

  const allDecided = group.candidates.every((c) => decisions[c.suggestionId] != null);

  function handleConfirm() {
    onConfirm(
      group.candidates.map((c) => ({
        suggestionId: c.suggestionId,
        same: decisions[c.suggestionId] === "same",
        candidateName: c.candidateName,
        candidateUpiId: c.candidateUpiId,
      })),
    );
  }

  return (
    <Card className="space-y-5">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-foreground-subtle">Comparing to</p>
        <h2 className="mt-1 font-display text-xl font-semibold text-foreground">{group.anchorCanonicalName}</h2>
        <p className="mt-1 text-sm text-foreground-subtle">Is this the same person or business?</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {group.candidates.map((candidate) => (
          <CandidateCard
            key={candidate.suggestionId}
            candidate={candidate}
            anchorName={group.anchorCanonicalName}
            decision={decisions[candidate.suggestionId] ?? null}
            onDecide={(d) => setDecision(candidate.suggestionId, d)}
          />
        ))}
      </div>

      <Button size="lg" className="w-full" disabled={!allDecided || busy} onClick={handleConfirm}>
        {busy ? "Saving…" : "Confirm & next"}
      </Button>
    </Card>
  );
}

function CandidateCard({
  candidate,
  anchorName,
  decision,
  onDecide,
}: {
  candidate: MergeCandidate;
  anchorName: string;
  decision: Decision;
  onDecide: (decision: Decision) => void;
}) {
  function handleDragEnd(_event: unknown, info: PanInfo) {
    if (info.offset.x > DRAG_THRESHOLD) onDecide("same");
    else if (info.offset.x < -DRAG_THRESHOLD) onDecide("different");
  }

  const tokens = diffTokens(candidate.candidateName, anchorName);

  return (
    <motion.div
      drag="x"
      dragConstraints={{ left: 0, right: 0 }}
      dragElastic={0.15}
      onDragEnd={handleDragEnd}
      animate={{ x: 0 }}
      whileDrag={{ cursor: "grabbing" }}
      className={cn(
        "flex cursor-grab touch-pan-y flex-col gap-3 rounded-[var(--radius-sm)] border p-4 transition-colors active:cursor-grabbing",
        decision === "same" && "border-brand-500 bg-brand-50 dark:bg-brand-400/15",
        decision === "different" && "border-[var(--color-danger-border)] bg-[var(--color-danger-surface)]",
        decision === null && "border-border",
      )}
    >
      {/* Differing tokens (relative to the anchor name above) are bolded and accent-colored so
          the mismatch is visible at a glance instead of requiring a careful re-read of both names. */}
      <p className="font-medium text-foreground">
        {tokens.map((token, i) => (
          <span key={i} className={token.differs ? "font-semibold text-brand-600 dark:text-brand-400" : undefined}>
            {i > 0 ? " " : ""}
            {token.text}
          </span>
        ))}
      </p>
      <p className="text-xs text-foreground-subtle">Match score: {candidate.score}%</p>
      <div className="flex gap-2">
        <Button variant={decision === "same" ? "primary" : "secondary"} size="sm" className="flex-1" onClick={() => onDecide("same")}>
          <Check className="size-4" /> Same
        </Button>
        <Button variant={decision === "different" ? "danger" : "secondary"} size="sm" className="flex-1" onClick={() => onDecide("different")}>
          <X className="size-4" /> Different
        </Button>
      </div>
    </motion.div>
  );
}
