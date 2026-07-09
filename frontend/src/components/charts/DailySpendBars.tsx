"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency } from "@/lib/format";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { CHART, axisTick } from "@/lib/chart-theme";
import type { TrendBucket } from "@/components/charts/TrendLineChart";

/**
 * One category's day-of-month spending pattern — bars for each day within a single month.
 * Pairs with the Analytics month-stepper: only meaningful once the selected range has
 * narrowed to ~one month (day-granularity buckets), so the caller only renders this when
 * `pickTrendGranularity` resolved to `"day"`.
 */
export function DailySpendBars({ buckets, color }: { buckets: TrendBucket[]; color: string }) {
  const data = buckets.map((b) => ({
    day: String(new Date(b.bucketStart).getDate()),
    spend: Number(b.totalSpend),
  }));

  return (
    <div data-testid="daily-spend-bars">
      <p className="sr-only">Daily spending across {data.length} days.</p>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={data} margin={{ top: 4, right: 8, bottom: 4, left: 4 }}>
          <CartesianGrid stroke={CHART.grid} vertical={false} strokeDasharray="3 3" />
          <XAxis
            dataKey="day"
            tick={axisTick}
            tickLine={false}
            axisLine={false}
            interval={Math.max(0, Math.ceil(data.length / 10) - 1)}
          />
          <YAxis
            tick={axisTick}
            tickLine={false}
            axisLine={false}
            width={56}
            tickFormatter={(v) => formatCurrency(Number(v))}
          />
          <Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--color-surface-muted)" }} />
          <Bar dataKey="spend" radius={[3, 3, 0, 0]} maxBarSize={14} fill={color} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
