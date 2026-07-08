"use client";

import { Bar, BarChart, Cell, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency } from "@/lib/format";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { categoryColor } from "@/lib/categories";
import { CHART, axisTick } from "@/lib/chart-theme";

export interface CategoryTotal {
  categoryId: number;
  categoryName: string;
  totalSpend: number;
}

/** Horizontal ranking of spend per category (from GET /analytics/summary). Each bar wears
 *  its category color; the axis label provides the always-present secondary encoding. */
export function CategoryBarChart({ categories }: { categories: CategoryTotal[] }) {
  const data = categories
    .map((c) => ({ name: c.categoryName, id: c.categoryId, spend: Number(c.totalSpend) }))
    .filter((c) => c.spend > 0)
    .sort((a, b) => b.spend - a.spend);

  return (
    <div data-testid="category-bar-chart">
      <p className="sr-only">Spending by category across {data.length} categories.</p>
      <ResponsiveContainer width="100%" height={Math.max(220, data.length * 40)}>
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid stroke={CHART.grid} horizontal={false} />
          <XAxis type="number" tick={axisTick} tickLine={false} axisLine={false} tickFormatter={(v) => formatCurrency(Number(v))} />
          <YAxis type="category" dataKey="name" tick={axisTick} tickLine={false} axisLine={false} width={110} />
          <Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--color-surface-muted)" }} />
          <Bar dataKey="spend" radius={[0, 4, 4, 0]} maxBarSize={22}>
            {data.map((d) => (
              <Cell key={d.id} fill={categoryColor(d.name, d.id)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
