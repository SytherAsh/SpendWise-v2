import {
  ArrowLeftRight,
  Clapperboard,
  Dumbbell,
  HelpCircle,
  MoreHorizontal,
  Plane,
  Receipt,
  Repeat,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Stethoscope,
  UtensilsCrossed,
  type LucideIcon,
} from "lucide-react";

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

/**
 * The backend seeds `categories.icon` as Google Material Icons identifiers (docs/database.md) —
 * there's no Material Icons font loaded in this app, so rendering that string directly just
 * prints it as text. Map each known identifier to the equivalent lucide-react icon (already the
 * app's icon set everywhere else) instead. Shared by every page that renders a category icon
 * (Transactions, Planning, ...) so the mapping stays in one place.
 */
const MATERIAL_ICON_TO_LUCIDE: Record<string, LucideIcon> = {
  shopping_bag: ShoppingBag,
  movie: Clapperboard,
  fitness_center: Dumbbell,
  local_grocery_store: ShoppingCart,
  flight: Plane,
  more_horiz: MoreHorizontal,
  restaurant: UtensilsCrossed,
  face: Sparkles,
  subscriptions: Repeat,
  swap_horiz: ArrowLeftRight,
  local_hospital: Stethoscope,
  request_quote: Receipt,
};

/** Resolves a category's `icon` field to a renderable component; falls back to a generic icon. */
export function categoryIcon(icon: string | null | undefined): LucideIcon {
  return (icon && MATERIAL_ICON_TO_LUCIDE[icon]) || HelpCircle;
}
