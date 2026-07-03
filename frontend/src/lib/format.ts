/** Shared display formatters for the web dashboard. */

const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

const inrFormatterPaise = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Format a rupee amount, e.g. 3240 → "₹3,240". Pass `paise: true` for two decimals. */
export function formatCurrency(amount: number, paise = false): string {
  const formatter = paise ? inrFormatterPaise : inrFormatter;
  return formatter.format(amount);
}

const dateFormatter = new Intl.DateTimeFormat("en-IN", {
  day: "2-digit",
  month: "short",
  year: "numeric",
});

/** Format an ISO date/datetime string as e.g. "15 Jun 2026". Returns "" for null/invalid. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return dateFormatter.format(d);
}

/** Current calendar year (used to default the export financial-year picker). */
export function currentYear(): number {
  return new Date().getFullYear();
}
