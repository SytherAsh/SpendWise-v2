"use client";

import type { CSSProperties, ReactNode } from "react";
import {
  AlertTriangle,
  AlertCircle,
  Info,
  Sparkles,
  X,
  Repeat,
  ArrowRightLeft,
  Receipt,
  Store,
  Bell,
  TrendingUp,
  PieChart,
  PiggyBank,
  type LucideIcon,
} from "lucide-react";
import { CategoryBarChart, type CategoryTotal } from "@/components/charts/CategoryBarChart";
import { CategoryDonut } from "@/components/charts/CategoryDonut";
import { TrendLineChart, type TrendBucket } from "@/components/charts/TrendLineChart";
import { EmptyState, ErrorState, ProgressBar } from "@/components/shared/ui";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { categoryColor } from "@/lib/categories";
import { formatCurrency, formatDate } from "@/lib/format";
import { cn } from "@/lib/cn";

export { CategoryBarChart };

/**
 * One accent hue per card, so each is identifiable at a glance instead of every box on the
 * page reading as an identical neutral container. Reuses the dataviz-validated hues already
 * shipped for category color (lib/categories.ts CATEGORY_PALETTE) rather than inventing new,
 * unvalidated ones — this is a fresh per-card assignment, unrelated to any category's color.
 */
const CARD_THEME = {
  trend: "#2a78d6", // blue
  alerts: "#e34948", // red
  topSpends: "#eb6834", // orange
  topPayees: "#7c5cff", // violet
  categorySummary: "#0891b2", // cyan
  recommendations: "#16a34a", // green
  upcomingEmis: "#4f46e5", // indigo
  recentActivity: "#e0559d", // pink
  budget: "#eda100", // amber
} as const;

/** Soft tinted chip background + full-strength icon color for a card's theme hue. */
function chipStyle(color: string): CSSProperties {
  return { backgroundColor: `color-mix(in srgb, ${color} 15%, transparent)`, color };
}

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

export interface Emi {
  id: string;
  label: string;
  amount: number;
  dueDay: number | null;
  detectedFromSms: boolean;
}

export interface RecentTransaction {
  id: string;
  transactionDate: string;
  amount: number;
  recipientName: string | null;
  recipientCanonical: string | null;
  upiId: string | null;
  bank: string | null;
}

/** Next occurrence of `dueDay` (clamped to each month's real length) on/after today. */
function nextDueDate(dueDay: number, now = new Date()): Date {
  const y = now.getFullYear();
  const m = now.getMonth();
  const lastDayThisMonth = new Date(y, m + 1, 0).getDate();
  if (dueDay >= now.getDate()) return new Date(y, m, Math.min(dueDay, lastDayThisMonth));
  const lastDayNextMonth = new Date(y, m + 2, 0).getDate();
  return new Date(y, m + 1, Math.min(dueDay, lastDayNextMonth));
}

function dueLabel(due: Date, now = new Date()): string {
  const days = Math.round((due.setHours(0, 0, 0, 0) - now.setHours(0, 0, 0, 0)) / 86_400_000);
  if (days === 0) return "Due today";
  if (days === 1) return "Due tomorrow";
  return `Due in ${days}d`;
}

function humanize(type: string): string {
  return type.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Human-readable summary of what an alert is actually about, from its payload
 * (AlertEvaluatorJob's payload shape per alert type — docs/spec/database.md `alerts.payload`). */
function alertDetail(a: Alert): string | null {
  const p = a.payload;
  switch (a.type) {
    case "recurring_payment": {
      const merchant = typeof p.merchant_label === "string" ? p.merchant_label : null;
      const amount = typeof p.representative_amount === "number" ? formatCurrency(p.representative_amount) : null;
      // Only present on alerts created after the ML strategy phase (2026-07-11) — older alerts
      // predate this payload field, same as the recurring_corrections backfill gap.
      const cadence = typeof p.cadence === "string" ? p.cadence : null;
      return [merchant, amount, cadence].filter((part): part is string => part !== null).join(" · ") || null;
    }
    case "mid_month_budget": {
      const spent = typeof p.total_spent === "number" ? formatCurrency(p.total_spent) : null;
      const budget = typeof p.total_budget === "number" ? formatCurrency(p.total_budget) : null;
      return spent && budget ? `${spent} of ${budget} spent` : null;
    }
    case "category_overspend":
    case "category_approaching_limit": {
      const spent = typeof p.amount_spent === "number" ? formatCurrency(p.amount_spent) : null;
      const limit = typeof p.monthly_limit === "number" ? formatCurrency(p.monthly_limit) : null;
      return spent && limit ? `${spent} of ${limit} limit` : null;
    }
    default:
      return typeof p.message === "string" ? p.message : null;
  }
}

/** Card shell with a header, loading skeleton, error and empty states. */
function SectionCard<T>({
  title,
  icon: Icon,
  theme,
  action,
  state,
  emptyMessage,
  isEmpty,
  children,
  className,
}: {
  title: string;
  /** Card identity — a top accent + a tinted icon chip next to the title. */
  icon?: LucideIcon;
  theme?: string;
  action?: ReactNode;
  state: SectionState<T>;
  emptyMessage: string;
  isEmpty: (data: T) => boolean;
  children: (data: T) => ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn("rounded-[var(--radius)] border border-border bg-surface p-5 shadow-[var(--shadow-sm)]", className)}
      style={theme ? { borderTopWidth: 3, borderTopColor: theme } : undefined}
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          {Icon && theme && (
            <span aria-hidden className="flex size-7 shrink-0 items-center justify-center rounded-full" style={chipStyle(theme)}>
              <Icon className="size-4" />
            </span>
          )}
          <h2 className="text-sm font-semibold text-foreground">{title}</h2>
        </div>
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

export function AlertsSection({
  state,
  onConfirm,
  onDismiss,
}: {
  state: SectionState<Alert[]>;
  /** Only called for `recurring_payment` alerts — POST /alerts/:id/confirm (creates an EMI). */
  onConfirm: (id: string) => void;
  /** Any alert type — PUT /alerts/:id/read (also the dismiss path for recurring_payment, E6-S2-T2). */
  onDismiss: (id: string) => void;
}) {
  return (
    <SectionCard
      title="Alerts"
      icon={Bell}
      theme={CARD_THEME.alerts}
      state={state}
      emptyMessage="No alerts right now."
      isEmpty={(d) => d.length === 0}
    >
      {(alerts) => (
        <ul className="space-y-2">
          {alerts.slice(0, 5).map((a) => {
            const tone = ALERT_TONE[a.priority] ?? ALERT_TONE.low;
            const Icon = tone.icon;
            const detail = alertDetail(a);
            return (
              <li key={a.id} className={cn("flex items-start gap-3 rounded-[var(--radius-sm)] border p-3", tone.wrap)}>
                <Icon className={cn("mt-0.5 size-4 shrink-0", tone.accent)} aria-hidden />
                <div className="min-w-0 flex-1 text-sm">
                  <span className="font-medium text-foreground">{humanize(a.type)}</span>
                  {detail && <span className="text-foreground-muted"> — {detail}</span>}
                </div>
                <span className={cn("shrink-0 rounded-full px-2 py-0.5 text-xs font-medium capitalize", tone.badge)}>
                  {a.priority}
                </span>
                {!a.isRead && (
                  <div className="flex shrink-0 items-center gap-1">
                    {a.type === "recurring_payment" && (
                      <button
                        type="button"
                        onClick={() => onConfirm(a.id)}
                        className="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-xs font-medium text-brand-700 transition-colors hover:bg-surface-muted dark:text-brand-300"
                      >
                        Confirm
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => onDismiss(a.id)}
                      aria-label="Dismiss alert"
                      className="rounded p-0.5 text-foreground-subtle transition-colors hover:text-foreground"
                    >
                      <X className="size-4" />
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </SectionCard>
  );
}

/** Payee label matching the fallback used in lib/contacts.ts / TransactionsBrowser. Prefers the
 * canonical (deduplicated) recipient name once the canonicalization job has assigned one. */
function payeeLabel(t: RecentTransaction): string {
  return t.recipientCanonical ?? t.recipientName ?? t.upiId ?? t.bank ?? "Unknown";
}

export function TopSpendsSection({ state }: { state: SectionState<RecentTransaction[]> }) {
  return (
    <SectionCard
      title="Top spends"
      icon={Receipt}
      theme={CARD_THEME.topSpends}
      state={state}
      emptyMessage="No spending in this period yet."
      isEmpty={(d) => d.length === 0}
    >
      {(transactions) => (
        <ul className="space-y-2">
          {[...transactions]
            .sort((a, b) => a.amount - b.amount)
            .slice(0, 5)
            .map((t) => (
              <li key={t.id} className="flex items-center gap-3 rounded-[var(--radius-sm)] border border-border bg-surface-muted p-3 text-sm">
                <span aria-hidden className="flex size-8 shrink-0 items-center justify-center rounded-full" style={chipStyle(CARD_THEME.topSpends)}>
                  <Receipt className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-foreground">{payeeLabel(t)}</p>
                  <p className="text-xs text-foreground-subtle">{formatDate(t.transactionDate)}</p>
                </div>
                <span className="mono shrink-0 text-[var(--color-danger)]">{formatCurrency(Math.abs(t.amount))}</span>
              </li>
            ))}
        </ul>
      )}
    </SectionCard>
  );
}

export function TopPayeesSection({ state }: { state: SectionState<RecentTransaction[]> }) {
  const topPayees = (transactions: RecentTransaction[]) => {
    const totals = new Map<string, { total: number; count: number }>();
    for (const t of transactions) {
      const key = payeeLabel(t);
      const entry = totals.get(key) ?? { total: 0, count: 0 };
      entry.total += Math.abs(t.amount);
      entry.count += 1;
      totals.set(key, entry);
    }
    return [...totals.entries()].sort((a, b) => b[1].total - a[1].total).slice(0, 5);
  };

  return (
    <SectionCard
      title="Top payees"
      icon={Store}
      theme={CARD_THEME.topPayees}
      state={state}
      emptyMessage="No spending in this period yet."
      isEmpty={(d) => d.length === 0}
    >
      {(transactions) => (
        <ul className="space-y-2">
          {topPayees(transactions).map(([payee, { total, count }]) => (
            <li key={payee} className="flex items-center gap-3 rounded-[var(--radius-sm)] border border-border bg-surface-muted p-3 text-sm">
              <span aria-hidden className="flex size-8 shrink-0 items-center justify-center rounded-full" style={chipStyle(CARD_THEME.topPayees)}>
                <Store className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-foreground">{payee}</p>
                <p className="text-xs text-foreground-subtle">{count === 1 ? "1 transaction" : `${count} transactions`}</p>
              </div>
              <span className="mono shrink-0 text-[var(--color-danger)]">{formatCurrency(total)}</span>
            </li>
          ))}
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
      icon={Sparkles}
      theme={CARD_THEME.recommendations}
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
              <Sparkles className="mt-0.5 size-4 shrink-0" style={{ color: CARD_THEME.recommendations }} aria-hidden />
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
      icon={PiggyBank}
      theme={CARD_THEME.budget}
      state={state}
      emptyMessage="No spending against a budget this period yet."
      isEmpty={(d) => d.filter((p) => p.spent > 0).length === 0}
    >
      {(progress) => (
        <ul className="space-y-3.5">
          {progress.filter((p) => p.spent > 0).map((p) => {
            const ratio = p.monthlyLimit > 0 ? p.spent / p.monthlyLimit : 0;
            const name = categoryName(p.categoryId);
            return (
              <li key={p.categoryId}>
                <div className="mb-1.5 flex items-center justify-between gap-2 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <span aria-hidden className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: categoryColor(name, p.categoryId) }} />
                    <span className="truncate text-foreground">{name}</span>
                  </span>
                  <span className="mono shrink-0 text-foreground-muted">
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
      icon={PieChart}
      theme={CARD_THEME.categorySummary}
      state={state}
      emptyMessage="No spending to summarize yet."
      isEmpty={(d) => d.filter((c) => Number(c.totalSpend) > 0).length === 0}
    >
      {(categories) => <CategoryDonut categories={categories} />}
    </SectionCard>
  );
}

function SpanToggle({
  span,
  onChange,
  disabled,
}: {
  span: 6 | 12;
  onChange: (span: 6 | 12) => void;
  disabled?: boolean;
}) {
  return (
    <div className="inline-flex items-center rounded-[var(--radius-sm)] border border-border-strong bg-surface p-0.5 text-xs">
      {([6, 12] as const).map((s) => (
        <button
          key={s}
          type="button"
          disabled={disabled}
          onClick={() => onChange(s)}
          aria-pressed={span === s}
          className={cn(
            "rounded-[calc(var(--radius-sm)-2px)] px-2 py-1 font-medium transition-colors disabled:pointer-events-none disabled:opacity-40",
            span === s ? "bg-surface-muted text-foreground" : "text-foreground-muted hover:text-foreground",
          )}
        >
          {s === 6 ? "6M" : "1Y"}
        </button>
      ))}
    </div>
  );
}

export function TrendSection({
  state,
  span,
  onSpanChange,
  spanDisabled,
}: {
  state: SectionState<{ buckets: TrendBucket[] }>;
  span: 6 | 12;
  onSpanChange: (span: 6 | 12) => void;
  spanDisabled?: boolean;
}) {
  return (
    <SectionCard
      title="Spending trend"
      icon={TrendingUp}
      theme={CARD_THEME.trend}
      action={<SpanToggle span={span} onChange={onSpanChange} disabled={spanDisabled} />}
      state={state}
      emptyMessage="Not enough history to plot a trend yet."
      isEmpty={(d) => d.buckets.length === 0}
    >
      {(trends) => <TrendLineChart buckets={trends.buckets} />}
    </SectionCard>
  );
}

export function UpcomingEmisSection({ state }: { state: SectionState<Emi[]> }) {
  const scheduled = (data: Emi[]) =>
    data
      .filter((e) => e.dueDay != null)
      .map((e) => ({ emi: e, due: nextDueDate(e.dueDay as number) }))
      .sort((a, b) => a.due.getTime() - b.due.getTime());

  return (
    <SectionCard
      title="Upcoming EMIs & subscriptions"
      icon={Repeat}
      theme={CARD_THEME.upcomingEmis}
      state={state}
      emptyMessage="No upcoming EMIs or subscriptions."
      isEmpty={(d) => scheduled(d).length === 0}
    >
      {(emis) => (
        <ul className="space-y-2">
          {scheduled(emis)
            .slice(0, 5)
            .map(({ emi, due }) => (
              <li key={emi.id} className="flex items-center gap-3 rounded-[var(--radius-sm)] border border-border bg-surface-muted p-3 text-sm">
                <span aria-hidden className="flex size-8 shrink-0 items-center justify-center rounded-full" style={chipStyle(CARD_THEME.upcomingEmis)}>
                  <Repeat className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-foreground">{emi.label}</p>
                  <p className="text-xs text-foreground-subtle">{dueLabel(due)}</p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {emi.detectedFromSms && <Badge tone="brand">Auto-detected</Badge>}
                  <span className="mono text-foreground">{formatCurrency(emi.amount)}</span>
                </div>
              </li>
            ))}
        </ul>
      )}
    </SectionCard>
  );
}

export function RecentActivitySection({ state }: { state: SectionState<RecentTransaction[]> }) {
  return (
    <SectionCard
      title="Recent activity"
      icon={ArrowRightLeft}
      theme={CARD_THEME.recentActivity}
      state={state}
      emptyMessage="No transactions in this period yet."
      isEmpty={(d) => d.length === 0}
    >
      {(transactions) => (
        <ul className="space-y-2">
          {transactions.slice(0, 5).map((t) => {
            const isCredit = t.amount > 0;
            return (
              <li key={t.id} className="flex items-center gap-3 rounded-[var(--radius-sm)] border border-border bg-surface-muted p-3 text-sm">
                <span aria-hidden className="flex size-8 shrink-0 items-center justify-center rounded-full" style={chipStyle(CARD_THEME.recentActivity)}>
                  <ArrowRightLeft className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-foreground">{payeeLabel(t)}</p>
                  <p className="text-xs text-foreground-subtle">{formatDate(t.transactionDate)}</p>
                </div>
                <span className={cn("mono shrink-0", isCredit ? "text-[var(--color-positive)]" : "text-[var(--color-danger)]")}>
                  {isCredit ? "+" : ""}
                  {formatCurrency(Math.abs(t.amount))}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </SectionCard>
  );
}
