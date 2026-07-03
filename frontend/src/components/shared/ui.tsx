"use client";

import type { ReactNode } from "react";

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-neutral-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-black/10 bg-white p-5 dark:border-white/10 dark:bg-neutral-900 ${className}`}>
      {children}
    </div>
  );
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return <p className="py-6 text-center text-sm text-neutral-500">{label}</p>;
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
      <p>{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-2 underline">
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <p className="py-8 text-center text-sm text-neutral-400">{message}</p>;
}

/**
 * Banner shown when the backend is unreachable but we still have last-fetched data to
 * display (E10-S3). Deliberately non-blocking — it sits above stale content rather than
 * replacing it.
 */
export function StaleBanner({ onRetry }: { onRetry?: () => void }) {
  return (
    <div
      role="status"
      className="mb-4 flex items-center justify-between gap-3 rounded-md bg-amber-50 px-4 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200"
    >
      <span>Showing last-loaded data — the server is currently unreachable.</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="shrink-0 underline">
          Retry
        </button>
      )}
    </div>
  );
}

export function ProgressBar({ ratio, danger = false }: { ratio: number; danger?: boolean }) {
  const pct = Math.min(Math.max(ratio, 0), 1) * 100;
  const over = ratio > 1;
  return (
    <div className="h-2.5 w-full overflow-hidden rounded-full bg-black/10 dark:bg-white/10">
      <div
        className={`h-full rounded-full ${over || danger ? "bg-red-500" : "bg-blue-600"}`}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
