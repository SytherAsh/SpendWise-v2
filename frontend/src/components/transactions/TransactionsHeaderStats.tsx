"use client";

import { TrendingDown, TrendingUp } from "lucide-react";
import { useApi } from "@/lib/useApi";
import { useDateRange } from "@/lib/date-range";
import { formatCurrency } from "@/lib/format";
import { MiniStat } from "@/components/shared/MiniStat";
import { Skeleton } from "@/components/ui/skeleton";

interface SummaryOverall {
  totalSpend: number;
  totalIncome: number;
}

/**
 * Header-right hero figures for the Transactions page — money spent and money received
 * for the active date range, the first thing a user's eye should land on. Shares the
 * `/analytics/summary` SWR key with CategorySummaryGrid, so this adds no extra request.
 */
export function TransactionsHeaderStats() {
  const { range } = useDateRange();
  const { data, isLoading } = useApi<SummaryOverall>(`/analytics/summary?from=${range.from}&to=${range.to}`);

  if (isLoading && !data) {
    return (
      <div className="flex flex-wrap gap-3">
        <Skeleton className="h-[60px] w-[9.5rem]" />
        <Skeleton className="h-[60px] w-[9.5rem]" />
      </div>
    );
  }
  if (!data) return null;

  return (
    <div data-testid="transactions-header-stats" className="flex flex-wrap gap-3">
      <MiniStat
        label="Money spent"
        value={data.totalSpend}
        format={formatCurrency}
        icon={<TrendingDown className="size-4" />}
        tone="negative"
      />
      <MiniStat
        label="Money received"
        value={data.totalIncome}
        format={formatCurrency}
        icon={<TrendingUp className="size-4" />}
        tone="positive"
      />
    </div>
  );
}
