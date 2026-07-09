"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useDateRange } from "@/lib/date-range";
import { cn } from "@/lib/cn";

/**
 * Steps the shared global date range one calendar month at a time ("‹ July 2026 ›"). Writes
 * to the same `DateRangeProvider` state the top-bar `DateRangePicker` uses — unlike those
 * relative-to-today presets (This month, Last 3 months, …), this is the fast way to land on
 * any specific past month, and since the range is shared app-wide, the other pages pick up
 * the same month if visited next. Used in both the Analytics page header and the Transactions
 * page header's center slot (`PageHeader`'s `center` prop) — a shared, page-agnostic control.
 *
 * Reads the "current" month off `range.from` rather than tracking its own state — works
 * whether the range got here via a previous step, a fresh page load (defaults to this
 * calendar month), or the user picking a different preset/custom range elsewhere; stepping
 * from a multi-month range (e.g. "Last 6 months") just steps from its start month.
 */
export function MonthStepper({ className }: { className?: string }) {
  const { range, setMonth } = useDateRange();
  const anchor = new Date(`${range.from}T00:00:00`);
  const year = anchor.getFullYear();
  const monthIndex = anchor.getMonth();

  const now = new Date();
  const isCurrentRealMonth = year === now.getFullYear() && monthIndex === now.getMonth();

  const label = anchor.toLocaleDateString("en-IN", { month: "long", year: "numeric" });

  const step = (delta: number) => {
    const next = new Date(year, monthIndex + delta, 1);
    setMonth(next.getFullYear(), next.getMonth());
  };

  return (
    <div
      className={cn(
        "inline-flex items-center gap-1 rounded-[var(--radius-sm)] border border-border-strong bg-surface p-1",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => step(-1)}
        aria-label="Previous month"
        className="grid size-7 place-items-center rounded-[var(--radius-sm)] text-foreground-muted transition-colors hover:bg-surface-muted hover:text-foreground"
      >
        <ChevronLeft className="size-4" />
      </button>
      <span className="min-w-[8.5rem] px-1 text-center text-sm font-medium tabular-nums text-foreground">{label}</span>
      <button
        type="button"
        onClick={() => step(1)}
        disabled={isCurrentRealMonth}
        aria-label="Next month"
        className="grid size-7 place-items-center rounded-[var(--radius-sm)] text-foreground-muted transition-colors hover:bg-surface-muted hover:text-foreground disabled:pointer-events-none disabled:opacity-40"
      >
        <ChevronRight className="size-4" />
      </button>
    </div>
  );
}
