"use client";

import { useState, type CSSProperties } from "react";
import { Table2, HelpCircle, type LucideIcon } from "lucide-react";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useDateRange, previousPeriod, pickTrendGranularity } from "@/lib/date-range";
import { categoryColor, categoryIcon } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";
import { EmptyState, ErrorState, StaleBanner } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Sparkline } from "@/components/charts/Sparkline";
import { cn } from "@/lib/cn";
import type { CategorySelection } from "@/components/transactions/CategorySummaryGrid";
import type { TrendBucket } from "@/components/charts/TrendLineChart";

interface CategoryTotalRow {
  categoryId: number | null;
  categoryName: string;
  totalSpend: number;
  totalIncome: number;
  transactionCount: number;
}

interface TrendsResponse {
  granularity: string;
  buckets: TrendBucket[];
}

interface Row {
  key: number | "uncategorized";
  name: string;
  icon: LucideIcon;
  color: string;
  amount: number;
  prevAmount: number;
  count: number;
  sharePct: number;
}

const TILE_COUNT_ESTIMATE = 13;

/**
 * The Analytics page's default (no category selected) view — a small-multiples grid, one tile
 * per category with spend, a delta vs. the previous period, and a sparkline trend, per
 * design-system.md §10.2's "small multiples" chart language for comparison/density surfaces.
 * Click a tile to drill into that category's full deep-dive (`CategoryDeepDive`).
 *
 * Deliberately not a repeat of the Dashboard's single combined donut/trend — every figure here
 * is scoped to one category, which the Dashboard's summary view never is.
 */
export function CategoryTrendGrid({
  selected,
  onSelect,
}: {
  selected: CategorySelection;
  onSelect: (value: CategorySelection) => void;
}) {
  const { range } = useDateRange();
  const prev = previousPeriod(range.from, range.to);
  const granularity = pickTrendGranularity(range.from, range.to);
  const { categories, isLoading: categoriesLoading } = useCategories();
  const [showTable, setShowTable] = useState(false);

  const now = useApi<CategoryTotalRow[]>(`/analytics/categories?from=${range.from}&to=${range.to}`);
  const previousTotals = useApi<CategoryTotalRow[]>(`/analytics/categories?from=${prev.from}&to=${prev.to}`);

  if (now.error && !now.data) {
    return <ErrorState message="Could not load category totals." onRetry={now.refresh} />;
  }

  const nowById = new Map((now.data ?? []).filter((r) => r.categoryId !== null).map((r) => [r.categoryId as number, r]));
  const prevById = new Map(
    (previousTotals.data ?? []).filter((r) => r.categoryId !== null).map((r) => [r.categoryId as number, r]),
  );
  const nowUncategorized = (now.data ?? []).find((r) => r.categoryId === null);
  const prevUncategorized = (previousTotals.data ?? []).find((r) => r.categoryId === null);
  const grandTotal = (now.data ?? []).reduce((sum, r) => sum + Number(r.totalSpend), 0);

  const rows: Row[] = categories
    .map((c): Row => {
      const row = nowById.get(c.id);
      const amount = Number(row?.totalSpend ?? 0);
      return {
        key: c.id,
        name: c.name,
        icon: categoryIcon(c.icon),
        color: categoryColor(c.name, c.id),
        amount,
        prevAmount: Number(prevById.get(c.id)?.totalSpend ?? 0),
        count: Number(row?.transactionCount ?? 0),
        sharePct: grandTotal > 0 ? (amount / grandTotal) * 100 : 0,
      };
    })
    .concat(
      nowUncategorized && Number(nowUncategorized.totalSpend) > 0
        ? [
            {
              key: "uncategorized" as const,
              name: "Uncategorized",
              icon: HelpCircle,
              color: categoryColor("Uncategorized"),
              amount: Number(nowUncategorized.totalSpend),
              prevAmount: Number(prevUncategorized?.totalSpend ?? 0),
              count: Number(nowUncategorized.transactionCount),
              sharePct: grandTotal > 0 ? (Number(nowUncategorized.totalSpend) / grandTotal) * 100 : 0,
            },
          ]
        : [],
    )
    .filter((r) => r.amount > 0)
    .sort((a, b) => b.amount - a.amount);

  const initialLoading = categoriesLoading || now.isLoading;
  const anyStale = now.isStale || previousTotals.isStale;
  const refreshAll = () => {
    now.refresh();
    previousTotals.refresh();
  };

  return (
    <div className="space-y-3">
      {anyStale && <StaleBanner onRetry={refreshAll} />}

      {!initialLoading && rows.length > 0 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-foreground-muted">Click a category to see its full trend</p>
          <Button variant="ghost" size="sm" onClick={() => setShowTable((s) => !s)}>
            <Table2 className="size-4" />
            {showTable ? "Hide table" : "View as table"}
          </Button>
        </div>
      )}

      {initialLoading ? (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(13rem,1fr))] gap-3">
          {Array.from({ length: TILE_COUNT_ESTIMATE }).map((_, i) => (
            <Skeleton key={i} className="h-[140px]" />
          ))}
        </div>
      ) : rows.length === 0 ? (
        <EmptyState message="No spending yet for this range — try a wider date range." />
      ) : showTable ? (
        <CategoryTable rows={rows} />
      ) : (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(13rem,1fr))] gap-3">
          {rows.map((row) => (
            <CategoryGridTile
              key={row.key}
              row={row}
              active={selected === row.key}
              onClick={() => onSelect(selected === row.key ? null : row.key)}
              granularity={granularity}
              from={range.from}
              to={range.to}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function CategoryGridTile({
  row,
  active,
  onClick,
  granularity,
  from,
  to,
}: {
  row: Row;
  active: boolean;
  onClick: () => void;
  granularity: string;
  from: string;
  to: string;
}) {
  const isRealCategory = typeof row.key === "number";
  const trend = useApi<TrendsResponse>(
    isRealCategory ? `/analytics/trends?category=${row.key}&granularity=${granularity}&from=${from}&to=${to}` : null,
  );
  const sparkValues = (trend.data?.buckets ?? []).map((b) => Number(b.totalSpend));
  const delta = row.prevAmount > 0 ? ((row.amount - row.prevAmount) / row.prevAmount) * 100 : null;
  const Icon = row.icon;

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "flex flex-col gap-2.5 rounded-[var(--radius)] border p-3.5 text-left transition-shadow",
        active ? "ring-2 ring-offset-2 ring-offset-surface" : "hover:shadow-[var(--shadow-sm)]",
      )}
      style={
        {
          borderColor: `color-mix(in srgb, ${row.color} 30%, transparent)`,
          backgroundColor: `color-mix(in srgb, ${row.color} 8%, transparent)`,
          ...(active ? { "--tw-ring-color": row.color } : {}),
        } as CSSProperties
      }
    >
      <div className="flex items-center gap-2">
        <span
          aria-hidden
          className="flex size-7 shrink-0 items-center justify-center rounded-full"
          style={{ backgroundColor: `color-mix(in srgb, ${row.color} 18%, transparent)`, color: row.color }}
        >
          <Icon className="size-4" />
        </span>
        <span className="truncate text-sm font-medium text-foreground">{row.name}</span>
      </div>

      <div className="flex items-end justify-between gap-2">
        <div>
          <div className="tnum text-lg font-semibold text-foreground">{formatCurrency(row.amount)}</div>
          {delta !== null && (
            <div
              className={cn(
                "text-xs font-medium",
                delta > 0 ? "text-[var(--color-danger)]" : delta < 0 ? "text-brand-700 dark:text-brand-300" : "text-foreground-subtle",
              )}
            >
              {delta > 0 ? "▲" : delta < 0 ? "▼" : ""} {Math.abs(delta).toFixed(0)}% vs prev.
            </div>
          )}
        </div>
        {isRealCategory && sparkValues.length >= 2 && <Sparkline values={sparkValues} />}
      </div>

      <div className="flex items-center justify-between text-xs text-foreground-subtle">
        <span>{row.sharePct.toFixed(0)}% of spend</span>
        <span>
          {row.count} {row.count === 1 ? "txn" : "txns"}
        </span>
      </div>
    </button>
  );
}

function CategoryTable({ rows }: { rows: Row[] }) {
  return (
    <div className="overflow-x-auto rounded-[var(--radius)] border border-border bg-surface p-4 shadow-[var(--shadow-sm)]">
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
          {rows.map((r) => (
            <tr key={r.key} className="border-b border-border last:border-0">
              <td className="py-2.5 pr-4">
                <span className="flex items-center gap-2">
                  <span aria-hidden className="size-2.5 rounded-full" style={{ backgroundColor: r.color }} />
                  <span className="text-foreground">{r.name}</span>
                </span>
              </td>
              <td className="tnum py-2.5 pr-4 text-right font-medium text-foreground">{formatCurrency(r.amount)}</td>
              <td className="tnum py-2.5 pr-4 text-right text-foreground-muted">{r.sharePct.toFixed(0)}%</td>
              <td className="tnum py-2.5 text-right text-foreground-muted">{r.count || "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
