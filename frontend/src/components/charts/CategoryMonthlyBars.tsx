"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency } from "@/lib/format";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { CHART, axisTick } from "@/lib/chart-theme";
import type { TrendBucket } from "@/components/charts/TrendLineChart";

/**
 * One category's last several calendar months, side by side — easier to compare specific
 * months at a glance than reading them off a single trend line. Anchored to the currently
 * viewed month (not always "today"), so it shifts along with the Analytics month-stepper.
 */
export function CategoryMonthlyBars({ buckets, color }: { buckets: TrendBucket[]; color: string }) {
  const data = buckets.map((b) => ({
    month: new Date(b.bucketStart).toLocaleDateString("en-IN", { month: "short" }),
    spend: Number(b.totalSpend),
  }));

  return (
    <div data-testid="category-monthly-bars">
      <p className="sr-only">Monthly spend across {data.length} months.</p>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={data} margin={{ top: 4, right: 8, bottom: 4, left: 4 }}>
          <CartesianGrid stroke={CHART.grid} vertical={false} strokeDasharray="3 3" />
          <XAxis dataKey="month" tick={axisTick} tickLine={false} axisLine={false} />
          <YAxis
            tick={axisTick}
            tickLine={false}
            axisLine={false}
            width={56}
            tickFormatter={(v) => formatCurrency(Number(v))}
          />
          <Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--color-surface-muted)" }} />
          <Bar dataKey="spend" radius={[3, 3, 0, 0]} maxBarSize={28} fill={color} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
