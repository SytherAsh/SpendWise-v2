"use client";

import { useState } from "react";
import { TrendingDown, Wallet, ArrowDownUp } from "lucide-react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useDateRange, monthSpan, trailingMonths } from "@/lib/date-range";
import {
  AlertsSection,
  BudgetSection,
  CategorySummarySection,
  RecommendationsSection,
  RecentActivitySection,
  TopPayeesSection,
  TopSpendsSection,
  TrendSection,
  UpcomingEmisSection,
  type Alert,
  type BudgetProgress,
  type Emi,
  type Recommendation,
  type RecentTransaction,
} from "@/components/dashboard/sections";
import { StatTile } from "@/components/shared/StatTile";
import { StaleBanner } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { categoryColor } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";
import type { CategoryTotal } from "@/components/charts/CategoryBarChart";
import type { TrendBucket } from "@/components/charts/TrendLineChart";

interface AlertListResponse {
  data: Alert[];
  nextCursor: string | null;
  hasMore: boolean;
}
interface SummaryResponse {
  totalSpend: number;
  totalIncome: number;
  categories: CategoryTotal[];
}
interface TrendsResponse {
  granularity: string;
  buckets: TrendBucket[];
}
interface TransactionListResponse {
  data: RecentTransaction[];
  nextCursor: string | null;
  hasMore: boolean;
}

export function DashboardView() {
  const { range } = useDateRange();
  const { categoryName } = useCategories();
  const [trendSpan, setTrendSpan] = useState<6 | 12>(6);

  // The trend chart always wants several months of history, unlike the rest of the dashboard
  // (which is scoped to whatever single period the page's date range/MonthStepper is on) — so
  // it gets its own window: "This financial year" is shown exactly as selected (not floored),
  // everything else trails 6 (or 12, via the toggle) months back from the selected range's end,
  // expanding further if the selected range itself already covers more than that.
  const trendWindow =
    range.preset === "this-fy"
      ? { from: range.from, to: range.to }
      : trailingMonths(range.to, Math.max(monthSpan(range.from, range.to), trendSpan));

  const alerts = useApi<AlertListResponse>("/alerts?limit=20");
  const recommendations = useApi<Recommendation[]>("/recommendations");
  const budgets = useApi<BudgetProgress[]>("/budgets/progress");
  const summary = useApi<SummaryResponse>(`/analytics/summary?from=${range.from}&to=${range.to}`);
  const trends = useApi<TrendsResponse>(`/analytics/trends?granularity=month&from=${trendWindow.from}&to=${trendWindow.to}`);
  const emis = useApi<Emi[]>("/emis");
  const recent = useApi<TransactionListResponse>(`/transactions?limit=5&from=${range.from}&to=${range.to}`);
  // Bounded, non-paginated read (matches TransactionsBrowser's GROUP_FETCH_LIMIT tradeoff) —
  // backs both Top Spends and Top Payees so they share one fetch instead of two.
  const topDebits = useApi<TransactionListResponse>(
    `/transactions?direction=debit&limit=500&from=${range.from}&to=${range.to}`,
  );

  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  async function dismiss(id: string) {
    setDismissed((prev) => new Set(prev).add(id));
    try {
      await apiClient.put(`/recommendations/${id}/dismiss`);
    } catch {
      setDismissed((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  }

  const visibleRecs = (recommendations.data ?? []).filter((r) => !dismissed.has(r.id));

  // Hero metrics derived from the summary + trend already being fetched.
  const totalSpend = summary.data?.totalSpend ?? 0;
  const totalIncome = summary.data?.totalIncome ?? 0;
  const net = totalIncome - totalSpend;
  const cats = (summary.data?.categories ?? []).filter((c) => Number(c.totalSpend) > 0).sort((a, b) => Number(b.totalSpend) - Number(a.totalSpend));
  const top = cats[0];

  const buckets = trends.data?.buckets ?? [];
  const spendDelta =
    buckets.length >= 2 && Number(buckets[buckets.length - 2].totalSpend) > 0
      ? {
          pct:
            ((Number(buckets[buckets.length - 1].totalSpend) - Number(buckets[buckets.length - 2].totalSpend)) /
              Number(buckets[buckets.length - 2].totalSpend)) *
            100,
        }
      : null;

  // E10-S3: surface a single page-level stale indicator if any section is serving stale data.
  const sources = [alerts, recommendations, budgets, summary, trends, emis, recent, topDebits];
  const anyStale = sources.some((s) => s.isStale);
  const refreshAll = () => sources.forEach((s) => s.refresh());
  const heroLoading = summary.isLoading && summary.data === undefined;

  return (
    <>
      {anyStale && <StaleBanner onRetry={refreshAll} />}

      {/* Hero metrics */}
      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {heroLoading ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="rounded-[var(--radius)] border border-border bg-surface p-5 shadow-[var(--shadow-sm)]">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="mt-3 h-7 w-32" />
            </div>
          ))
        ) : (
          <>
            <StatTile
              label={`Spent · ${range.label}`}
              value={totalSpend}
              format={(n) => formatCurrency(n)}
              delta={spendDelta}
              accent="var(--color-danger)"
              icon={<TrendingDown className="size-4" />}
            />
            <StatTile label="Income" value={totalIncome} format={(n) => formatCurrency(n)} accent="var(--color-positive)" icon={<Wallet className="size-4" />} />
            <StatTile label="Net" value={net} format={(n) => formatCurrency(n)} accent={net >= 0 ? "var(--color-positive)" : "var(--color-danger)"} icon={<ArrowDownUp className="size-4" />} />
            {top ? (
              <StatTile
                label={`Top · ${top.categoryName}`}
                value={Number(top.totalSpend)}
                format={(n) => formatCurrency(n)}
                accent={categoryColor(top.categoryName, top.categoryId)}
              />
            ) : (
              <StatTile label="Top category" value={0} format={() => "—"} />
            )}
          </>
        )}
      </div>

      {/* Bento grid */}
        <TrendSection
          state={{ data: trends.data ? { buckets: trends.data.buckets } : undefined, error: trends.error, isLoading: trends.isLoading }}
          span={trendSpan}
          onSpanChange={setTrendSpan}
          spanDisabled={range.preset === "this-fy"}
        />

        <div className="lg:col-span-2 xl:col-span-2">
          <CategorySummarySection state={{ data: summary.data?.categories, error: summary.error, isLoading: summary.isLoading }} />
        </div>
      <div className="grid gap-5 lg:grid-cols-2 xl:grid-cols-3">
        <UpcomingEmisSection state={{ data: emis.data, error: emis.error, isLoading: emis.isLoading }} />
        <AlertsSection state={{ data: alerts.data?.data, error: alerts.error, isLoading: alerts.isLoading }} />
        <RecommendationsSection
          state={{ data: recommendations.data ? visibleRecs : undefined, error: recommendations.error, isLoading: recommendations.isLoading }}
          onDismiss={dismiss}
        />
        
      
        

        <TopSpendsSection state={{ data: topDebits.data?.data, error: topDebits.error, isLoading: topDebits.isLoading }} />
        <TopPayeesSection state={{ data: topDebits.data?.data, error: topDebits.error, isLoading: topDebits.isLoading }} />
        <RecentActivitySection state={{ data: recent.data?.data, error: recent.error, isLoading: recent.isLoading }} />

        <div className="lg:col-span-2 xl:col-span-3">
          <BudgetSection state={{ data: budgets.data, error: budgets.error, isLoading: budgets.isLoading }} categoryName={categoryName} />
        </div>
      </div>
    </>
  );
}
