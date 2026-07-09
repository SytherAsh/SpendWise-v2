"use client";

import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

export type RangePreset = "this-month" | "last-month" | "last-3-months" | "last-6-months" | "this-fy" | "ytd" | "month" | "custom";

export interface DateRange {
  /** ISO date (YYYY-MM-DD), inclusive start. */
  from: string;
  /** ISO date (YYYY-MM-DD), inclusive end. */
  to: string;
  preset: RangePreset;
  label: string;
}

/**
 * `Date` → inclusive `YYYY-MM-DD`, in the convention every `DateRange` field uses — built from
 * the date's *local* year/month/day, not `toISOString()` (which converts to UTC first). Every
 * `Date` in this file is constructed via the local `new Date(year, month, day)` form, so
 * formatting through UTC would silently shift the result by a day in any timezone ahead of UTC
 * (IST included) — e.g. `monthRange(2026, 3)` (April) would format its 1st as `"2026-03-31"`.
 */
export function iso(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** Indian financial year starts 1 April. */
function financialYearStart(now: Date): Date {
  const y = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1;
  return new Date(y, 3, 1);
}

export function computeRange(preset: Exclude<RangePreset, "custom" | "month">, now = new Date()): DateRange {
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

/**
 * A single calendar month, `year`/`monthIndex` (0-based, matching `Date`) — backs the
 * Analytics page's month-stepper card. Caps `to` at today when `monthIndex` is the current
 * real-world month (same convention as `computeRange("this-month")`), so a not-yet-finished
 * month never implies data for days that haven't happened; past months use the full month.
 */
export function monthRange(year: number, monthIndex: number, now = new Date()): DateRange {
  const first = new Date(year, monthIndex, 1);
  const lastDay = new Date(year, monthIndex + 1, 0);
  const isCurrentMonth = year === now.getFullYear() && monthIndex === now.getMonth();
  const label = first.toLocaleDateString("en-IN", { month: "long", year: "numeric" });
  return { preset: "month", from: iso(first), to: iso(isCurrentMonth ? now : lastDay), label };
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
  const fromMs = new Date(`${from}T00:00:00`).getTime();
  const toMs = new Date(`${to}T00:00:00`).getTime();
  const spanDays = Math.round((toMs - fromMs) / DAY_MS) + 1;
  const prevTo = new Date(fromMs - DAY_MS);
  const prevFrom = new Date(fromMs - spanDays * DAY_MS);
  return { from: iso(prevFrom), to: iso(prevTo) };
}

/**
 * A window of `count` calendar months ending at the month containing `anchorTo` (inclusive) —
 * e.g. `trailingMonths("2026-07-15", 6)` is Feb–Jul 2026. `to` is capped at today so a
 * not-yet-finished anchor month never requests future dates. Used for "last N months side by
 * side" bar charts that should shift along with whatever month is currently being viewed
 * (e.g. via the Analytics month-stepper), rather than always being anchored to today.
 */
export function trailingMonths(anchorTo: string, count: number, now = new Date()): { from: string; to: string } {
  const anchor = new Date(`${anchorTo}T00:00:00`);
  const start = new Date(anchor.getFullYear(), anchor.getMonth() - (count - 1), 1);
  const endOfAnchorMonth = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0);
  const end = endOfAnchorMonth > now ? now : endOfAnchorMonth;
  return { from: iso(start), to: iso(end) };
}

/** Number of calendar months a `[from, to]` window covers, inclusive (e.g. Jul→Jul is 1, Feb→Jul is 6). */
export function monthSpan(from: string, to: string): number {
  const f = new Date(`${from}T00:00:00`);
  const t = new Date(`${to}T00:00:00`);
  return (t.getFullYear() - f.getFullYear()) * 12 + (t.getMonth() - f.getMonth()) + 1;
}

/**
 * Adaptive bucket size for `/analytics/trends` by the selected range's span — a sparkline or
 * trend chart over "This month" wants daily buckets, but the same chart over "This financial
 * year" would be a single indistinguishable blob at daily resolution and a wall of noise at
 * weekly, so the granularity scales with how much time is actually being covered.
 */
export function pickTrendGranularity(from: string, to: string): "day" | "week" | "month" | "year" {
  const spanDays =
    Math.round((new Date(`${to}T00:00:00`).getTime() - new Date(`${from}T00:00:00`).getTime()) / DAY_MS) + 1;
  if (spanDays <= 45) return "day";
  if (spanDays <= 200) return "week";
  if (spanDays <= 800) return "month";
  return "year";
}

interface DateRangeContextValue {
  range: DateRange;
  setPreset: (preset: Exclude<RangePreset, "custom" | "month">) => void;
  setCustom: (from: string, to: string) => void;
  /** Sets the shared range to one calendar month — the Analytics month-stepper's control. */
  setMonth: (year: number, monthIndex: number) => void;
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
      setMonth: (year, monthIndex) => setRange(monthRange(year, monthIndex)),
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
