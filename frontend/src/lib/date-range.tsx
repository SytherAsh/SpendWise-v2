"use client";

import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

export type RangePreset = "this-month" | "last-month" | "last-3-months" | "this-fy" | "ytd" | "custom";

export interface DateRange {
  /** ISO date (YYYY-MM-DD), inclusive start. */
  from: string;
  /** ISO date (YYYY-MM-DD), inclusive end. */
  to: string;
  preset: RangePreset;
  label: string;
}

function iso(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** Indian financial year starts 1 April. */
function financialYearStart(now: Date): Date {
  const y = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1;
  return new Date(y, 3, 1);
}

export function computeRange(preset: Exclude<RangePreset, "custom">, now = new Date()): DateRange {
  const end = iso(now);
  switch (preset) {
    case "this-month":
      return { preset, from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: end, label: "This month" };
    case "last-month": {
      const first = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const last = new Date(now.getFullYear(), now.getMonth(), 0);
      return { preset, from: iso(first), to: iso(last), label: "Last month" };
    }
    case "last-3-months":
      return { preset, from: iso(new Date(now.getFullYear(), now.getMonth() - 2, 1)), to: end, label: "Last 3 months" };
    case "this-fy":
      return { preset, from: iso(financialYearStart(now)), to: end, label: "This financial year" };
    case "ytd":
      return { preset, from: iso(new Date(now.getFullYear(), 0, 1)), to: end, label: "Year to date" };
  }
}

interface DateRangeContextValue {
  range: DateRange;
  setPreset: (preset: Exclude<RangePreset, "custom">) => void;
  setCustom: (from: string, to: string) => void;
}

const DateRangeContext = createContext<DateRangeContextValue | null>(null);

export function DateRangeProvider({ children }: { children: ReactNode }) {
  const [range, setRange] = useState<DateRange>(() => computeRange("this-month"));

  const value = useMemo<DateRangeContextValue>(
    () => ({
      range,
      setPreset: (preset) => setRange(computeRange(preset)),
      setCustom: (from, to) =>
        setRange({ preset: "custom", from, to, label: `${from} → ${to}` }),
    }),
    [range],
  );

  return <DateRangeContext.Provider value={value}>{children}</DateRangeContext.Provider>;
}

export function useDateRange(): DateRangeContextValue {
  const ctx = useContext(DateRangeContext);
  if (!ctx) throw new Error("useDateRange must be used within DateRangeProvider");
  return ctx;
}
