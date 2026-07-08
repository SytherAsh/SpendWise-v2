"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { CategoryTotal } from "@/components/charts/CategoryBarChart";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { categoryColor } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";

/**
 * Category breakdown as a donut paired with a ranked list. The ranked list is the
 * mandatory secondary encoding for the 12-color palette — every slice's identity is
 * also carried by a labeled row (color dot + name + amount + share), so category is
 * never identified by hue alone.
 */
export function CategoryDonut({ categories }: { categories: CategoryTotal[] }) {
  const rows = categories
    .map((c) => ({ name: c.categoryName, id: c.categoryId, spend: Number(c.totalSpend) }))
    .filter((c) => c.spend > 0)
    .sort((a, b) => b.spend - a.spend);

  const total = rows.reduce((sum, r) => sum + r.spend, 0);
  const top = rows.slice(0, 6);

  return (
    <div data-testid="category-donut" className="flex flex-col items-center gap-6 sm:flex-row">
      <p className="sr-only">Spending by category across {rows.length} categories.</p>

      <div className="relative h-[180px] w-[180px] shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={rows}
              dataKey="spend"
              nameKey="name"
              innerRadius={58}
              outerRadius={84}
              paddingAngle={2}
              stroke="var(--color-surface)"
              strokeWidth={2}
            >
              {rows.map((r) => (
                <Cell key={r.id} fill={categoryColor(r.name, r.id)} />
              ))}
            </Pie>
            <Tooltip content={<ChartTooltip />} />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-xs text-foreground-subtle">Total</span>
          <span className="tnum text-lg font-semibold text-foreground">{formatCurrency(total)}</span>
        </div>
      </div>

      <ul className="w-full min-w-0 flex-1 space-y-2">
        {top.map((r) => {
          const pct = total > 0 ? Math.round((r.spend / total) * 100) : 0;
          return (
            <li key={r.id} className="flex items-center gap-2.5 text-sm">
              <span aria-hidden className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: categoryColor(r.name, r.id) }} />
              <span className="min-w-0 flex-1 truncate text-foreground">{r.name}</span>
              <span className="tnum font-medium text-foreground">{formatCurrency(r.spend)}</span>
              <span className="tnum w-9 shrink-0 text-right text-xs text-foreground-subtle">{pct}%</span>
            </li>
          );
        })}
        {rows.length > top.length && (
          <li className="pl-5 text-xs text-foreground-subtle">
            +{rows.length - top.length} more {rows.length - top.length === 1 ? "category" : "categories"}
          </li>
        )}
      </ul>
    </div>
  );
}
