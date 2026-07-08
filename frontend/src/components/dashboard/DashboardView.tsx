"use client";

import { useState } from "react";
import { TrendingDown, Wallet, ArrowDownUp } from "lucide-react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useDateRange } from "@/lib/date-range";
import {
  AlertsSection,
  BudgetSection,
  CategorySummarySection,
  RecommendationsSection,
  TrendSection,
  type Alert,
  type BudgetProgress,
  type Recommendation,
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

export function DashboardView() {
  const { range } = useDateRange();
  const { categoryName } = useCategories();

  const alerts = useApi<AlertListResponse>("/alerts?limit=20");
  const recommendations = useApi<Recommendation[]>("/recommendations");
  const budgets = useApi<BudgetProgress[]>("/budgets/progress");
  const summary = useApi<SummaryResponse>(`/analytics/summary?from=${range.from}&to=${range.to}`);
  const trends = useApi<TrendsResponse>(`/analytics/trends?granularity=month&from=${range.from}&to=${range.to}`);

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
  const sources = [alerts, recommendations, budgets, summary, trends];
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
            <StatTile label={`Spent · ${range.label}`} value={totalSpend} format={(n) => formatCurrency(n)} delta={spendDelta} icon={<TrendingDown className="size-4" />} />
            <StatTile label="Income" value={totalIncome} format={(n) => formatCurrency(n)} icon={<Wallet className="size-4" />} />
            <StatTile label="Net" value={net} format={(n) => formatCurrency(n)} accent={net >= 0 ? "var(--color-brand-700)" : "var(--color-danger)"} icon={<ArrowDownUp className="size-4" />} />
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
      <div className="grid gap-5 lg:grid-cols-2 xl:grid-cols-3">
        <TrendSection
          state={{ data: trends.data ? { buckets: trends.data.buckets } : undefined, error: trends.error, isLoading: trends.isLoading }}
        />
        <AlertsSection state={{ data: alerts.data?.data, error: alerts.error, isLoading: alerts.isLoading }} />

        <div className="lg:col-span-1 xl:col-span-2">
          <CategorySummarySection state={{ data: summary.data?.categories, error: summary.error, isLoading: summary.isLoading }} />
        </div>
        <RecommendationsSection
          state={{ data: recommendations.data ? visibleRecs : undefined, error: recommendations.error, isLoading: recommendations.isLoading }}
          onDismiss={dismiss}
        />

        <div className="lg:col-span-2 xl:col-span-3">
          <BudgetSection state={{ data: budgets.data, error: budgets.error, isLoading: budgets.isLoading }} categoryName={categoryName} />
        </div>
      </div>
    </>
  );
}
