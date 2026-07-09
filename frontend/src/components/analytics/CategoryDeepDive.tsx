"use client";

import Link from "next/link";
import { ArrowLeft, ArrowRight, HelpCircle, Receipt, Sparkles, TrendingDown, type LucideIcon } from "lucide-react";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useDateRange, previousPeriod, pickTrendGranularity, trailingMonths } from "@/lib/date-range";
import { categoryColor, categoryIcon } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";
import { Card, EmptyState, ErrorState } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { MiniStat } from "@/components/shared/MiniStat";
import { TrendLineChart, type TrendBucket } from "@/components/charts/TrendLineChart";
import { DailySpendBars } from "@/components/charts/DailySpendBars";
import { CategoryMonthlyBars } from "@/components/charts/CategoryMonthlyBars";
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
interface MerchantGroup {
  key: string;
  label: string;
  total: number;
  count: number;
}

const formatPercent = (n: number) => `${n.toFixed(0)}%`;

/** This category's spend as a % of everyone's spend, per matching bucket — the denominator (`overallBuckets`) comes from the same `/analytics/trends` call with no `category` param. Joined by `bucketStart` rather than array index, so a mismatched or out-of-order response still lines up correctly. */
function computeShareBuckets(categoryBuckets: TrendBucket[] | undefined, overallBuckets: TrendBucket[] | undefined): TrendBucket[] {
  if (!categoryBuckets || !overallBuckets) return [];
  const overallByStart = new Map(overallBuckets.map((b) => [b.bucketStart, Number(b.totalSpend)]));
  return categoryBuckets.map((b) => {
    const overallTotal = overallByStart.get(b.bucketStart) ?? 0;
    return { bucketStart: b.bucketStart, totalSpend: overallTotal > 0 ? (Number(b.totalSpend) / overallTotal) * 100 : 0 };
  });
}

/** Same person/merchant across several transactions reads as one row (total spend, txn count) — not a repeated line per transaction; that per-transaction detail already lives on the Transactions page. */
function groupByCounterparty(transactions: Transaction[]): MerchantGroup[] {
  const groups = new Map<string, MerchantGroup>();
  for (const t of transactions) {
    const label = t.recipientName ?? t.upiId ?? t.note ?? "Other";
    const existing = groups.get(label);
    const amount = Math.abs(Number(t.amount));
    if (existing) {
      existing.total += amount;
      existing.count += 1;
    } else {
      groups.set(label, { key: label, label, total: amount, count: 1 });
    }
  }
  return Array.from(groups.values()).sort((a, b) => b.total - a.total);
}

/**
 * The Analytics page's category deep-dive — everything scoped to one category: its trend,
 * this-period-vs-previous, spending pace, who you spend it with, and any AI recommendations
 * tied to it. Reached by clicking a tile in `CategoryTrendGrid`.
 *
 * `categoryId === "uncategorized"` gets a reduced view (no trend/pattern/share/monthly charts —
 * `/analytics/trends` has no uncategorized filter — and no recommendations, since that concept
 * doesn't apply to spend with no category) but still totals, comparison, and counterparties.
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
  const sixMo = trailingMonths(range.to, 6);
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
  // Same granularity/range as `trend` but with no category filter — the denominator for "this
  // category's share of total spend, over time" below.
  const overallTrend = useApi<TrendsResponse>(
    isReal ? `/analytics/trends?granularity=${granularity}&from=${range.from}&to=${range.to}` : null,
  );
  // Always the trailing 6 calendar months ending at the currently viewed month, regardless of
  // the main range's own span — a stable comparison strip that shifts with the month-stepper.
  const monthlyTrend = useApi<TrendsResponse>(
    isReal ? `/analytics/trends?category=${categoryId}&granularity=month&from=${sixMo.from}&to=${sixMo.to}` : null,
  );
  const recommendations = useApi<Recommendation[]>(isReal ? "/recommendations" : null);
  // Bounded read (most recent 200 in range, date-desc) grouped client-side — not paginated
  // "top N by amount" like the old version, since grouping needs the whole set to sum
  // correctly per counterparty, not just the individually-largest transactions.
  const transactionsResp = useApi<TransactionListResponse>(
    `/transactions?category=${categoryParam}&from=${range.from}&to=${range.to}&limit=200`,
  );

  const nowRow = (categoriesNow.data ?? []).find((r) => (isReal ? r.categoryId === categoryId : r.categoryId === null));
  const prevRow = (categoriesPrev.data ?? []).find((r) => (isReal ? r.categoryId === categoryId : r.categoryId === null));
  const currentTotal = Number(nowRow?.totalSpend ?? 0);
  const previousTotal = Number(prevRow?.totalSpend ?? 0);
  const delta = previousTotal > 0 ? ((currentTotal - previousTotal) / previousTotal) * 100 : null;
  const txnCount = Number(nowRow?.transactionCount ?? 0);
  const avgPerTxn = txnCount > 0 ? currentTotal / txnCount : 0;

  const categoryRecs = isReal ? (recommendations.data ?? []).filter((r) => r.categoryId === categoryId) : [];
  const merchantGroups = groupByCounterparty(transactionsResp.data?.data ?? []).slice(0, 6);
  const shareBuckets = computeShareBuckets(trend.data?.buckets, overallTrend.data?.buckets);

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
          <MiniStat label="Spent" value={currentTotal} format={formatCurrency} icon={<TrendingDown className="size-4" />}tone="negative" />
          {txnCount > 0 && (
            <MiniStat label={`Avg · ${txnCount} txns`} value={avgPerTxn} format={formatCurrency} icon={<Receipt className="size-4" />} />
          )}
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

      {isReal && granularity === "day" && (
        <Card>
          <h3 className="mb-4 text-sm font-semibold text-foreground">Daily pattern</h3>
          {trend.data && trend.data.buckets.length > 0 ? (
            <DailySpendBars buckets={trend.data.buckets} color={color} />
          ) : trend.isLoading ? (
            <Skeleton className="h-[180px] w-full" />
          ) : (
            <EmptyState message="No spending yet this month." />
          )}
        </Card>
      )}

      {isReal && (
        <div className="grid gap-5 lg:grid-cols-2">
          <Card>
            <h3 className="mb-4 text-sm font-semibold text-foreground">Share of total spend</h3>
            {overallTrend.error && !overallTrend.data ? (
              <ErrorState message="Could not load this chart." onRetry={overallTrend.refresh} />
            ) : shareBuckets.length > 0 ? (
              <TrendLineChart buckets={shareBuckets} height={180} variant="crisp" formatValue={formatPercent} />
            ) : trend.isLoading || overallTrend.isLoading ? (
              <Skeleton className="h-[180px] w-full" />
            ) : (
              <EmptyState message="Not enough history yet." />
            )}
          </Card>

          <Card>
            <h3 className="mb-4 text-sm font-semibold text-foreground">Last 6 months</h3>
            {monthlyTrend.error && !monthlyTrend.data ? (
              <ErrorState message="Could not load this chart." onRetry={monthlyTrend.refresh} />
            ) : monthlyTrend.data && monthlyTrend.data.buckets.length > 0 ? (
              <CategoryMonthlyBars buckets={monthlyTrend.data.buckets} color={color} />
            ) : monthlyTrend.isLoading ? (
              <Skeleton className="h-[180px] w-full" />
            ) : (
              <EmptyState message="No spending in this window yet." />
            )}
          </Card>
        </div>
      )}

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-foreground">Who you spent it with</h3>
          <Button asChild variant="ghost" size="sm">
            <Link href={`/transactions?category=${categoryParam}`}>
              View all
              <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
        {transactionsResp.isLoading && !transactionsResp.data ? (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-9 w-full" />
            ))}
          </div>
        ) : merchantGroups.length > 0 ? (
          <ul className="space-y-2.5">
            {merchantGroups.map((g) => (
              <li key={g.key} className="flex items-center gap-3 text-sm">
                <span className="min-w-0 flex-1 truncate text-foreground">{g.label}</span>
                <span className="tnum shrink-0 text-xs text-foreground-subtle">
                  {g.count} {g.count === 1 ? "txn" : "txns"}
                </span>
                <span className="tnum shrink-0 font-medium text-foreground">{formatCurrency(g.total)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState message="No transactions in this range." />
        )}
      </Card>

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
