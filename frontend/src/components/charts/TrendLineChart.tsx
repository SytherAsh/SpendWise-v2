"use client";

import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency, formatDate } from "@/lib/format";

export interface TrendBucket {
  bucketStart: string;
  totalSpend: number;
}

/** Line chart of spending over time (E10-S2-T1 trend chart, from GET /analytics/trends). */
export function TrendLineChart({ buckets }: { buckets: TrendBucket[] }) {
  const data = buckets.map((b) => ({
    label: formatDate(b.bucketStart),
    spend: Number(b.totalSpend),
  }));
  const latest = data.length ? data[data.length - 1] : null;

  return (
    <div data-testid="trend-line-chart">
      {/* Accessible caption doubles as a stable assertion point in jsdom, where Recharts'
          SVG has no measured dimensions. */}
      <p className="sr-only">
        Spending trend across {data.length} periods{latest ? `, latest ${formatCurrency(latest.spend)}` : ""}.
      </p>
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.1} />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} width={64} tickFormatter={(v) => formatCurrency(Number(v))} />
          <Tooltip formatter={(v) => formatCurrency(Number(v))} />
          <Line type="monotone" dataKey="spend" stroke="#2563eb" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
