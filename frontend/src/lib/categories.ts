/**
 * Category color system for the 12 fixed spending categories.
 *
 * The palette was validated with the dataviz skill. A 12-hue categorical set sits in
 * the "floor" band for colorblind separation, which is legal ONLY when color is backed
 * by secondary encoding — so category color is ALWAYS shipped alongside the category's
 * icon + text label (chips, the ranked list beside donuts, direct slice labels, table
 * views). Never rely on hue alone to identify a category.
 *
 * Colors are keyed by a normalized form of the category name so they stay stable
 * regardless of the backend's numeric id ordering; an id-based slot is the fallback
 * for any unrecognized name.
 */

/** Ordered slot list — also the fallback ramp for unknown names (by id). */
export const CATEGORY_PALETTE = [
  "#2a78d6", // blue
  "#eb6834", // orange
  "#16a34a", // green
  "#0891b2", // cyan
  "#7c5cff", // violet
  "#4f46e5", // indigo
  "#84a800", // lime
  "#e0559d", // pink
  "#1baf7a", // teal
  "#e34948", // red
  "#eda100", // amber
  "#8b8b82", // neutral
] as const;

/** Keyword → hex. Matched against a lowercased category name (first hit wins). */
const NAME_COLORS: Array<[RegExp, string]> = [
  [/shop/, "#2a78d6"],
  [/grocer/, "#16a34a"],
  [/food|dine|restaurant|eat/, "#eb6834"],
  [/travel|transport|fuel|cab|uber|ola/, "#0891b2"],
  [/entertain|movie|stream/, "#7c5cff"],
  [/subscrip/, "#4f46e5"],
  [/sport|fitness|gym/, "#84a800"],
  [/cosmetic|beauty|salon/, "#e0559d"],
  [/medical|health|pharma|doctor/, "#1baf7a"],
  [/fee|debt|loan|emi|interest/, "#e34948"],
  [/transfer|sent|received|upi/, "#eda100"],
  [/misc|other/, "#8b8b82"],
];

/**
 * Resolve a stable hex color for a category. Prefers a name-keyword match; falls back
 * to a deterministic palette slot by id so every category always has a color.
 */
export function categoryColor(name: string | null | undefined, id?: number): string {
  if (name) {
    const lower = name.toLowerCase();
    for (const [re, hex] of NAME_COLORS) {
      if (re.test(lower)) return hex;
    }
  }
  if (typeof id === "number" && id > 0) {
    return CATEGORY_PALETTE[(id - 1) % CATEGORY_PALETTE.length];
  }
  return CATEGORY_PALETTE[CATEGORY_PALETTE.length - 1];
}

/** A soft tinted background for category chips (12% of the hue over the surface). */
export function categoryTint(name: string | null | undefined, id?: number): string {
  return `color-mix(in srgb, ${categoryColor(name, id)} 12%, transparent)`;
}
