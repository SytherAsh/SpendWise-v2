"use client";

import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

export type RangePreset = "this-month" | "last-month" | "last-3-months" | "last-6-months" | "this-fy" | "ytd" | "custom";

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
    case "last-6-months":
      return { preset, from: iso(new Date(now.getFullYear(), now.getMonth() - 5, 1)), to: end, label: "Last 6 months" };
    case "this-fy":
      return { preset, from: iso(financialYearStart(now)), to: end, label: "This financial year" };
    case "ytd":
      return { preset, from: iso(new Date(now.getFullYear(), 0, 1)), to: end, label: "Year to date" };
  }
}

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The equal-length window immediately preceding `[from, to]` (both inclusive `YYYY-MM-DD`) —
 * e.g. previousPeriod("2026-04-01", "2026-04-30") is March 2026. Used for "this period vs
 * previous period" comparisons that must track whatever range the user has picked (Analytics'
 * category deep-dive), unlike `/analytics/comparison`, which is always anchored to *today*
 * regardless of the selected range.
 */
export function previousPeriod(from: string, to: string): { from: string; to: string } {
  const fromMs = Date.parse(`${from}T00:00:00Z`);
  const toMs = Date.parse(`${to}T00:00:00Z`);
  const spanDays = Math.round((toMs - fromMs) / DAY_MS) + 1;
  const prevTo = new Date(fromMs - DAY_MS);
  const prevFrom = new Date(fromMs - spanDays * DAY_MS);
  return { from: iso(prevFrom), to: iso(prevTo) };
}

/**
 * Adaptive bucket size for `/analytics/trends` by the selected range's span — a sparkline or
 * trend chart over "This month" wants daily buckets, but the same chart over "This financial
 * year" would be a single indistinguishable blob at daily resolution and a wall of noise at
 * weekly, so the granularity scales with how much time is actually being covered.
 */
export function pickTrendGranularity(from: string, to: string): "day" | "week" | "month" | "year" {
  const spanDays = Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS) + 1;
  if (spanDays <= 45) return "day";
  if (spanDays <= 200) return "week";
  if (spanDays <= 800) return "month";
  return "year";
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
