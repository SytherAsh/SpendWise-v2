/** Shared chart theming so every Recharts surface reads as one system (dataviz method). */

export const CHART = {
  brand: "var(--color-brand-600)",
  brandStrong: "var(--color-brand-700)",
  grid: "var(--border)",
  axis: "var(--color-foreground-subtle)",
  ink: "var(--color-foreground)",
} as const;

/** Recharts axis tick style — recessive, tabular for aligned money ticks. */
export const axisTick = { fontSize: 12, fill: "var(--color-foreground-subtle)" } as const;
