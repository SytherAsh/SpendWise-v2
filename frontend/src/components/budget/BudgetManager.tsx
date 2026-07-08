"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { categoryColor, categoryIcon } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";
import { EmptyState, ErrorState, ProgressBar } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";

interface BudgetProgress {
  categoryId: number;
  monthlyLimit: number;
  spent: number;
  percentSpent: number;
}

interface BudgetSuggestion {
  categoryId: number;
  suggestedMonthlyLimit: number;
  available: boolean;
}

const SLIDER_STEP = 100;
const FALLBACK_MAX = 10000;
const TILE_COUNT_ESTIMATE = 12; // the 12 fixed categories — used only to size the loading skeleton

function roundUpToStep(n: number): number {
  return Math.ceil(n / SLIDER_STEP) * SLIDER_STEP;
}

/**
 * Slider ceiling for a category: 3x its 6-month suggested average (rounded to a clean step),
 * falling back to a modest flat max for a category with no spending history at all. Never lower
 * than the category's current budget, so an existing high budget is never clipped off-slider.
 */
function computeMax(suggestion: BudgetSuggestion | undefined, currentLimit: number): number {
  const scaled = suggestion?.available ? roundUpToStep(suggestion.suggestedMonthlyLimit * 3) : FALLBACK_MAX;
  return Math.max(scaled, roundUpToStep(currentLimit), FALLBACK_MAX);
}

/**
 * Budgets tab of Planning — every category as a tile (no scrolling to see all 12, mirroring the
 * Transactions page's category grid), colored/iconed the same way. Clicking a tile expands an
 * inline slider (0 to a per-category max, step ₹100) to set its monthly limit; a small corner
 * link jumps to that category's transactions. The header's total is a pure sum of the 12
 * categories' budgets — there's no separate "overall budget" concept in the schema.
 */
export function BudgetManager() {
  const progress = useApi<BudgetProgress[]>("/budgets/progress");
  const suggestions = useApi<BudgetSuggestion[]>("/budgets/suggestions");
  const { categories, isLoading: categoriesLoading } = useCategories();

  const [expanded, setExpanded] = useState<number | null>(null);
  const [sliderValue, setSliderValue] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const initialLoading = categoriesLoading || (progress.isLoading && !progress.data);

  if (progress.error && !progress.data) {
    return <ErrorState message="Could not load budgets." onRetry={progress.refresh} />;
  }

  if (initialLoading) {
    return (
      <div className="grid grid-cols-[repeat(auto-fit,minmax(15rem,1fr))] gap-3">
        {Array.from({ length: TILE_COUNT_ESTIMATE }).map((_, i) => (
          <Skeleton key={i} className="h-[132px]" />
        ))}
      </div>
    );
  }

  if (categories.length === 0) return <EmptyState message="No categories available." />;

  const progressByCat = new Map((progress.data ?? []).map((p) => [p.categoryId, p]));
  const suggestionByCat = new Map((suggestions.data ?? []).map((s) => [s.categoryId, s]));
  const totalBudgeted = (progress.data ?? []).reduce((sum, p) => sum + p.monthlyLimit, 0);

  function startEdit(categoryId: number) {
    const existing = progressByCat.get(categoryId);
    const suggestion = suggestionByCat.get(categoryId);
    setExpanded(categoryId);
    setSliderValue(existing ? existing.monthlyLimit : suggestion?.available ? suggestion.suggestedMonthlyLimit : 0);
    setError(null);
  }

  function toggle(categoryId: number) {
    if (expanded === categoryId) {
      setExpanded(null);
    } else {
      startEdit(categoryId);
    }
  }

  async function save(categoryId: number) {
    if (sliderValue <= 0) {
      setError("Set an amount greater than zero.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await apiClient.post("/budgets", { categoryId, monthlyLimit: sliderValue });
      setExpanded(null);
      progress.refresh();
    } catch {
      setError("Could not save the budget. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-4">
      <p data-testid="budget-total" className="text-sm text-foreground-muted">
        Total budget this month <span className="font-medium text-foreground">{formatCurrency(totalBudgeted)}</span>
      </p>

      <div className="grid grid-cols-[repeat(auto-fit,minmax(15rem,1fr))] gap-3">
        {categories.map((c) => {
          const p = progressByCat.get(c.id);
          const suggestion = suggestionByCat.get(c.id);
          const isExpanded = expanded === c.id;
          const color = categoryColor(c.name, c.id);
          const Icon = categoryIcon(c.icon);
          const ratio = p && p.monthlyLimit > 0 ? p.spent / p.monthlyLimit : 0;
          const max = computeMax(suggestion, p?.monthlyLimit ?? 0);

          return (
            <div
              key={c.id}
              className="relative rounded-[var(--radius)] border p-3.5"
              style={{
                borderColor: `color-mix(in srgb, ${color} 30%, transparent)`,
                backgroundColor: `color-mix(in srgb, ${color} 8%, transparent)`,
              }}
            >
              <Link
                href={`/transactions?category=${c.id}`}
                aria-label={`View ${c.name} transactions`}
                className="absolute right-2 top-2 rounded-full p-1 text-foreground-subtle transition-colors hover:bg-surface-muted hover:text-foreground"
              >
                <ArrowUpRight className="size-3.5" />
              </Link>

              <button type="button" onClick={() => toggle(c.id)} aria-expanded={isExpanded} className="flex w-full flex-col gap-2 pr-5 text-left">
                <div className="flex items-center gap-2">
                  <span
                    aria-hidden
                    className="flex size-7 shrink-0 items-center justify-center rounded-full"
                    style={{ backgroundColor: `color-mix(in srgb, ${color} 18%, transparent)`, color }}
                  >
                    <Icon className="size-4" />
                  </span>
                  <span className="truncate text-sm font-medium text-foreground">{c.name}</span>
                </div>

                {p ? (
                  <>
                    <ProgressBar ratio={ratio} danger={ratio > 1} />
                    <p className="text-xs text-foreground-subtle">
                      {formatCurrency(p.spent)} / {formatCurrency(p.monthlyLimit)} · {Math.round(p.percentSpent)}%
                    </p>
                  </>
                ) : (
                  <p className="text-xs text-foreground-subtle">No budget set</p>
                )}
              </button>

              {isExpanded && (
                <div className="mt-3 space-y-2 border-t border-border pt-3">
                  {suggestion?.available && (
                    <p className="text-xs text-foreground-muted">
                      Suggested (6-mo avg) {formatCurrency(suggestion.suggestedMonthlyLimit)}{" "}
                      <button
                        type="button"
                        onClick={() => setSliderValue(suggestion.suggestedMonthlyLimit)}
                        className="ml-1 font-medium text-brand-700 underline"
                      >
                        Use
                      </button>
                    </p>
                  )}
                  <input
                    type="range"
                    min={0}
                    max={max}
                    step={SLIDER_STEP}
                    value={sliderValue}
                    onChange={(e) => setSliderValue(Number(e.target.value))}
                    aria-label={`Monthly budget for ${c.name}`}
                    className="w-full cursor-pointer"
                    style={{ accentColor: color }}
                  />
                  <p className="tnum text-sm font-semibold text-foreground">{formatCurrency(sliderValue)}</p>
                  {error && (
                    <p role="alert" className="text-xs text-[var(--color-danger)]">
                      {error}
                    </p>
                  )}
                  <div className="flex gap-2">
                    <Button size="sm" onClick={() => save(c.id)} disabled={saving}>
                      {saving ? "Saving…" : "Save"}
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setExpanded(null)}>
                      Cancel
                    </Button>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
