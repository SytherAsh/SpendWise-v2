"use client";

import { Wallet } from "lucide-react";
import { useApi } from "@/lib/useApi";
import { formatCurrency } from "@/lib/format";
import { MiniStat } from "@/components/shared/MiniStat";
import { Skeleton } from "@/components/ui/skeleton";

interface BudgetProgress {
  categoryId: number;
  monthlyLimit: number;
  spent: number;
  percentSpent: number;
}

/**
 * Header-right hero figure for Planning's Budgets tab — the sum of every category's
 * monthly limit, the first thing a user's eye should land on. Shares the
 * `/budgets/progress` SWR key with BudgetManager, so this adds no extra request.
 */
export function BudgetTotalStat() {
  const { data, isLoading } = useApi<BudgetProgress[]>("/budgets/progress");

  if (isLoading && !data) {
    return <Skeleton className="h-[60px] w-[13rem]" />;
  }
  if (!data) return null;

  const total = data.reduce((sum, p) => sum + p.monthlyLimit, 0);

  return (
    <div data-testid="budget-total-stat">
      <MiniStat label="Total budget this month" value={total} format={formatCurrency} icon={<Wallet className="size-4" />} />
    </div>
  );
}
