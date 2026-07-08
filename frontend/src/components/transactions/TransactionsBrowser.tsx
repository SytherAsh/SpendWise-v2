"use client";

import { useEffect, useState } from "react";
import useSWRInfinite from "swr/infinite";
import { Users, X } from "lucide-react";
import { apiClient, swrFetcher } from "@/lib/apiClient";
import { useCategories } from "@/lib/useCategories";
import { useDateRange } from "@/lib/date-range";
import { formatCurrency, formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/input";
import { categoryColor } from "@/lib/categories";
import { cn } from "@/lib/cn";
import type { CategorySelection } from "./CategorySummaryGrid";

interface Transaction {
  id: string;
  transactionDate: string;
  amount: number;
  recipientName: string | null;
  upiId: string | null;
  bank: string | null;
  note: string | null;
  categoryId: number | null;
}

interface TransactionListResponse {
  data: Transaction[];
  nextCursor: string | null;
  hasMore: boolean;
}

interface BulkPrompt {
  payee: string;
  categoryId: number;
  ids: string[];
}

const PAGE_SIZE = 50;

function buildPath(categoryFilter: CategorySelection, from: string, to: string, cursor: string | null): string {
  const params = new URLSearchParams();
  params.set("limit", String(PAGE_SIZE));
  if (cursor) params.set("cursor", cursor);
  if (categoryFilter !== null) params.set("category", String(categoryFilter));
  params.set("from", from);
  params.set("to", to);
  return `/transactions?${params.toString()}`;
}

export function TransactionsBrowser({
  categoryFilter,
  onClearFilter,
}: {
  categoryFilter: CategorySelection;
  onClearFilter: () => void;
}) {
  const { range } = useDateRange();
  const { categories, categoryName } = useCategories();
  // Optimistic category corrections overlaid on the fetched data — lets a change reflect
  // immediately without refetching the whole paginated list.
  const [overrides, setOverrides] = useState<Record<string, number>>({});
  const [correctionError, setCorrectionError] = useState<string | null>(null);
  const [bulk, setBulk] = useState<BulkPrompt | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);

  const getKey = (pageIndex: number, previous: TransactionListResponse | null) => {
    if (previous && !previous.hasMore) return null;
    const cursor = pageIndex === 0 ? null : previous?.nextCursor ?? null;
    return buildPath(categoryFilter, range.from, range.to, cursor);
  };

  const { data, error, isLoading, size, setSize, isValidating } = useSWRInfinite<TransactionListResponse>(
    getKey,
    swrFetcher,
    { revalidateFirstPage: false },
  );

  // Dismiss any pending bulk-correction prompt as soon as the filter it was computed against
  // changes — adjusted during render (not an effect) per React's "adjusting state when a prop
  // changes" pattern, since it's derived from categoryFilter/range rather than an external system.
  const filterKey = `${categoryFilter}|${range.from}|${range.to}`;
  const [lastFilterKey, setLastFilterKey] = useState(filterKey);
  if (filterKey !== lastFilterKey) {
    setLastFilterKey(filterKey);
    setBulk(null);
  }

  // Reset to the first page whenever the category tile or global date range changes — mirrors
  // the old "Apply"/"Reset" buttons' behavior, just triggered by the new filter sources instead.
  useEffect(() => {
    void setSize(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setSize's identity is stable per SWR
  }, [categoryFilter, range.from, range.to]);

  const pages = data ?? [];
  const items = pages.flatMap((p) => p.data);
  const hasMore = pages.length > 0 ? pages[pages.length - 1].hasMore : false;
  const loadingMore = isValidating && pages.length > 0 && pages.length < size;

  const categoryOf = (t: Transaction): number | "" => overrides[t.id] ?? t.categoryId ?? "";

  const filterLabel = categoryFilter === "uncategorized" ? "Uncategorized" : categoryFilter !== null ? categoryName(categoryFilter) : null;

  async function putCategory(id: string, categoryId: number) {
    setOverrides((prev) => ({ ...prev, [id]: categoryId }));
    await apiClient.put(`/transactions/${id}/category`, { category_id: categoryId });
  }

  async function correctCategory(t: Transaction, categoryId: number) {
    setCorrectionError(null);
    try {
      await putCategory(t.id, categoryId);
      // Offer to apply the same category to this payee's other transactions in the list.
      const payee = t.recipientName;
      if (payee) {
        const matches = items.filter(
          (o) => o.id !== t.id && o.recipientName === payee && (overrides[o.id] ?? o.categoryId) !== categoryId,
        );
        setBulk(matches.length > 0 ? { payee, categoryId, ids: matches.map((m) => m.id) } : null);
      }
    } catch {
      setOverrides((prev) => {
        const next = { ...prev };
        delete next[t.id];
        return next;
      });
      setCorrectionError("Could not update the category. Please try again.");
    }
  }

  async function applyBulk() {
    if (!bulk) return;
    setBulkBusy(true);
    setCorrectionError(null);
    try {
      await Promise.all(bulk.ids.map((id) => putCategory(id, bulk.categoryId)));
      setBulk(null);
    } catch {
      setCorrectionError("Could not update all matching transactions. Some may not have changed.");
    } finally {
      setBulkBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      {filterLabel && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-foreground-muted">
            Showing: <span className="font-medium text-foreground">{filterLabel}</span>
          </p>
          <button
            type="button"
            onClick={onClearFilter}
            className="flex items-center gap-1 text-sm font-medium text-foreground-muted transition-colors hover:text-foreground"
          >
            Clear filter
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {correctionError && <ErrorState message={correctionError} />}
      {error && items.length === 0 && <ErrorState message="Could not load transactions." />}

      {bulk && (
        <div className="flex flex-wrap items-center gap-3 rounded-[var(--radius-sm)] border border-brand-200 bg-brand-50 px-4 py-3 text-sm">
          <Users className="size-4 shrink-0 text-brand-700" />
          <span className="flex-1 text-foreground">
            Apply <span className="font-medium">{categoryName(bulk.categoryId)}</span> to {bulk.ids.length} other{" "}
            <span className="font-medium">{bulk.payee}</span> {bulk.ids.length === 1 ? "transaction" : "transactions"}?
          </span>
          <Button size="sm" onClick={applyBulk} disabled={bulkBusy}>
            {bulkBusy ? "Applying…" : `Apply to all`}
          </Button>
          <button type="button" onClick={() => setBulk(null)} aria-label="Dismiss" className="rounded p-1 text-foreground-subtle hover:text-foreground">
            <X className="size-4" />
          </button>
        </div>
      )}

      {isLoading ? (
        <Spinner />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState message="No transactions match this filter." />
        </Card>
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border text-xs text-foreground-subtle">
              <tr>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Payee</th>
                <th className="px-4 py-3 text-right font-medium">Amount</th>
                <th className="px-4 py-3 font-medium">Category</th>
              </tr>
            </thead>
            <tbody>
              {items.map((t) => {
                const catId = categoryOf(t);
                return (
                  <tr key={t.id} className="border-b border-border last:border-0 hover:bg-surface-muted/60">
                    <td className="whitespace-nowrap px-4 py-3 text-foreground-muted">{formatDate(t.transactionDate)}</td>
                    <td className="px-4 py-3">
                      <span className="font-medium text-foreground">{t.recipientName ?? t.upiId ?? t.bank ?? "—"}</span>
                      {(t.upiId || t.bank) && t.recipientName && (
                        <span className="block text-xs text-foreground-subtle">{t.upiId ?? t.bank}</span>
                      )}
                    </td>
                    <td className={cn("tnum whitespace-nowrap px-4 py-3 text-right font-medium", t.amount < 0 ? "text-foreground" : "text-brand-700")}>
                      {formatCurrency(t.amount, true)}
                    </td>
                    <td className="px-4 py-3">
                      <span className="flex items-center gap-2">
                        {typeof catId === "number" && (
                          <span aria-hidden className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: categoryColor(categoryName(catId), catId) }} />
                        )}
                        <Select
                          aria-label={`Category for transaction on ${formatDate(t.transactionDate)}`}
                          value={catId}
                          onChange={(e) => correctCategory(t, Number(e.target.value))}
                          className="h-8 w-40 text-sm"
                        >
                          <option value="" disabled>Uncategorized</option>
                          {categories.map((c) => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                          ))}
                        </Select>
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}

      {hasMore && (
        <div className="flex justify-center">
          <Button type="button" variant="secondary" onClick={() => setSize(size + 1)} disabled={loadingMore}>
            {loadingMore ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
