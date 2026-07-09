/**
 * A minimal inline trend indicator for a compact card (e.g. a category tile) — not a full
 * Recharts surface. Per the dataviz stat-tile contract: the line rides the de-emphasis gray
 * (never a per-series hue, since the card already carries category identity via its icon/color
 * chip elsewhere) with the current-period endpoint picked out in the brand accent.
 */
export function Sparkline({
  values,
  width = 72,
  height = 28,
}: {
  values: number[];
  width?: number;
  height?: number;
}) {
  if (values.length < 2) return null;

  const max = Math.max(...values, 0);
  const min = Math.min(...values, 0);
  const range = max - min || 1;
  const stepX = width / (values.length - 1);
  const points = values.map((v, i) => [i * stepX, height - ((v - min) / range) * height] as const);
  const path = points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const [lastX, lastY] = points[points.length - 1];

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      className="overflow-visible"
      aria-hidden="true"
    >
      <path
        d={path}
        fill="none"
        stroke="var(--color-foreground-subtle)"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={lastX} cy={lastY} r={2.5} fill="var(--chart-line)" stroke="var(--color-surface)" strokeWidth={1} />
    </svg>
  );
}
