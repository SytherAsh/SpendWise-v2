"use client";

import { CategoryBarChart, type CategoryTotal } from "@/components/charts/CategoryBarChart";
import { TrendLineChart, type TrendBucket } from "@/components/charts/TrendLineChart";
import { Card, EmptyState, ErrorState, ProgressBar, Spinner } from "@/components/shared/ui";
import { formatCurrency } from "@/lib/format";

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

function SectionShell<T>({
  title,
  state,
  emptyMessage,
  isEmpty,
  children,
}: {
  title: string;
  state: SectionState<T>;
  emptyMessage: string;
  isEmpty: (data: T) => boolean;
  children: (data: T) => React.ReactNode;
}) {
  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-neutral-500">{title}</h2>
      {state.isLoading && state.data === undefined ? (
        <Spinner />
      ) : state.error && state.data === undefined ? (
        <ErrorState message={`Could not load ${title.toLowerCase()}.`} />
      ) : state.data === undefined || isEmpty(state.data) ? (
        <EmptyState message={emptyMessage} />
      ) : (
        children(state.data)
      )}
    </Card>
  );
}

export function AlertsSection({ state }: { state: SectionState<Alert[]> }) {
  return (
    <SectionShell
      title="Alerts"
      state={state}
      emptyMessage="No alerts right now."
      isEmpty={(d) => d.length === 0}
    >
      {(alerts) => (
        <ul className="space-y-2">
          {alerts.slice(0, 5).map((a) => (
            <li key={a.id} className="flex items-start justify-between gap-3 text-sm">
              <span>
                <span className="font-medium">{humanize(a.type)}</span>
                {typeof a.payload?.message === "string" && (
                  <span className="text-neutral-500"> — {a.payload.message}</span>
                )}
              </span>
              <span
                className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${
                  a.priority === "high"
                    ? "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300"
                    : "bg-neutral-100 text-neutral-600 dark:bg-neutral-800 dark:text-neutral-300"
                }`}
              >
                {a.priority}
              </span>
            </li>
          ))}
        </ul>
      )}
    </SectionShell>
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
    <SectionShell
      title="Savings recommendations"
      state={state}
      emptyMessage="No recommendations yet."
      isEmpty={(d) => d.length === 0}
    >
      {(recs) => (
        <ul className="space-y-2">
          {recs.map((r) => (
            <li key={r.id} className="flex items-start justify-between gap-3 rounded-md bg-black/[0.03] p-3 text-sm dark:bg-white/[0.03]">
              <span>{r.text}</span>
              <button
                type="button"
                onClick={() => onDismiss(r.id)}
                aria-label={`Dismiss recommendation`}
                className="shrink-0 text-xs text-neutral-500 underline"
              >
                Dismiss
              </button>
            </li>
          ))}
        </ul>
      )}
    </SectionShell>
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
    <SectionShell
      title="Budget progress"
      state={state}
      emptyMessage="No budgets set yet."
      isEmpty={(d) => d.length === 0}
    >
      {(progress) => (
        <ul className="space-y-3">
          {progress.map((p) => {
            const ratio = p.monthlyLimit > 0 ? p.spent / p.monthlyLimit : 0;
            return (
              <li key={p.categoryId}>
                <div className="mb-1 flex justify-between text-sm">
                  <span>{categoryName(p.categoryId)}</span>
                  <span className="text-neutral-500">
                    {formatCurrency(p.spent)} / {formatCurrency(p.monthlyLimit)}
                  </span>
                </div>
                <ProgressBar ratio={ratio} danger={ratio > 1} />
              </li>
            );
          })}
        </ul>
      )}
    </SectionShell>
  );
}

export function CategorySummarySection({ state }: { state: SectionState<CategoryTotal[]> }) {
  return (
    <SectionShell
      title="Spending by category"
      state={state}
      emptyMessage="No spending to summarize yet."
      isEmpty={(d) => d.filter((c) => Number(c.totalSpend) > 0).length === 0}
    >
      {(categories) => <CategoryBarChart categories={categories} />}
    </SectionShell>
  );
}

export function TrendSection({ state }: { state: SectionState<{ buckets: TrendBucket[] }> }) {
  return (
    <SectionShell
      title="Spending trend"
      state={state}
      emptyMessage="Not enough history to plot a trend yet."
      isEmpty={(d) => d.buckets.length === 0}
    >
      {(trends) => <TrendLineChart buckets={trends.buckets} />}
    </SectionShell>
  );
}
