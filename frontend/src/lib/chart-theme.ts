/** Shared chart theming so every Recharts surface reads as one system (dataviz method). */

export const CHART = {
  // Theme-aware brand ink (see --chart-* in globals.css): the interactive brand on
  // light, the bright signal green on dark, so lines stay legible on both grounds.
  brand: "var(--chart-line)",
  brandStrong: "var(--chart-line-strong)",
  fill: "var(--chart-fill)",
  grid: "var(--border)",
  axis: "var(--color-foreground-subtle)",
  ink: "var(--color-foreground)",
} as const;

/** Recharts axis tick style — recessive, tabular for aligned money ticks. */
export const axisTick = { fontSize: 12, fill: "var(--color-foreground-subtle)" } as const;
