"use client";

import { useState } from "react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { formatCurrency } from "@/lib/format";
import { Card, EmptyState, ErrorState, ProgressBar, Spinner } from "@/components/shared/ui";

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

export function BudgetManager() {
  const progress = useApi<BudgetProgress[]>("/budgets/progress");
  const suggestions = useApi<BudgetSuggestion[]>("/budgets/suggestions");
  const { categories, categoryName } = useCategories();

  const [editing, setEditing] = useState<number | null>(null);
  const [limitInput, setLimitInput] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (progress.isLoading && !progress.data) return <Spinner />;
  if (progress.error && !progress.data)
    return <ErrorState message="Could not load budgets." onRetry={progress.refresh} />;

  const progressByCat = new Map((progress.data ?? []).map((p) => [p.categoryId, p]));
  const suggestionByCat = new Map((suggestions.data ?? []).map((s) => [s.categoryId, s]));

  // Show every category so a user can set a budget for one that has none yet.
  const rows = categories.length
    ? categories.map((c) => c.id)
    : Array.from(progressByCat.keys());

  function startEdit(categoryId: number) {
    const existing = progressByCat.get(categoryId);
    setEditing(categoryId);
    setLimitInput(existing ? String(existing.monthlyLimit) : "");
    setError(null);
  }

  function acceptSuggestion(categoryId: number) {
    const s = suggestionByCat.get(categoryId);
    if (s?.available) setLimitInput(String(s.suggestedMonthlyLimit));
  }

  async function save(categoryId: number) {
    const limit = Number(limitInput);
    if (!limitInput || Number.isNaN(limit) || limit <= 0) {
      setError("Enter a monthly limit greater than zero.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await apiClient.post("/budgets", { categoryId, monthlyLimit: limit });
      setEditing(null);
      progress.refresh();
    } catch {
      setError("Could not save the budget. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  if (!rows.length) return <EmptyState message="No categories available." />;

  return (
    <div className="max-w-2xl space-y-3">
      {rows.map((categoryId) => {
        const p = progressByCat.get(categoryId);
        const suggestion = suggestionByCat.get(categoryId);
        const isEditing = editing === categoryId;
        const ratio = p && p.monthlyLimit > 0 ? p.spent / p.monthlyLimit : 0;

        return (
          <Card key={categoryId}>
            <div className="flex items-center justify-between gap-4">
              <span className="font-medium">{categoryName(categoryId)}</span>
              {p ? (
                <span className="text-sm text-neutral-500">
                  {formatCurrency(p.spent)} / {formatCurrency(p.monthlyLimit)}
                  {" · "}
                  {Math.round(p.percentSpent)}%
                </span>
              ) : (
                <span className="text-sm text-neutral-400">No budget set</span>
              )}
            </div>

            {p && (
              <div className="mt-3">
                <ProgressBar ratio={ratio} danger={ratio > 1} />
              </div>
            )}

            {isEditing ? (
              <div className="mt-4 space-y-2">
                {suggestion?.available && (
                  <p className="text-sm text-neutral-500">
                    Suggested: {formatCurrency(suggestion.suggestedMonthlyLimit)}{" "}
                    <button
                      type="button"
                      onClick={() => acceptSuggestion(categoryId)}
                      className="ml-1 text-blue-600 underline"
                    >
                      Accept
                    </button>
                  </p>
                )}
                <div className="flex items-center gap-2">
                  <span className="text-sm text-neutral-500">₹</span>
                  <input
                    type="number"
                    min={1}
                    aria-label={`Monthly limit for ${categoryName(categoryId)}`}
                    value={limitInput}
                    onChange={(e) => setLimitInput(e.target.value)}
                    className="w-36 rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
                  />
                  <button
                    type="button"
                    onClick={() => save(categoryId)}
                    disabled={saving}
                    className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {saving ? "Saving…" : "Save"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditing(null)}
                    className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15"
                  >
                    Cancel
                  </button>
                </div>
                {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
              </div>
            ) : (
              <button
                type="button"
                onClick={() => startEdit(categoryId)}
                className="mt-3 text-sm text-blue-600 underline"
              >
                {p ? "Edit budget" : "Set budget"}
              </button>
            )}
          </Card>
        );
      })}
    </div>
  );
}
