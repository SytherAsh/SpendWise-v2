"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import useSWRInfinite from "swr/infinite";
import { ChevronDown, ChevronRight, Users, X } from "lucide-react";
import { apiClient, swrFetcher } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useContacts, groupTransactions, relationshipLabel, RELATIONSHIP_TYPES, type Contact, type TransactionGroup } from "@/lib/contacts";
import { useDateRange } from "@/lib/date-range";
import { formatCurrency, formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { categoryColor } from "@/lib/categories";
import { cn } from "@/lib/cn";
import type { CategorySelection } from "./CategorySummaryGrid";

interface Transaction {
  id: string;
  transactionDate: string;
  amount: number;
  recipientName: string | null;
  recipientCanonical: string | null;
  upiId: string | null;
  bank: string | null;
  note: string | null;
  categoryId: number | null;
}

/** Deduplicated payee name once the canonicalization job has run, else the raw one. */
function payeeName(t: Pick<Transaction, "recipientCanonical" | "recipientName">): string | null {
  return t.recipientCanonical ?? t.recipientName;
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
/** Bounded, non-paginated read backing the grouped view (docs/api.md "direction") — fine at
 * personal-finance volumes; see CategorySummaryGrid's RECEIVED_TILE_LIMIT for the same tradeoff. */
const GROUP_FETCH_LIMIT = 500;

function buildPath(
  categoryFilter: CategorySelection,
  from: string,
  to: string,
  cursor: string | null,
  limit: number = PAGE_SIZE,
): string {
  const params = new URLSearchParams();
  params.set("limit", String(limit));
  if (cursor) params.set("cursor", cursor);
  if (categoryFilter === "received") {
    params.set("direction", "credit");
  } else if (categoryFilter !== null) {
    params.set("category", String(categoryFilter));
  }
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
  const { contacts } = useContacts();
  const isGrouped = categoryFilter !== null;
  // Subcategory tagging (ADR-010) only makes sense for Transfers-shaped views — a payee's
  // relationship (family/friend/self/settlement) is meaningful for money moving between
  // people, not for a spend category like Groceries or Food.
  const transfersCategoryId = categories.find((c) => c.name.toLowerCase() === "transfers")?.id ?? null;
  const isTransferLike = categoryFilter === "received" || (transfersCategoryId != null && categoryFilter === transfersCategoryId);

  // Optimistic category corrections overlaid on the fetched data — lets a change reflect
  // immediately without refetching the whole list.
  const [overrides, setOverrides] = useState<Record<string, number>>({});
  const [correctionError, setCorrectionError] = useState<string | null>(null);
  const [bulk, setBulk] = useState<BulkPrompt | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [subFilter, setSubFilter] = useState<Contact["relationshipType"] | "other" | null>(null);

  // Flat, paginated view — active only when no tile is selected (categoryFilter === null).
  const getKey = (pageIndex: number, previous: TransactionListResponse | null) => {
    if (isGrouped) return null;
    if (previous && !previous.hasMore) return null;
    const cursor = pageIndex === 0 ? null : previous?.nextCursor ?? null;
    return buildPath(categoryFilter, range.from, range.to, cursor);
  };
  const {
    data: flatPages,
    error: flatError,
    isLoading: flatLoading,
    size,
    setSize,
    isValidating,
  } = useSWRInfinite<TransactionListResponse>(getKey, swrFetcher, { revalidateFirstPage: false });

  // Grouped, bounded view — active only when a tile is selected. Fetches every matching
  // transaction at once (not just one page) so group sums/counts are accurate.
  const groupedKey = isGrouped ? buildPath(categoryFilter, range.from, range.to, null, GROUP_FETCH_LIMIT) : null;
  const {
    data: groupedPage,
    error: groupedError,
    isLoading: groupedLoading,
    refresh: refreshGrouped,
  } = useApi<TransactionListResponse>(groupedKey);

  // Dismiss any pending bulk-correction prompt as soon as the filter it was computed against
  // changes — adjusted during render (not an effect) per React's "adjusting state when a prop
  // changes" pattern, since it's derived from categoryFilter/range rather than an external system.
  const filterKey = `${categoryFilter}|${range.from}|${range.to}`;
  const [lastFilterKey, setLastFilterKey] = useState(filterKey);
  if (filterKey !== lastFilterKey) {
    setLastFilterKey(filterKey);
    setBulk(null);
    setExpanded(new Set());
    setSubFilter(null);
  }

  // Reset to the first page whenever the category tile or global date range changes — mirrors
  // the old "Apply"/"Reset" buttons' behavior, just triggered by the new filter sources instead.
  useEffect(() => {
    void setSize(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setSize's identity is stable per SWR
  }, [categoryFilter, range.from, range.to]);

  const flatItems = (flatPages ?? []).flatMap((p) => p.data);
  const flatHasMore = flatPages && flatPages.length > 0 ? flatPages[flatPages.length - 1].hasMore : false;
  const loadingMore = isValidating && (flatPages?.length ?? 0) > 0 && (flatPages?.length ?? 0) < size;

  const groupedItems = groupedPage?.data ?? [];
  const groups = useMemo(() => groupTransactions(groupedItems, contacts), [groupedItems, contacts]);
  const transactionsById = useMemo(() => new Map(groupedItems.map((t) => [t.id, t])), [groupedItems]);
  const filteredGroups = useMemo(() => {
    if (!isTransferLike || subFilter === null) return groups;
    if (subFilter === "other") return groups.filter((g) => g.relationshipType === null);
    return groups.filter((g) => g.relationshipType === subFilter);
  }, [groups, isTransferLike, subFilter]);

  const items = isGrouped ? groupedItems : flatItems;
  const isLoading = isGrouped ? groupedLoading : flatLoading;
  const hasError = isGrouped ? groupedError : flatError;

  const categoryOf = (t: Transaction): number | "" => overrides[t.id] ?? t.categoryId ?? "";

  const filterLabel =
    categoryFilter === "uncategorized"
      ? "Uncategorized"
      : categoryFilter === "received"
        ? "Received"
        : categoryFilter !== null
          ? categoryName(categoryFilter)
          : null;

  async function putCategory(id: string, categoryId: number) {
    setOverrides((prev) => ({ ...prev, [id]: categoryId }));
    await apiClient.put(`/transactions/${id}/category`, { category_id: categoryId });
  }

  async function correctCategory(t: Transaction, categoryId: number) {
    setCorrectionError(null);
    try {
      await putCategory(t.id, categoryId);
      // Offer to apply the same category to this payee's other transactions in the list. Match on
      // the canonical (deduplicated) payee name when available, so spelling variants of one payee
      // are treated as the same payee here too; falls back to the raw name before canonicalization
      // has run.
      const payee = payeeName(t);
      if (payee) {
        const matches = items.filter(
          (o) => o.id !== t.id && payeeName(o) === payee && (overrides[o.id] ?? o.categoryId) !== categoryId,
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

  function toggleExpand(key: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
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
      {hasError && items.length === 0 && (
        <ErrorState message="Could not load transactions." onRetry={isGrouped ? refreshGrouped : undefined} />
      )}

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
      ) : isGrouped ? (
        <div className="space-y-3">
          {isTransferLike && (
            <SubcategoryChips groups={groups} selected={subFilter} onSelect={setSubFilter} />
          )}
          {filteredGroups.length === 0 ? (
            <Card>
              <EmptyState message="No transactions match this tag." />
            </Card>
          ) : (
            <div className="space-y-2">
              {filteredGroups.map((group) => (
                <GroupCard
                  key={group.key}
                  group={group}
                  expanded={expanded.has(group.key)}
                  onToggle={() => toggleExpand(group.key)}
                  transactionsById={transactionsById}
                  categories={categories}
                  categoryName={categoryName}
                  categoryOf={categoryOf}
                  onCorrect={correctCategory}
                />
              ))}
            </div>
          )}
        </div>
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <TransactionTableHead />
            <tbody>
              {flatItems.map((t) => (
                <TransactionRow
                  key={t.id}
                  t={t}
                  catId={categoryOf(t)}
                  categories={categories}
                  categoryName={categoryName}
                  onCorrect={(categoryId) => correctCategory(t, categoryId)}
                />
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {!isGrouped && flatHasMore && (
        <div className="flex justify-center">
          <Button type="button" variant="secondary" onClick={() => setSize(size + 1)} disabled={loadingMore}>
            {loadingMore ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}

function TransactionTableHead() {
  return (
    <thead className="border-b border-border text-xs text-foreground-subtle">
      <tr>
        <th className="px-4 py-3 font-medium">Date</th>
        <th className="px-4 py-3 font-medium">Payee</th>
        <th className="px-4 py-3 text-right font-medium">Amount</th>
        <th className="px-4 py-3 font-medium">Category</th>
      </tr>
    </thead>
  );
}

function TransactionRow({
  t,
  catId,
  categories,
  categoryName,
  onCorrect,
}: {
  t: Transaction;
  catId: number | "";
  categories: Array<{ id: number; name: string }>;
  categoryName: (id: number) => string;
  onCorrect: (categoryId: number) => void;
}) {
  return (
    <tr className="border-b border-border last:border-0 hover:bg-surface-muted/60">
      <td className="whitespace-nowrap px-4 py-3 text-foreground-muted">{formatDate(t.transactionDate)}</td>
      <td className="px-4 py-3">
        <span className="font-medium text-foreground">{payeeName(t) ?? t.upiId ?? t.bank ?? "—"}</span>
        {(t.upiId || t.bank) && payeeName(t) && (
          <span className="block text-xs text-foreground-subtle">{t.upiId ?? t.bank}</span>
        )}
      </td>
      <td
        className={cn(
          "tnum whitespace-nowrap px-4 py-3 text-right font-medium",
          t.amount < 0 ? "text-[var(--color-danger)]" : "text-brand-700 dark:text-brand-300",
        )}
      >
        {formatCurrency(t.amount, true)}
      </td>
      <td className="px-4 py-3">
        <span className="flex items-center gap-2">
          {typeof catId === "number" && (
            <span
              aria-hidden
              className="size-5 shrink-0 rounded-[var(--radius-sm)]"
              style={{ backgroundColor: categoryColor(categoryName(catId), catId) }}
            />
          )}
          <Select
            aria-label={`Category for transaction on ${formatDate(t.transactionDate)}`}
            value={catId}
            onChange={(e) => onCorrect(Number(e.target.value))}
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
}

function GroupCard({
  group,
  expanded,
  onToggle,
  transactionsById,
  categories,
  categoryName,
  categoryOf,
  onCorrect,
}: {
  group: TransactionGroup;
  expanded: boolean;
  onToggle: () => void;
  transactionsById: Map<string, Transaction>;
  categories: Array<{ id: number; name: string }>;
  categoryName: (id: number) => string;
  categoryOf: (t: Transaction) => number | "";
  onCorrect: (t: Transaction, categoryId: number) => void;
}) {
  const transactions = group.transactionIds.map((id) => transactionsById.get(id)).filter((t): t is Transaction => t != null);

  return (
    <Card className="p-0">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        className="flex w-full items-center gap-3 px-4 py-3 text-left"
      >
        {expanded ? (
          <ChevronDown className="size-4 shrink-0 text-foreground-subtle" />
        ) : (
          <ChevronRight className="size-4 shrink-0 text-foreground-subtle" />
        )}
        <span className="flex-1 truncate font-medium text-foreground">{group.label}</span>
        {group.relationshipType && <Badge tone="brand">{relationshipLabel(group.relationshipType)}</Badge>}
        <span className="text-xs text-foreground-subtle">
          {group.count} {group.count === 1 ? "txn" : "txns"}
        </span>
        <span
          className={cn(
            "tnum whitespace-nowrap text-sm font-semibold",
            group.amount < 0 ? "text-[var(--color-danger)]" : "text-brand-700 dark:text-brand-300",
          )}
        >
          {formatCurrency(group.amount, true)}
        </span>
      </button>
      {expanded && (
        <div className="overflow-x-auto border-t border-border">
          <table className="w-full text-left text-sm">
            <TransactionTableHead />
            <tbody>
              {transactions.map((t) => (
                <TransactionRow
                  key={t.id}
                  t={t}
                  catId={categoryOf(t)}
                  categories={categories}
                  categoryName={categoryName}
                  onCorrect={(categoryId) => onCorrect(t, categoryId)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

/** Filter chips for Transfers/Received groups by counterparty relationship (ADR-010) — only
 * rendered for Transfers-shaped views; only tags with at least one matching group appear. */
function SubcategoryChips({
  groups,
  selected,
  onSelect,
}: {
  groups: TransactionGroup[];
  selected: Contact["relationshipType"] | "other" | null;
  onSelect: (value: Contact["relationshipType"] | "other" | null) => void;
}) {
  const counts = new Map<string, number>();
  for (const g of groups) {
    const key = g.relationshipType ?? "other";
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  const options: Array<{ value: Contact["relationshipType"] | "other"; label: string }> = [
    ...RELATIONSHIP_TYPES,
    { value: "other", label: "Other" },
  ];
  const visible = options.filter((o) => (counts.get(o.value) ?? 0) > 0);
  if (visible.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-2">
      <SubChip active={selected === null} onClick={() => onSelect(null)}>
        All
      </SubChip>
      {visible.map((o) => (
        <SubChip key={o.value} active={selected === o.value} onClick={() => onSelect(selected === o.value ? null : o.value)}>
          {o.label} <span className="opacity-70">({counts.get(o.value)})</span>
        </SubChip>
      ))}
    </div>
  );
}

function SubChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "border-brand-600 bg-brand-50 text-brand-800"
          : "border-border-strong text-foreground-muted hover:border-border-strong hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}
