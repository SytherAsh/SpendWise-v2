"use client";

import { useState } from "react";
import { useAdminApi } from "@/lib/useAdminApi";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";

interface AdminLog {
  id: string;
  eventType: string;
  userId: string | null;
  payload: Record<string, unknown>;
  createdAt: string;
}

const EVENT_TYPES = ["parse_failure", "model_retrain", "sync_error", "prediction_low_confidence"];

export function LogsViewer() {
  const [eventType, setEventType] = useState("");
  const query = eventType ? `/admin/logs?eventType=${encodeURIComponent(eventType)}` : "/admin/logs";
  const { data, error, isLoading, refresh } = useAdminApi<AdminLog[]>(query);

  return (
    <div className="max-w-3xl space-y-4">
      <label className="block text-sm">
        <span className="mb-1 block text-foreground-muted">Filter by event type</span>
        <select
          value={eventType}
          onChange={(e) => setEventType(e.target.value)}
          className="w-full max-w-xs rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
        >
          <option value="">All event types</option>
          {EVENT_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>

      {isLoading && !data ? (
        <Spinner />
      ) : error && !data ? (
        <ErrorState message="Could not load logs." onRetry={refresh} />
      ) : !data || data.length === 0 ? (
        <EmptyState message="No log entries." />
      ) : (
        data.map((log) => (
          <Card key={log.id}>
            <div className="mb-1 flex items-center justify-between text-sm">
              <span className="font-mono font-medium">{log.eventType}</span>
              <span className="text-foreground-muted">{new Date(log.createdAt).toLocaleString()}</span>
            </div>
            {log.userId && <p className="mb-1 text-xs text-foreground-muted">user {log.userId}</p>}
            <pre className="overflow-x-auto rounded-md bg-black/5 p-2 text-xs dark:bg-white/5">
              {JSON.stringify(log.payload, null, 2)}
            </pre>
          </Card>
        ))
      )}
    </div>
  );
}
