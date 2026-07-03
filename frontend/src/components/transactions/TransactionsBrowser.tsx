"use client";

import { useState } from "react";
import useSWRInfinite from "swr/infinite";
import { apiClient, swrFetcher } from "@/lib/apiClient";
import { useCategories } from "@/lib/useCategories";
import { formatCurrency, formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";

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
  const { categories } = useCategories();
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<Filters>(EMPTY_FILTERS);
  // Optimistic category corrections overlaid on the fetched data — lets a change reflect
  // immediately without refetching the whole paginated list.
  const [overrides, setOverrides] = useState<Record<string, number>>({});
  const [correctionError, setCorrectionError] = useState<string | null>(null);

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

  function applyFilters(e: React.FormEvent) {
    e.preventDefault();
    setApplied(filters);
    void setSize(1); // reset to the first page for the new filter set
  }

  function resetFilters() {
    setFilters(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    void setSize(1);
  }

  async function correctCategory(id: string, categoryId: number) {
    setOverrides((prev) => ({ ...prev, [id]: categoryId }));
    setCorrectionError(null);
    try {
      await apiClient.put(`/transactions/${id}/category`, { category_id: categoryId });
    } catch {
      setOverrides((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
      setCorrectionError("Could not update the category. Please try again.");
    }
  }

  const categoryOf = (t: Transaction) => overrides[t.id] ?? t.categoryId ?? "";

  return (
    <div className="space-y-4">
      <form onSubmit={applyFilters} className="flex flex-wrap items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-neutral-500">Category</span>
          <select
            aria-label="Filter by category"
            value={filters.category}
            onChange={(e) => setFilters({ ...filters, category: e.target.value })}
            className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
          >
            <option value="">All</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-neutral-500">From</span>
          <input
            type="date"
            aria-label="From date"
            value={filters.from}
            onChange={(e) => setFilters({ ...filters, from: e.target.value })}
            className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
          />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-neutral-500">To</span>
          <input
            type="date"
            aria-label="To date"
            value={filters.to}
            onChange={(e) => setFilters({ ...filters, to: e.target.value })}
            className="rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
          />
        </label>
        <button type="submit" className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white">
          Apply
        </button>
        <button type="button" onClick={resetFilters} className="rounded-md border border-black/15 px-4 py-2 text-sm dark:border-white/15">
          Reset
        </button>
      </form>

      {correctionError && <ErrorState message={correctionError} />}
      {error && items.length === 0 && <ErrorState message="Could not load transactions." />}

      {isLoading ? (
        <Spinner />
      ) : items.length === 0 ? (
        <EmptyState message="No transactions match these filters." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-black/10 text-neutral-500 dark:border-white/10">
              <tr>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Payee</th>
                <th className="px-4 py-3 text-right font-medium">Amount</th>
                <th className="px-4 py-3 font-medium">Category</th>
              </tr>
            </thead>
            <tbody>
              {items.map((t) => (
                <tr key={t.id} className="border-b border-black/5 last:border-0 dark:border-white/5">
                  <td className="whitespace-nowrap px-4 py-3">{formatDate(t.transactionDate)}</td>
                  <td className="px-4 py-3">{t.recipientName ?? t.upiId ?? t.bank ?? "—"}</td>
                  <td className={`whitespace-nowrap px-4 py-3 text-right ${t.amount < 0 ? "" : "text-green-600 dark:text-green-400"}`}>
                    {formatCurrency(t.amount, true)}
                  </td>
                  <td className="px-4 py-3">
                    <select
                      aria-label={`Category for transaction on ${formatDate(t.transactionDate)}`}
                      value={categoryOf(t)}
                      onChange={(e) => correctCategory(t.id, Number(e.target.value))}
                      className="rounded-md border border-black/15 px-2 py-1 text-sm dark:border-white/15 dark:bg-neutral-800"
                    >
                      <option value="" disabled>Uncategorized</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {hasMore && (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={() => setSize(size + 1)}
            disabled={loadingMore}
            className="rounded-md border border-black/15 px-4 py-2 text-sm disabled:opacity-50 dark:border-white/15"
          >
            {loadingMore ? "Loading…" : "Load more"}
          </button>
        </div>
      )}
    </div>
  );
}
