"use client";

import type { ReactNode } from "react";
import { AlertTriangle, AlertCircle, Info, Sparkles, X } from "lucide-react";
import { CategoryBarChart, type CategoryTotal } from "@/components/charts/CategoryBarChart";
import { CategoryDonut } from "@/components/charts/CategoryDonut";
import { TrendLineChart, type TrendBucket } from "@/components/charts/TrendLineChart";
import { EmptyState, ErrorState, ProgressBar } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { categoryColor } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/cn";

export { CategoryBarChart };

interface SectionState<T> {
  data: T | undefined;
  error: unknown;
  isLoading: boolean;
}

export interface Alert {
  id: string;
  type: string;
  priority: string;
  isRead: boolean;
  payload: Record<string, unknown>;
}

export interface Recommendation {
  id: string;
  categoryId: number | null;
  text: string;
  priority: string;
}

export interface BudgetProgress {
  categoryId: number;
  monthlyLimit: number;
  spent: number;
  percentSpent: number;
}

function humanize(type: string): string {
  return type.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Card shell with a header, loading skeleton, error and empty states. */
function SectionCard<T>({
  title,
  action,
  state,
  emptyMessage,
  isEmpty,
  children,
  className,
}: {
  title: string;
  action?: ReactNode;
  state: SectionState<T>;
  emptyMessage: string;
  isEmpty: (data: T) => boolean;
  children: (data: T) => ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("rounded-[var(--radius)] border border-border bg-surface p-5 shadow-[var(--shadow-sm)]", className)}>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-foreground">{title}</h2>
        {action}
      </div>
      {state.isLoading && state.data === undefined ? (
        <div className="space-y-2.5">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ) : state.error && state.data === undefined ? (
        <ErrorState message={`Could not load ${title.toLowerCase()}.`} />
      ) : state.data === undefined || isEmpty(state.data) ? (
        <EmptyState message={emptyMessage} />
      ) : (
        children(state.data)
      )}
    </section>
  );
}

const ALERT_TONE: Record<string, { icon: typeof AlertTriangle; wrap: string; badge: string; accent: string }> = {
  high: {
    icon: AlertTriangle,
    wrap: "border-[var(--color-danger-border)] bg-[var(--color-danger-surface)]",
    badge: "bg-[var(--color-danger)] text-white",
    accent: "text-[var(--color-danger)]",
  },
  medium: {
    icon: AlertCircle,
    wrap: "border-[var(--color-warning-border)] bg-[var(--color-warning-surface)]",
    badge: "bg-[var(--color-warning)] text-white",
    accent: "text-[var(--color-warning)]",
  },
  low: {
    icon: Info,
    wrap: "border-border bg-surface-muted",
    badge: "bg-surface text-foreground-muted",
    accent: "text-foreground-subtle",
  },
};

export function AlertsSection({ state }: { state: SectionState<Alert[]> }) {
  return (
    <SectionCard
      title="Alerts"
      state={state}
      emptyMessage="No alerts right now."
      isEmpty={(d) => d.length === 0}
    >
      {(alerts) => (
        <ul className="space-y-2">
          {alerts.slice(0, 5).map((a) => {
            const tone = ALERT_TONE[a.priority] ?? ALERT_TONE.low;
            const Icon = tone.icon;
            return (
              <li key={a.id} className={cn("flex items-start gap-3 rounded-[var(--radius-sm)] border p-3", tone.wrap)}>
                <Icon className={cn("mt-0.5 size-4 shrink-0", tone.accent)} aria-hidden />
                <div className="min-w-0 flex-1 text-sm">
                  <span className="font-medium text-foreground">{humanize(a.type)}</span>
                  {typeof a.payload?.message === "string" && (
                    <span className="text-foreground-muted"> — {a.payload.message}</span>
                  )}
                </div>
                <span className={cn("shrink-0 rounded-full px-2 py-0.5 text-xs font-medium capitalize", tone.badge)}>
                  {a.priority}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </SectionCard>
  );
}

export function RecommendationsSection({
  state,
  onDismiss,
}: {
  state: SectionState<Recommendation[]>;
  onDismiss: (id: string) => void;
}) {
  return (
    <SectionCard
      title="Savings recommendations"
      state={state}
      emptyMessage="No recommendations yet."
      isEmpty={(d) => d.length === 0}
    >
      {(recs) => (
        <ul className="space-y-2">
          {recs.map((r) => (
            <li
              key={r.id}
              className="flex items-start gap-3 rounded-[var(--radius-sm)] border border-border bg-surface-muted p-3 text-sm"
            >
              <Sparkles className="mt-0.5 size-4 shrink-0 text-brand-600" aria-hidden />
              <span className="min-w-0 flex-1 text-foreground">{r.text}</span>
              <button
                type="button"
                onClick={() => onDismiss(r.id)}
                aria-label="Dismiss recommendation"
                className="shrink-0 rounded p-0.5 text-foreground-subtle transition-colors hover:text-foreground"
              >
                <X className="size-4" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}

export function BudgetSection({
  state,
  categoryName,
}: {
  state: SectionState<BudgetProgress[]>;
  categoryName: (id: number) => string;
}) {
  return (
    <SectionCard
      title="Budget progress"
      state={state}
      emptyMessage="No budgets set yet."
      isEmpty={(d) => d.length === 0}
    >
      {(progress) => (
        <ul className="space-y-3.5">
          {progress.map((p) => {
            const ratio = p.monthlyLimit > 0 ? p.spent / p.monthlyLimit : 0;
            const name = categoryName(p.categoryId);
            return (
              <li key={p.categoryId}>
                <div className="mb-1.5 flex items-center justify-between gap-2 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <span aria-hidden className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: categoryColor(name, p.categoryId) }} />
                    <span className="truncate text-foreground">{name}</span>
                  </span>
                  <span className="tnum shrink-0 text-foreground-muted">
                    {formatCurrency(p.spent)} / {formatCurrency(p.monthlyLimit)}
                  </span>
                </div>
                <ProgressBar ratio={ratio} danger={ratio > 1} />
              </li>
            );
          })}
        </ul>
      )}
    </SectionCard>
  );
}

export function CategorySummarySection({ state }: { state: SectionState<CategoryTotal[]> }) {
  return (
    <SectionCard
      title="Spending by category"
      state={state}
      emptyMessage="No spending to summarize yet."
      isEmpty={(d) => d.filter((c) => Number(c.totalSpend) > 0).length === 0}
    >
      {(categories) => <CategoryDonut categories={categories} />}
    </SectionCard>
  );
}

export function TrendSection({ state }: { state: SectionState<{ buckets: TrendBucket[] }> }) {
  return (
    <SectionCard
      title="Spending trend"
      state={state}
      emptyMessage="Not enough history to plot a trend yet."
      isEmpty={(d) => d.buckets.length === 0}
    >
      {(trends) => <TrendLineChart buckets={trends.buckets} />}
    </SectionCard>
  );
}
