"use client";

import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-foreground-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "rounded-[var(--radius)] border border-border bg-surface p-5 shadow-[var(--shadow-sm)]",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-6 text-sm text-foreground-muted">
      <Loader2 className="size-4 animate-spin" aria-hidden />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="rounded-[var(--radius-sm)] border border-[var(--color-danger-border)] bg-[var(--color-danger-surface)] px-4 py-3 text-sm text-[var(--color-danger)]"
    >
      <p>{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-2 font-medium underline">
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <p className="py-8 text-center text-sm text-foreground-subtle">{message}</p>
  );
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
      className="mb-4 flex items-center justify-between gap-3 rounded-[var(--radius-sm)] border border-[var(--color-warning-border)] bg-[var(--color-warning-surface)] px-4 py-2 text-sm text-[var(--color-warning)]"
    >
      <span>Showing last-loaded data — the server is currently unreachable.</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="shrink-0 font-medium underline">
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
    <div className="h-2.5 w-full overflow-hidden rounded-full bg-surface-muted">
      <div
        className={cn(
          "h-full rounded-full transition-[width] duration-500",
          over || danger ? "bg-[var(--color-danger)]" : "bg-brand-600",
        )}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
