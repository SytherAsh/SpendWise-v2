"use client";

import type { ReactNode } from "react";
import { Loader2, AlertTriangle, Inbox } from "lucide-react";
import { cn } from "@/lib/cn";

/** `center` is an optional slot between the title block and `action` — e.g. a page-level
 * control like `MonthStepper` that shouldn't crowd into either end. Omit it and the header
 * lays out exactly as before (title left, action right). */
export function PageHeader({
  title,
  subtitle,
  center,
  action,
}: {
  title: string;
  subtitle?: string;
  center?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-foreground-muted">{subtitle}</p>}
      </div>
      {center && <div className="flex items-center">{center}</div>}
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
      className="flex items-start gap-2.5 rounded-[var(--radius-sm)] border border-[var(--color-danger-border)] bg-[var(--color-danger-surface)] px-4 py-3 text-sm text-[var(--color-danger)]"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>
        <p>{message}</p>
        {onRetry && (
          <button type="button" onClick={onRetry} className="mt-2 font-medium underline">
            Retry
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * Empty state. Passing only `message` keeps the compact inline form; passing an
 * `icon`/`title`/`action` renders the branded, illustrative variant with a CTA.
 */
export function EmptyState({
  message,
  title,
  icon,
  action,
}: {
  message: string;
  title?: string;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  if (!title && !icon && !action) {
    return <p className="py-8 text-center text-sm text-foreground-subtle">{message}</p>;
  }
  return (
    <div className="flex flex-col items-center gap-3 py-10 text-center">
      <span className="grid size-12 place-items-center rounded-[var(--radius)] bg-[image:var(--gradient-brand-vivid)] text-[#04170d] shadow-[var(--glow-brand-sm)]">
        {icon ?? <Inbox className="size-6" />}
      </span>
      {title && <p className="font-display text-base font-semibold text-foreground">{title}</p>}
      <p className="max-w-sm text-sm text-foreground-muted">{message}</p>
      {action}
    </div>
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
