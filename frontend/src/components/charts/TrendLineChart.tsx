"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency, formatDate } from "@/lib/format";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { CHART, axisTick } from "@/lib/chart-theme";

export interface TrendBucket {
  bucketStart: string;
  totalSpend: number;
}

/** Area chart of spending over time (E10-S2-T1 trend chart, from GET /analytics/trends). */
export function TrendLineChart({ buckets, height = 260 }: { buckets: TrendBucket[]; height?: number }) {
  const data = buckets.map((b) => ({
    label: formatDate(b.bucketStart),
    spend: Number(b.totalSpend),
  }));
  const latest = data.length ? data[data.length - 1] : null;

  return (
    <div data-testid="trend-line-chart" className="chart-glow">
      {/* Accessible caption doubles as a stable assertion point in jsdom, where Recharts'
          SVG has no measured dimensions. */}
      <p className="sr-only">
        Spending trend across {data.length} periods{latest ? `, latest ${formatCurrency(latest.spend)}` : ""}.
      </p>
      <ResponsiveContainer width="100%" height={height}>
        <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: 4 }}>
          <defs>
            <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={CHART.fill} stopOpacity={0.34} />
              <stop offset="100%" stopColor={CHART.fill} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={CHART.grid} vertical={false} />
          <XAxis dataKey="label" tick={axisTick} tickLine={false} axisLine={false} />
          <YAxis
            tick={axisTick}
            tickLine={false}
            axisLine={false}
            width={64}
            tickFormatter={(v) => formatCurrency(Number(v))}
          />
          <Tooltip content={<ChartTooltip />} cursor={{ stroke: CHART.axis, strokeDasharray: "4 4" }} />
          <Area
            type="monotone"
            dataKey="spend"
            stroke={CHART.brand}
            strokeWidth={2.4}
            fill="url(#trendFill)"
            activeDot={{ r: 5, strokeWidth: 2, stroke: "var(--surface)", fill: CHART.brand }}
            dot={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
