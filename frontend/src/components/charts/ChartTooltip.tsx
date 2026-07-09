"use client";

import { formatCurrency } from "@/lib/format";

interface TooltipEntry {
  color?: string;
  value?: number | string;
  name?: string | number;
}

interface ChartTooltipProps {
  active?: boolean;
  label?: string | number;
  payload?: TooltipEntry[];
}

/**
 * Shared, tokenized tooltip for all charts — a small elevated card with the point label
 * and an INR-formatted value. Text stays in ink tokens; the series color rides the swatch.
 * Recharts injects `active`/`payload`/`label` at render time.
 */
export function ChartTooltip({ active, payload, label }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="rounded-[var(--radius-sm)] border border-border bg-surface-elevated px-3 py-2 shadow-[var(--shadow-md)]">
      {label != null && <p className="mb-1 text-xs font-medium text-foreground-muted">{String(label)}</p>}
      {payload.map((entry, i) => (
        <div key={i} className="flex items-center gap-2 text-sm">
          {entry.color && (
            <span aria-hidden className="size-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
          )}
          <span className="mono font-medium text-foreground">{formatCurrency(Number(entry.value))}</span>
        </div>
      ))}
    </div>
  );
}
