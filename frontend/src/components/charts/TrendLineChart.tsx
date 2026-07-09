"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency, formatDate } from "@/lib/format";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { CHART, axisTick } from "@/lib/chart-theme";

export interface TrendBucket {
  bucketStart: string;
  totalSpend: number;
}

/**
 * Area chart of spending over time (E10-S2-T1 trend chart, from GET /analytics/trends).
 *
 * Two purposes per design-system.md §10.2: "hero" (default) is the glowing gradient area
 * used on the Dashboard — thicker line, soft glow, emphasized fill, faint grid. "crisp" is
 * the comparison/density treatment for Analytics — thin line, no glow, minimal fill, a
 * tighter reference grid — where reading exact values matters more than the hero moment.
 */
export function TrendLineChart({
  buckets,
  height = 260,
  variant = "hero",
  formatValue = formatCurrency,
}: {
  buckets: TrendBucket[];
  height?: number;
  variant?: "hero" | "crisp";
  /** Y-axis tick + tooltip formatter — defaults to INR currency; pass e.g. a percent formatter for a non-money series (the `totalSpend` field is reused as a generic plotted value). */
  formatValue?: (n: number) => string;
}) {
  const data = buckets.map((b) => ({
    label: formatDate(b.bucketStart),
    spend: Number(b.totalSpend),
  }));
  const latest = data.length ? data[data.length - 1] : null;
  const crisp = variant === "crisp";
  const gradientId = crisp ? "trendFillCrisp" : "trendFill";

  return (
    <div data-testid="trend-line-chart" className={crisp ? undefined : "chart-glow"}>
      {/* Accessible caption doubles as a stable assertion point in jsdom, where Recharts'
          SVG has no measured dimensions. */}
      <p className="sr-only">
        Spending trend across {data.length} periods{latest ? `, latest ${formatValue(latest.spend)}` : ""}.
      </p>
      <ResponsiveContainer width="100%" height={height}>
        <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: 4 }}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={CHART.fill} stopOpacity={crisp ? 0.14 : 0.34} />
              <stop offset="100%" stopColor={CHART.fill} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={CHART.grid} vertical={false} horizontal={crisp} strokeDasharray={crisp ? "3 3" : undefined} />
          <XAxis dataKey="label" tick={axisTick} tickLine={false} axisLine={false} />
          <YAxis
            tick={axisTick}
            tickLine={false}
            axisLine={false}
            width={64}
            tickFormatter={(v) => formatValue(Number(v))}
          />
          <Tooltip content={<ChartTooltip formatValue={formatValue} />} cursor={{ stroke: CHART.axis, strokeDasharray: "4 4" }} />
          <Area
            type="monotone"
            dataKey="spend"
            stroke={CHART.brand}
            strokeWidth={crisp ? 1.5 : 2.4}
            fill={`url(#${gradientId})`}
            activeDot={{ r: crisp ? 4 : 5, strokeWidth: 2, stroke: "var(--surface)", fill: CHART.brand }}
            dot={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
