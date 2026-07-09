"use client";

import { useState } from "react";
import { CategoryTrendGrid } from "@/components/analytics/CategoryTrendGrid";
import { CategoryDeepDive } from "@/components/analytics/CategoryDeepDive";
import type { CategorySelection } from "@/components/transactions/CategorySummaryGrid";

/**
 * Analytics is the category-specific deep-dive surface (as opposed to the Dashboard's
 * summary) — every figure on this page is scoped to one category, or (with nothing selected)
 * a small-multiples comparison across all of them. See design-system.md §10.2.
 */
export function AnalyticsView() {
  const [selected, setSelected] = useState<CategorySelection>(null);

  return selected !== null ? (
    <CategoryDeepDive categoryId={selected} onClose={() => setSelected(null)} />
  ) : (
    <CategoryTrendGrid selected={selected} onSelect={setSelected} />
  );
}
