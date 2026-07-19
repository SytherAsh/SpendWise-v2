"use client";

import { useState } from "react";
import { adminApiClient } from "@/lib/adminApiClient";
import { Card } from "@/components/shared/ui";

interface JobDefinition {
  key: string;
  label: string;
  description: string;
  endpoint: string;
  /** Shown as a standing caveat under the button, not gated behind a confirm dialog. */
  costNote?: string;
}

const JOBS: JobDefinition[] = [
  {
    key: "canonicalize",
    label: "Recipient canonicalization sweep",
    description: "Clusters payee name spelling variants and populates the Merge Payees review queue. See Job Schedules for its current cadence.",
    endpoint: "/admin/ml/canonicalize-recipients",
  },
  {
    key: "categorization-retry",
    label: "Categorization retry",
    description: "Re-attempts transactions left uncategorized or low-confidence. See Job Schedules for its current cadence.",
    endpoint: "/admin/categorization/retry",
  },
  {
    key: "alert-evaluation",
    label: "Alert + recurring-payment evaluator",
    description: "Evaluates budget/overspend alerts and recurring-payment detection for every user. See Job Schedules for its current cadence.",
    endpoint: "/admin/alerts/evaluate",
  },
  {
    key: "recommendation-generation",
    label: "Recommendation generator",
    description: "Generates LLM-backed savings recommendations for qualifying month-over-month spend increases. See Job Schedules for its current cadence.",
    endpoint: "/admin/recommendations/generate",
    costNote: "Makes a real LLM call per candidate — same cost as a scheduled run.",
  },
];

/** Admin's "run any scheduled job on demand" page (ML strategy phase, 2026-07-19) — same
 * trigger-and-toast pattern as MlAccuracyPanel's "Trigger retrain" button, one row per job.
 * Each row runs synchronously on the request thread, same as every other admin trigger endpoint —
 * outcomes (including partial/internal failures the job swallows internally) are visible on the
 * Logs page, filterable by event type, not in this panel's toast. */
export function ScheduledJobsPanel() {
  const [pending, setPending] = useState<string | null>(null);
  const [messages, setMessages] = useState<Record<string, string>>({});

  async function onTrigger(job: JobDefinition) {
    setPending(job.key);
    setMessages((prev) => ({ ...prev, [job.key]: "" }));
    try {
      await adminApiClient.post(job.endpoint);
      setMessages((prev) => ({ ...prev, [job.key]: "Triggered. Check the Logs page for the run outcome." }));
    } catch {
      setMessages((prev) => ({ ...prev, [job.key]: "Could not trigger this job. Please try again." }));
    } finally {
      setPending(null);
    }
  }

  return (
    <div className="max-w-2xl space-y-4">
      {JOBS.map((job) => (
        <Card key={job.key}>
          <div className="mb-2 flex items-center justify-between gap-4">
            <h3 className="text-sm font-semibold">{job.label}</h3>
            <button
              type="button"
              onClick={() => onTrigger(job)}
              disabled={pending === job.key}
              className="shrink-0 rounded-md bg-brand-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {pending === job.key ? "Triggering…" : "Run now"}
            </button>
          </div>
          <p className="text-sm text-foreground-muted">{job.description}</p>
          {job.costNote && <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">{job.costNote}</p>}
          {messages[job.key] && (
            <p role="status" className="mt-2 text-sm text-foreground-muted">
              {messages[job.key]}
            </p>
          )}
        </Card>
      ))}
    </div>
  );
}
