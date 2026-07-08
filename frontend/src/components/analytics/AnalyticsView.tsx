"use client";

import { TrendingDown, Wallet, ArrowDownUp, Receipt } from "lucide-react";
import { useApi } from "@/lib/useApi";
import { useDateRange } from "@/lib/date-range";
import { Card, StaleBanner, EmptyState, ErrorState } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { StatTile } from "@/components/shared/StatTile";
import { CategoryDonut } from "@/components/charts/CategoryDonut";
import { CategoryBarChart, type CategoryTotal } from "@/components/charts/CategoryBarChart";
import { TrendLineChart, type TrendBucket } from "@/components/charts/TrendLineChart";
import { categoryColor } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";

interface SummaryResponse {
  totalSpend: number;
  totalIncome: number;
  categories: (CategoryTotal & { totalIncome?: number; transactionCount?: number })[];
}
interface TrendsResponse {
  granularity: string;
  buckets: TrendBucket[];
}
interface PeriodTotals {
  from: string;
  to: string;
  totalSpend: number;
  totalIncome: number;
}
interface ComparisonResponse {
  granularity: string;
  current: PeriodTotals;
  previous: PeriodTotals;
}

function ChartCard({ title, children, className }: { title: string; children: React.ReactNode; className?: string }) {
  return (
    <Card className={className}>
      <h2 className="mb-4 text-sm font-semibold text-foreground">{title}</h2>
      {children}
    </Card>
  );
}

export function AnalyticsView() {
  const { range } = useDateRange();
  const summary = useApi<SummaryResponse>(`/analytics/summary?from=${range.from}&to=${range.to}`);
  const trends = useApi<TrendsResponse>(`/analytics/trends?granularity=month&from=${range.from}&to=${range.to}`);
  const comparison = useApi<ComparisonResponse>(`/analytics/comparison?granularity=month`);

  const sources = [summary, trends, comparison];
  const anyStale = sources.some((s) => s.isStale);
  const refreshAll = () => sources.forEach((s) => s.refresh());

  const totalSpend = summary.data?.totalSpend ?? 0;
  const totalIncome = summary.data?.totalIncome ?? 0;
  const net = totalIncome - totalSpend;
  const cats = (summary.data?.categories ?? [])
    .map((c) => ({ ...c, totalSpend: Number(c.totalSpend) }))
    .filter((c) => c.totalSpend > 0)
    .sort((a, b) => b.totalSpend - a.totalSpend);
  const catTotal = cats.reduce((s, c) => s + c.totalSpend, 0);
  const txnCount = cats.reduce((s, c) => s + (Number(c.transactionCount) || 0), 0);

  const cur = comparison.data?.current;
  const prev = comparison.data?.previous;
  const spendDelta =
    cur && prev && Number(prev.totalSpend) > 0
      ? { pct: ((Number(cur.totalSpend) - Number(prev.totalSpend)) / Number(prev.totalSpend)) * 100 }
      : null;

  const heroLoading = summary.isLoading && summary.data === undefined;

  return (
    <>
      {anyStale && <StaleBanner onRetry={refreshAll} />}

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
            <StatTile label={`Spent · ${range.label}`} value={totalSpend} format={formatCurrency} delta={spendDelta} icon={<TrendingDown className="size-4" />} />
            <StatTile label="Income" value={totalIncome} format={formatCurrency} icon={<Wallet className="size-4" />} />
            <StatTile label="Net" value={net} format={formatCurrency} accent={net >= 0 ? "var(--color-brand-700)" : "var(--color-danger)"} icon={<ArrowDownUp className="size-4" />} />
            <StatTile label="Transactions" value={txnCount} format={(n) => String(Math.round(n))} icon={<Receipt className="size-4" />} />
          </>
        )}
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <ChartCard title="Spending trend" className="lg:col-span-3">
          {trends.error && !trends.data ? (
            <ErrorState message="Could not load the trend." />
          ) : trends.data && trends.data.buckets.length > 0 ? (
            <TrendLineChart buckets={trends.data.buckets} height={300} />
          ) : trends.isLoading ? (
            <Skeleton className="h-[300px] w-full" />
          ) : (
            <EmptyState message="Not enough history to plot a trend yet." />
          )}
        </ChartCard>

        <ChartCard title="Category share" className="lg:col-span-1">
          {cats.length > 0 ? <CategoryDonut categories={cats} /> : <EmptyState message="No spending yet." />}
        </ChartCard>

        <ChartCard title="Spending by category" className="lg:col-span-2">
          {cats.length > 0 ? <CategoryBarChart categories={cats} /> : <EmptyState message="No spending yet." />}
        </ChartCard>

        <ChartCard title="Breakdown" className="lg:col-span-3">
          {cats.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs font-medium text-foreground-subtle">
                    <th className="py-2 pr-4 font-medium">Category</th>
                    <th className="py-2 pr-4 text-right font-medium">Spent</th>
                    <th className="py-2 pr-4 text-right font-medium">Share</th>
                    <th className="py-2 text-right font-medium">Transactions</th>
                  </tr>
                </thead>
                <tbody>
                  {cats.map((c) => (
                    <tr key={c.categoryId} className="border-b border-border last:border-0">
                      <td className="py-2.5 pr-4">
                        <span className="flex items-center gap-2">
                          <span aria-hidden className="size-2.5 rounded-full" style={{ backgroundColor: categoryColor(c.categoryName, c.categoryId) }} />
                          <span className="text-foreground">{c.categoryName}</span>
                        </span>
                      </td>
                      <td className="tnum py-2.5 pr-4 text-right font-medium text-foreground">{formatCurrency(c.totalSpend)}</td>
                      <td className="tnum py-2.5 pr-4 text-right text-foreground-muted">{catTotal > 0 ? Math.round((c.totalSpend / catTotal) * 100) : 0}%</td>
                      <td className="tnum py-2.5 text-right text-foreground-muted">{Number(c.transactionCount) || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState message="No spending to break down yet." />
          )}
        </ChartCard>
      </div>
    </>
  );
}
