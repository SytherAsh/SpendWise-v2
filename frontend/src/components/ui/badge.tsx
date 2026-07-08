"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Small pill for status/labels. For CATEGORY chips prefer <CategoryChip> (categories.ts)
 * so the icon + name secondary encoding always accompanies the color.
 */
export function Badge({
  className,
  tone = "neutral",
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & {
  tone?: "neutral" | "brand" | "warning" | "danger";
}) {
  const tones = {
    neutral: "bg-surface-muted text-foreground-muted",
    brand: "bg-brand-50 text-brand-800",
    warning: "bg-[var(--color-warning-surface)] text-[var(--color-warning)]",
    danger: "bg-[var(--color-danger-surface)] text-[var(--color-danger)]",
  } as const;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium",
        tones[tone],
        className,
      )}
      {...props}
    />
  );
}
