"use client";

import type { ReactNode } from "react";
import { useCountUp } from "@/components/shared/StatTile";
import { cn } from "@/lib/cn";

/**
 * Compact, glow-accented figure card for a page header's "hero number" — the value a
 * user's eye should land on first (e.g. money spent/received, a running total). Visually
 * lighter than <StatTile> (which anchors a full dashboard grid) but shares its mono
 * numerals + count-up so header figures and dashboard KPIs read as one system.
 */
export function MiniStat({
  label,
  value,
  format,
  icon,
  tone = "neutral",
  animate = true,
  className,
}: {
  label: string;
  value: number;
  format: (n: number) => string;
  icon?: ReactNode;
  /** "positive" accents the value in brand green (e.g. money received, a credit). */
  tone?: "neutral" | "positive";
  animate?: boolean;
  className?: string;
}) {
  const animated = useCountUp(animate ? value : 0, 700);
  const shown = animate ? animated : value;

  return (
    <div
      className={cn(
        "flex min-w-[9.5rem] items-center gap-3 rounded-[var(--radius)] border border-brand-400/25 bg-surface px-4 py-3",
        "shadow-[var(--shadow-sm),var(--glow-brand-sm)]",
        className,
      )}
    >
      {icon && (
        <span
          aria-hidden
          className="grid size-9 shrink-0 place-items-center rounded-[var(--radius-sm)] bg-[image:var(--gradient-brand-vivid)] text-[#04170d]"
        >
          {icon}
        </span>
      )}
      <div className="min-w-0">
        <p className="text-xs font-medium text-foreground-subtle">{label}</p>
        <p
          className={cn(
            "mono text-xl font-medium leading-tight tracking-tight",
            tone === "positive" ? "text-brand-700 dark:text-brand-300" : "text-foreground",
          )}
        >
          {format(shown)}
        </p>
      </div>
    </div>
  );
}
