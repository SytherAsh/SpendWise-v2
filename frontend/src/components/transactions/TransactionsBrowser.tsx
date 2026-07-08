"use client";

import { useState } from "react";
import useSWRInfinite from "swr/infinite";
import { Filter, Users, X } from "lucide-react";
import { apiClient, swrFetcher } from "@/lib/apiClient";
import { useCategories } from "@/lib/useCategories";
import { formatCurrency, formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/input";
import { categoryColor } from "@/lib/categories";
import { cn } from "@/lib/cn";

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

interface Filters {
  category: string;
  from: string;
  to: string;
}

interface BulkPrompt {
  payee: string;
  categoryId: number;
  ids: string[];
}

const EMPTY_FILTERS: Filters = { category: "", from: "", to: "" };
const PAGE_SIZE = 50;

function buildPath(filters: Filters, cursor: string | null): string {
  const params = new URLSearchParams();
  params.set("limit", String(PAGE_SIZE));
  if (cursor) params.set("cursor", cursor);
  if (filters.category) params.set("category", filters.category);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  return `/transactions?${params.toString()}`;
}

export function TransactionsBrowser() {
  const { categories, categoryName } = useCategories();
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<Filters>(EMPTY_FILTERS);
  // Optimistic category corrections overlaid on the fetched data — lets a change reflect
  // immediately without refetching the whole paginated list.
  const [overrides, setOverrides] = useState<Record<string, number>>({});
  const [correctionError, setCorrectionError] = useState<string | null>(null);
  const [bulk, setBulk] = useState<BulkPrompt | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);

  const getKey = (pageIndex: number, previous: TransactionListResponse | null) => {
    if (previous && !previous.hasMore) return null;
    const cursor = pageIndex === 0 ? null : previous?.nextCursor ?? null;
    return buildPath(applied, cursor);
  };

  const { data, error, isLoading, size, setSize, isValidating } = useSWRInfinite<TransactionListResponse>(
    getKey,
    swrFetcher,
    { revalidateFirstPage: false },
  );

  const pages = data ?? [];
  const items = pages.flatMap((p) => p.data);
  const hasMore = pages.length > 0 ? pages[pages.length - 1].hasMore : false;
  const loadingMore = isValidating && pages.length > 0 && pages.length < size;

  const categoryOf = (t: Transaction): number | "" => overrides[t.id] ?? t.categoryId ?? "";

  function applyFilters(e: React.FormEvent) {
    e.preventDefault();
    setApplied(filters);
    setBulk(null);
    void setSize(1); // reset to the first page for the new filter set
  }

  function resetFilters() {
    setFilters(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setBulk(null);
    void setSize(1);
  }

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
      <form onSubmit={applyFilters} className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-foreground-muted">Category</span>
          <Select
            aria-label="Filter by category"
            value={filters.category}
            onChange={(e) => setFilters({ ...filters, category: e.target.value })}
            className="w-44"
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </Select>
        </label>
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-foreground-muted">From</span>
          <Input type="date" aria-label="From date" value={filters.from} onChange={(e) => setFilters({ ...filters, from: e.target.value })} className="w-40" />
        </label>
        <label className="text-sm">
          <span className="mb-1.5 block font-medium text-foreground-muted">To</span>
          <Input type="date" aria-label="To date" value={filters.to} onChange={(e) => setFilters({ ...filters, to: e.target.value })} className="w-40" />
        </label>
        <Button type="submit" className="gap-1.5">
          <Filter className="size-4" />
          Apply
        </Button>
        <Button type="button" variant="secondary" onClick={resetFilters}>
          Reset
        </Button>
      </form>

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
          <EmptyState message="No transactions match these filters." />
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
