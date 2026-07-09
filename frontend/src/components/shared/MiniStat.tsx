"use client";

import type { ReactNode } from "react";
import { useCountUp } from "@/components/shared/StatTile";
import { cn } from "@/lib/cn";

/**
 * "positive" = money credited/received (brand green — a border/glow/icon-badge that also
 * carries the tint, not just the value text, so a "money spent" figure sitting right next
 * to it doesn't still read as generically green at a glance). "negative" = money debited/
 * spent (danger red, same full treatment). "neutral" is the original brand-tinted default
 * for figures that are neither — a budget limit, a category total, etc.
 */
const TONE_STYLES = {
  neutral: {
    border: "border-brand-400/25",
    shadow: "shadow-[var(--shadow-sm),var(--glow-brand-sm)]",
    iconBg: "bg-[image:var(--gradient-brand-vivid)] text-[#04170d]",
    text: "text-foreground",
  },
  positive: {
    border: "border-brand-400/25",
    shadow: "shadow-[var(--shadow-sm),var(--glow-brand-sm)]",
    iconBg: "bg-[image:var(--gradient-brand-vivid)] text-[#04170d]",
    text: "text-brand-700 dark:text-brand-300",
  },
  negative: {
    border: "border-[var(--color-danger)]/30",
    shadow: "shadow-[var(--shadow-sm)]",
    iconBg: "bg-[var(--color-danger)] text-white",
    text: "text-[var(--color-danger)]",
  },
} as const;

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
  /** "positive" = money received; "negative" = money spent; "neutral" is the plain default. */
  tone?: "neutral" | "positive" | "negative";
  animate?: boolean;
  className?: string;
}) {
  const animated = useCountUp(animate ? value : 0, 700);
  const shown = animate ? animated : value;
  const style = TONE_STYLES[tone];

  return (
    <div
      className={cn(
        "flex min-w-[9.5rem] items-center gap-3 rounded-[var(--radius)] border bg-surface px-4 py-3",
        style.border,
        style.shadow,
        className,
      )}
    >
      {icon && (
        <span aria-hidden className={cn("grid size-9 shrink-0 place-items-center rounded-[var(--radius-sm)]", style.iconBg)}>
          {icon}
        </span>
      )}
      <div className="min-w-0">
        <p className="text-xs font-medium text-foreground-subtle">{label}</p>
        <p className={cn("mono text-xl font-medium leading-tight tracking-tight", style.text)}>{format(shown)}</p>
      </div>
    </div>
  );
}
