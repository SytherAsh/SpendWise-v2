"use client";

import Link from "next/link";
import { ArrowLeft, ArrowRight, HelpCircle, Sparkles, TrendingDown, type LucideIcon } from "lucide-react";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useDateRange, previousPeriod, pickTrendGranularity } from "@/lib/date-range";
import { categoryColor, categoryIcon } from "@/lib/categories";
import { formatCurrency, formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, ProgressBar } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { MiniStat } from "@/components/shared/MiniStat";
import { TrendLineChart, type TrendBucket } from "@/components/charts/TrendLineChart";
import { cn } from "@/lib/cn";
import type { CategorySelection } from "@/components/transactions/CategorySummaryGrid";

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
interface BudgetProgress {
  categoryId: number;
  monthlyLimit: number;
  spent: number;
  percentSpent: number;
}
interface Recommendation {
  id: string;
  categoryId: number | null;
  text: string;
  priority: string;
}
interface Transaction {
  id: string;
  transactionDate: string;
  amount: number;
  recipientName: string | null;
  upiId: string | null;
  note: string | null;
}
interface TransactionListResponse {
  data: Transaction[];
}

/**
 * The Analytics page's category deep-dive — everything scoped to one category: its trend,
 * this-period-vs-previous, budget status, biggest transactions, and any AI recommendations
 * tied to it. Reached by clicking a tile in `CategoryTrendGrid`.
 *
 * `categoryId === "uncategorized"` gets a reduced view (no trend — `/analytics/trends` has no
 * uncategorized filter; no budget or recommendations — neither concept applies to spend with no
 * category) but still totals, comparison, and its biggest transactions.
 */
export function CategoryDeepDive({
  categoryId,
  onClose,
}: {
  categoryId: Exclude<CategorySelection, null>;
  onClose: () => void;
}) {
  const { range } = useDateRange();
  const { categories } = useCategories();
  const prev = previousPeriod(range.from, range.to);
  const granularity = pickTrendGranularity(range.from, range.to);
  const isReal = typeof categoryId === "number";
  const categoryParam = isReal ? String(categoryId) : "uncategorized";

  const category = isReal ? categories.find((c) => c.id === categoryId) : undefined;
  const name = isReal ? (category?.name ?? `Category ${categoryId}`) : "Uncategorized";
  const color = categoryColor(name, isReal ? categoryId : undefined);
  const Icon = isReal ? categoryIcon(category?.icon) : HelpCircle;

  // Same SWR keys CategoryTrendGrid already fetches for its tiles — cached, not a new request.
  const categoriesNow = useApi<CategoryTotalRow[]>(`/analytics/categories?from=${range.from}&to=${range.to}`);
  const categoriesPrev = useApi<CategoryTotalRow[]>(`/analytics/categories?from=${prev.from}&to=${prev.to}`);
  const trend = useApi<TrendsResponse>(
    isReal ? `/analytics/trends?category=${categoryId}&granularity=${granularity}&from=${range.from}&to=${range.to}` : null,
  );
  const budgets = useApi<BudgetProgress[]>(isReal ? "/budgets/progress" : null);
  const recommendations = useApi<Recommendation[]>(isReal ? "/recommendations" : null);
  const topTransactions = useApi<TransactionListResponse>(
    `/transactions?category=${categoryParam}&from=${range.from}&to=${range.to}&limit=5&sort=amount_desc`,
  );

  const nowRow = (categoriesNow.data ?? []).find((r) => (isReal ? r.categoryId === categoryId : r.categoryId === null));
  const prevRow = (categoriesPrev.data ?? []).find((r) => (isReal ? r.categoryId === categoryId : r.categoryId === null));
  const currentTotal = Number(nowRow?.totalSpend ?? 0);
  const previousTotal = Number(prevRow?.totalSpend ?? 0);
  const delta = previousTotal > 0 ? ((currentTotal - previousTotal) / previousTotal) * 100 : null;

  const budgetRow = isReal ? (budgets.data ?? []).find((b) => b.categoryId === categoryId) : undefined;
  const categoryRecs = isReal ? (recommendations.data ?? []).filter((r) => r.categoryId === categoryId) : [];

  return (
    <div className="space-y-5">
      <Button variant="ghost" size="sm" onClick={onClose}>
        <ArrowLeft className="size-4" />
        All categories
      </Button>

      <div className="flex flex-wrap items-center gap-4">
        <CategoryIconBadge Icon={Icon} color={color} />
        <div>
          <h2 className="font-display text-xl font-semibold text-foreground">{name}</h2>
          <p className="text-sm text-foreground-muted">{range.label}</p>
        </div>
        <div className="ml-auto flex flex-wrap items-center gap-3">
          {delta !== null && (
            <span
              className={cn(
                "text-sm font-medium",
                delta > 0 ? "text-[var(--color-danger)]" : "text-brand-700 dark:text-brand-300",
              )}
            >
              {delta > 0 ? "▲" : "▼"} {Math.abs(delta).toFixed(0)}% vs previous period
            </span>
          )}
          <MiniStat label="Spent" value={currentTotal} format={formatCurrency} icon={<TrendingDown className="size-4" />} />
        </div>
      </div>

      {isReal ? (
        <Card>
          <h3 className="mb-4 text-sm font-semibold text-foreground">Trend</h3>
          {trend.error && !trend.data ? (
            <ErrorState message="Could not load the trend." onRetry={trend.refresh} />
          ) : trend.data && trend.data.buckets.length > 0 ? (
            <TrendLineChart buckets={trend.data.buckets} height={260} variant="crisp" />
          ) : trend.isLoading ? (
            <Skeleton className="h-[260px] w-full" />
          ) : (
            <EmptyState message="Not enough history to plot a trend yet." />
          )}
        </Card>
      ) : (
        <Card>
          <p className="text-sm text-foreground-muted">
            Uncategorized transactions don&apos;t have a category-level trend. Assign a category to
            a transaction below to start tracking it.
          </p>
        </Card>
      )}

      <div className="grid gap-5 lg:grid-cols-2">
        {isReal && (
          <Card>
            <h3 className="mb-4 text-sm font-semibold text-foreground">Budget</h3>
            {budgets.isLoading && !budgets.data ? (
              <Skeleton className="h-14 w-full" />
            ) : budgetRow ? (
              <div>
                <div className="mb-1.5 flex items-center justify-between text-sm">
                  <span className="text-foreground-muted">Spent this month</span>
                  <span className="mono text-foreground">
                    {formatCurrency(budgetRow.spent)} / {formatCurrency(budgetRow.monthlyLimit)}
                  </span>
                </div>
                <ProgressBar
                  ratio={budgetRow.monthlyLimit > 0 ? budgetRow.spent / budgetRow.monthlyLimit : 0}
                  danger={budgetRow.percentSpent > 100}
                />
              </div>
            ) : (
              <EmptyState
                message="No budget set for this category yet."
                action={
                  <Button asChild variant="secondary" size="sm">
                    <Link href="/planning">Set a budget</Link>
                  </Button>
                }
              />
            )}
          </Card>
        )}

        <Card className={!isReal ? "lg:col-span-2" : undefined}>
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-foreground">Biggest transactions</h3>
            <Button asChild variant="ghost" size="sm">
              <Link href={`/transactions?category=${categoryParam}`}>
                View all
                <ArrowRight className="size-4" />
              </Link>
            </Button>
          </div>
          {topTransactions.isLoading && !topTransactions.data ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-9 w-full" />
              ))}
            </div>
          ) : topTransactions.data && topTransactions.data.data.length > 0 ? (
            <ul className="space-y-2.5">
              {topTransactions.data.data.map((t) => (
                <li key={t.id} className="flex items-center gap-3 text-sm">
                  <span className="min-w-0 flex-1 truncate text-foreground">
                    {t.recipientName ?? t.upiId ?? t.note ?? "Transaction"}
                  </span>
                  <span className="tnum shrink-0 text-foreground-subtle">{formatDate(t.transactionDate)}</span>
                  <span className="tnum shrink-0 font-medium text-foreground">{formatCurrency(Math.abs(t.amount))}</span>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState message="No transactions in this range." />
          )}
        </Card>
      </div>

      {isReal && categoryRecs.length > 0 && (
        <Card>
          <h3 className="mb-4 flex items-center gap-2 text-sm font-semibold text-foreground">
            <Sparkles className="size-4 text-brand-600" aria-hidden />
            Recommendations
          </h3>
          <ul className="space-y-2">
            {categoryRecs.map((r) => (
              <li key={r.id} className="rounded-[var(--radius-sm)] border border-border bg-surface-muted p-3 text-sm text-foreground">
                {r.text}
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}

/** `Icon` arrives as a prop (never resolved inline in JSX) — react-hooks/static-components flags a component type computed and rendered in the same scope. */
function CategoryIconBadge({ Icon, color }: { Icon: LucideIcon; color: string }) {
  return (
    <span
      aria-hidden
      className="grid size-11 shrink-0 place-items-center rounded-[var(--radius)]"
      style={{ backgroundColor: `color-mix(in srgb, ${color} 18%, transparent)`, color }}
    >
      <Icon className="size-5" />
    </span>
  );
}
