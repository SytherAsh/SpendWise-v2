"use client";

import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { cn } from "@/lib/cn";

function prefersReducedMotionNow(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

// Client-only read (matchMedia) — resolved via useSyncExternalStore (server/first-paint
// snapshot false) rather than a setState-in-effect, matching AuthGuard's established pattern.
const EMPTY_SUBSCRIBE = () => () => {};

/** Animate a number from 0 → value on mount (skipped under reduced-motion). */
export function useCountUp(value: number, durationMs = 900): number {
  const reduced = useSyncExternalStore(EMPTY_SUBSCRIBE, prefersReducedMotionNow, () => false);
  const [display, setDisplay] = useState(0);
  const raf = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (reduced) {
      return;
    }
    const start = performance.now();
    const from = 0;
    const tick = (now: number) => {
      const t = Math.min((now - start) / durationMs, 1);
      const eased = 1 - Math.pow(1 - t, 3); // easeOutCubic
      setDisplay(from + (value - from) * eased);
      if (t < 1) raf.current = requestAnimationFrame(tick);
    };
    raf.current = requestAnimationFrame(tick);
    return () => {
      if (raf.current) cancelAnimationFrame(raf.current);
    };
  }, [value, durationMs, reduced]);

  return reduced ? value : display;
}

export function StatTile({
  label,
  value,
  format,
  delta,
  accent,
  icon,
  animate = true,
}: {
  label: string;
  value: number;
  format: (n: number) => string;
  /** Signed period-over-period change, if available. */
  delta?: { pct: number } | null;
  /** Optional accent color for the value (e.g. a category color). */
  accent?: string;
  icon?: ReactNode;
  animate?: boolean;
}) {
  const animated = useCountUp(animate ? value : 0);
  const shown = animate ? animated : value;

  return (
    <div className="rounded-[var(--radius)] border border-border bg-surface p-5 shadow-[var(--shadow-sm)]">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-foreground-muted">{label}</p>
        {icon && <span className="text-foreground-subtle">{icon}</span>}
      </div>
      <p className="mono mt-2 text-[1.75rem] font-medium leading-tight tracking-tight text-foreground" style={accent ? { color: accent } : undefined}>
        {format(shown)}
      </p>
      {delta && (
        <p
          className={cn(
            "mt-1 text-xs font-medium",
            delta.pct > 0 ? "text-[var(--color-danger)]" : delta.pct < 0 ? "text-brand-700" : "text-foreground-subtle",
          )}
        >
          {delta.pct > 0 ? "▲" : delta.pct < 0 ? "▼" : ""} {Math.abs(delta.pct).toFixed(0)}% vs previous period
        </p>
      )}
    </div>
  );
}
