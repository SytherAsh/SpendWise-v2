"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency } from "@/lib/format";

export interface CategoryTotal {
  categoryId: number;
  categoryName: string;
  totalSpend: number;
}

/** Bar chart of spend per category (E10-S2-T1 category summary, from GET /analytics/summary). */
export function CategoryBarChart({ categories }: { categories: CategoryTotal[] }) {
  const data = categories
    .map((c) => ({ name: c.categoryName, spend: Number(c.totalSpend) }))
    .filter((c) => c.spend > 0)
    .sort((a, b) => b.spend - a.spend);

  return (
    <div data-testid="category-bar-chart">
      <p className="sr-only">Spending by category across {data.length} categories.</p>
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.1} vertical={false} />
          <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-20} textAnchor="end" height={60} />
          <YAxis tick={{ fontSize: 12 }} width={64} tickFormatter={(v) => formatCurrency(Number(v))} />
          <Tooltip formatter={(v) => formatCurrency(Number(v))} />
          <Bar dataKey="spend" fill="#2563eb" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
