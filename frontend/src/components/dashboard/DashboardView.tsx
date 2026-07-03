"use client";

import { useMemo, useState } from "react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
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
import { StaleBanner } from "@/components/shared/ui";
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

function ymd(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** A trailing ~6-calendar-month window ending today, for the trend + category summary. */
function defaultRange(): { from: string; to: string } {
  const now = new Date();
  const to = ymd(now);
  const from = ymd(new Date(now.getFullYear(), now.getMonth() - 5, 1));
  return { from, to };
}

export function DashboardView() {
  const { from, to } = useMemo(() => defaultRange(), []);
  const { categoryName } = useCategories();

  const alerts = useApi<AlertListResponse>("/alerts?limit=20");
  const recommendations = useApi<Recommendation[]>("/recommendations");
  const budgets = useApi<BudgetProgress[]>("/budgets/progress");
  const summary = useApi<SummaryResponse>(`/analytics/summary?from=${from}&to=${to}`);
  const trends = useApi<TrendsResponse>(`/analytics/trends?granularity=month&from=${from}&to=${to}`);

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

  // E10-S3: when the backend becomes unreachable mid-session, SWR keeps the last-fetched
  // data for each section (each section renders it, not an error, as long as data exists).
  // We surface a single page-level stale indicator if any section is now serving stale
  // data, and let the user retry all sections at once.
  const sources = [alerts, recommendations, budgets, summary, trends];
  const anyStale = sources.some((s) => s.isStale);
  const refreshAll = () => sources.forEach((s) => s.refresh());

  return (
    <>
      {anyStale && <StaleBanner onRetry={refreshAll} />}
      <div className="grid gap-5 lg:grid-cols-2">
      <AlertsSection
        state={{ data: alerts.data?.data, error: alerts.error, isLoading: alerts.isLoading }}
      />
      <RecommendationsSection
        state={{
          data: recommendations.data ? visibleRecs : undefined,
          error: recommendations.error,
          isLoading: recommendations.isLoading,
        }}
        onDismiss={dismiss}
      />
      <BudgetSection
        state={{ data: budgets.data, error: budgets.error, isLoading: budgets.isLoading }}
        categoryName={categoryName}
      />
      <CategorySummarySection
        state={{ data: summary.data?.categories, error: summary.error, isLoading: summary.isLoading }}
      />
      <div className="lg:col-span-2">
        <TrendSection
          state={{
            data: trends.data ? { buckets: trends.data.buckets } : undefined,
            error: trends.error,
            isLoading: trends.isLoading,
          }}
        />
      </div>
      </div>
    </>
  );
}
