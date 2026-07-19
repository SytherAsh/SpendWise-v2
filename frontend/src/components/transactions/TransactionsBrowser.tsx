"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import useSWRInfinite from "swr/infinite";
import { Check, ChevronDown, ChevronRight, Info, Pencil, Trash2, X } from "lucide-react";
import { apiClient, swrFetcher } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useContacts, groupTransactions, relationshipLabel, RELATIONSHIP_TYPES, type Contact, type TransactionGroup } from "@/lib/contacts";
import { useDateRange } from "@/lib/date-range";
import { formatCurrency, formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";
import { Button } from "@/components/ui/button";
import { CategorySelect } from "@/components/ui/category-select";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
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

/** Full shape of `GET /transactions/:id` — a superset of the list row above, fetched lazily
 * only when the detail modal opens (the list itself doesn't need these fields). */
interface TransactionDetail extends Transaction {
  debit: number;
  credit: number;
  balance: number | null;
  transactionMode: string | null;
  drCrIndicator: string;
  transactionId: string;
  source: string;
  parsedAt: string;
  confidenceScore: number | null;
  assignedBy: string | null;
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
  /** The transaction whose correction triggered this prompt — the prompt renders inline on
   * this row (at the row's own action slot) rather than as a page-level banner, so a user
   * correcting a category sees the follow-up offer right where they just acted. */
  sourceId: string;
  payee: string;
  categoryId: number;
  ids: string[];
}

/** A proactive group-header action (category or payee rename) awaiting explicit confirmation
 * before it's applied to every transaction in the group — unlike BulkPrompt above (which applies
 * a single-row correction to the rest of a payee's transactions), this acts on a whole GroupCard's
 * transactionIds at once and is a wider-blast-radius action, so it always confirms first. */
interface GroupPending {
  groupKey: string;
  transactionIds: string[];
  label: string;
  apply: () => Promise<void>;
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
  search: string = "",
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
  if (search) params.set("search", search);
  return `/transactions?${params.toString()}`;
}

export function TransactionsBrowser({
  categoryFilter,
  onClearFilter,
  search = "",
}: {
  categoryFilter: CategorySelection;
  onClearFilter: () => void;
  /** Free-text search (debounced by the caller) — matches payee, UPI id, note, or category name. */
  search?: string;
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
  // Optimistic payee-name corrections (ADR-014), same shape as the category overrides above.
  const [payeeOverrides, setPayeeOverrides] = useState<Record<string, string>>({});
  const [editingPayeeId, setEditingPayeeId] = useState<string | null>(null);
  const [correctionError, setCorrectionError] = useState<string | null>(null);
  const [bulk, setBulk] = useState<BulkPrompt | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [groupPending, setGroupPending] = useState<GroupPending | null>(null);
  const [groupBusy, setGroupBusy] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [subFilter, setSubFilter] = useState<Contact["relationshipType"] | "other" | null>(null);
  const [detailTx, setDetailTx] = useState<Transaction | null>(null);
  const [deleteTx, setDeleteTx] = useState<Transaction | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Flat, paginated view — active only when no tile is selected (categoryFilter === null).
  const getKey = (pageIndex: number, previous: TransactionListResponse | null) => {
    if (isGrouped) return null;
    if (previous && !previous.hasMore) return null;
    const cursor = pageIndex === 0 ? null : previous?.nextCursor ?? null;
    return buildPath(categoryFilter, range.from, range.to, cursor, search);
  };
  const {
    data: flatPages,
    error: flatError,
    isLoading: flatLoading,
    size,
    setSize,
    isValidating,
    mutate: mutateFlat,
  } = useSWRInfinite<TransactionListResponse>(getKey, swrFetcher, { revalidateFirstPage: false });

  // Grouped, bounded view — active only when a tile is selected. Fetches every matching
  // transaction at once (not just one page) so group sums/counts are accurate.
  const groupedKey = isGrouped
    ? buildPath(categoryFilter, range.from, range.to, null, search, GROUP_FETCH_LIMIT)
    : null;
  const {
    data: groupedPage,
    error: groupedError,
    isLoading: groupedLoading,
    refresh: refreshGrouped,
  } = useApi<TransactionListResponse>(groupedKey);

  // Dismiss any pending bulk-correction prompt as soon as the filter it was computed against
  // changes — adjusted during render (not an effect) per React's "adjusting state when a prop
  // changes" pattern, since it's derived from categoryFilter/range rather than an external system.
  const filterKey = `${categoryFilter}|${range.from}|${range.to}|${search}`;
  const [lastFilterKey, setLastFilterKey] = useState(filterKey);
  if (filterKey !== lastFilterKey) {
    setLastFilterKey(filterKey);
    setBulk(null);
    setGroupPending(null);
    setEditingPayeeId(null);
    setExpanded(new Set());
    setSubFilter(null);
  }

  // Reset to the first page whenever the category tile, global date range, or search query
  // changes — mirrors the old "Apply"/"Reset" buttons' behavior, just triggered by the new
  // filter sources instead.
  useEffect(() => {
    void setSize(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setSize's identity is stable per SWR
  }, [categoryFilter, range.from, range.to, search]);

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
  const payeeOf = (t: Transaction): string | null => payeeOverrides[t.id] ?? payeeName(t) ?? t.upiId ?? t.bank ?? null;

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
        setBulk(matches.length > 0 ? { sourceId: t.id, payee, categoryId, ids: matches.map((m) => m.id) } : null);
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

  async function putPayee(id: string, canonicalName: string) {
    setPayeeOverrides((prev) => ({ ...prev, [id]: canonicalName }));
    await apiClient.put(`/transactions/${id}/payee`, { canonical_name: canonicalName });
    // Fires as a second, independent, best-effort request (ADR-020) — never lets a
    // recategorize failure surface as a payee-rename failure, since the rename itself already
    // succeeded above. See mergePayees.ts#resolveMergeGroup for the identical pattern and the
    // module-boundary reasoning (why this is frontend-orchestrated rather than a backend call).
    const original = transactionsById.get(id);
    if (original) {
      apiClient.post("/categorization/recategorize", { recipient_name: original.recipientName, upi_id: original.upiId }).catch(() => {});
    }
  }

  async function correctPayee(t: Transaction, canonicalName: string) {
    const trimmed = canonicalName.trim();
    if (!trimmed || trimmed === payeeOf(t)) {
      setEditingPayeeId(null);
      return;
    }
    setCorrectionError(null);
    try {
      await putPayee(t.id, trimmed);
      setEditingPayeeId(null);
    } catch {
      setPayeeOverrides((prev) => {
        const next = { ...prev };
        delete next[t.id];
        return next;
      });
      setCorrectionError("Could not update the payee name. Please try again.");
    }
  }

  // Proactive group-header actions (ADR-014 + the group category control below) — unlike the
  // per-row correction above, these act on every transaction in a GroupCard at once, so they
  // stage into groupPending and wait for an explicit confirm click rather than applying instantly.
  function requestGroupCategoryChange(group: TransactionGroup, categoryId: number) {
    setGroupPending({
      groupKey: group.key,
      transactionIds: group.transactionIds,
      label: `Change all ${group.transactionIds.length} transactions to ${categoryName(categoryId)}?`,
      apply: () => Promise.all(group.transactionIds.map((id) => putCategory(id, categoryId))).then(() => undefined),
    });
  }

  function requestGroupPayeeRename(group: TransactionGroup, canonicalName: string) {
    const trimmed = canonicalName.trim();
    if (!trimmed) return;
    setGroupPending({
      groupKey: group.key,
      transactionIds: group.transactionIds,
      label: `Rename all ${group.transactionIds.length} transactions in "${group.label}" to "${trimmed}"?`,
      apply: () => Promise.all(group.transactionIds.map((id) => putPayee(id, trimmed))).then(() => undefined),
    });
  }

  async function confirmGroupPending() {
    if (!groupPending) return;
    setGroupBusy(true);
    setCorrectionError(null);
    try {
      await groupPending.apply();
      setGroupPending(null);
    } catch {
      setCorrectionError("Could not update all transactions in the group. Some may not have changed.");
    } finally {
      setGroupBusy(false);
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

  async function confirmDelete() {
    if (!deleteTx) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await apiClient.delete(`/transactions/${deleteTx.id}`);
      setDeleteTx(null);
      if (isGrouped) {
        refreshGrouped();
      } else {
        void mutateFlat();
      }
    } catch {
      setDeleteError("Could not delete this transaction. Please try again.");
    } finally {
      setDeleteBusy(false);
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
      {hasError && items.length === 0 && (
        <ErrorState message="Could not load transactions." onRetry={isGrouped ? refreshGrouped : undefined} />
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
                  bulk={bulk}
                  bulkBusy={bulkBusy}
                  onApplyBulk={applyBulk}
                  onDismissBulk={() => setBulk(null)}
                  payeeOf={payeeOf}
                  editingPayeeId={editingPayeeId}
                  onStartEditPayee={setEditingPayeeId}
                  onCorrectPayee={correctPayee}
                  onRequestGroupCategory={requestGroupCategoryChange}
                  onRequestGroupPayeeRename={requestGroupPayeeRename}
                  groupPending={groupPending?.groupKey === group.key ? groupPending : null}
                  groupBusy={groupBusy}
                  onConfirmGroupPending={confirmGroupPending}
                  onCancelGroupPending={() => setGroupPending(null)}
                  onInfo={setDetailTx}
                  onDelete={setDeleteTx}
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
                  bulk={bulk?.sourceId === t.id ? bulk : undefined}
                  bulkBusy={bulkBusy}
                  onApplyBulk={applyBulk}
                  onDismissBulk={() => setBulk(null)}
                  payeeLabel={payeeOf(t)}
                  isEditingPayee={editingPayeeId === t.id}
                  onStartEditPayee={() => setEditingPayeeId(t.id)}
                  onCancelEditPayee={() => setEditingPayeeId(null)}
                  onCorrectPayee={(name) => correctPayee(t, name)}
                  onInfo={() => setDetailTx(t)}
                  onDelete={() => setDeleteTx(t)}
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

      <TransactionDetailDialog transaction={detailTx} onClose={() => setDetailTx(null)} categoryName={categoryName} />

      <Dialog open={deleteTx != null} onOpenChange={(open) => !open && !deleteBusy && setDeleteTx(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete this transaction?</DialogTitle>
            {deleteTx && (
              <DialogDescription>
                {formatCurrency(deleteTx.amount, true)} {deleteTx.amount < 0 ? "to" : "from"}{" "}
                {payeeName(deleteTx) ?? deleteTx.upiId ?? deleteTx.bank ?? "this payee"} on{" "}
                {formatDate(deleteTx.transactionDate)} will be removed from your transactions and totals.
              </DialogDescription>
            )}
          </DialogHeader>
          {deleteError && <ErrorState message={deleteError} />}
          <div className="mt-1 flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setDeleteTx(null)} disabled={deleteBusy}>
              Cancel
            </Button>
            <Button type="button" variant="danger" onClick={confirmDelete} disabled={deleteBusy}>
              {deleteBusy ? "Deleting…" : "Delete transaction"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
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
        <th className="px-4 py-3 font-medium">
          <span className="sr-only">Bulk actions</span>
        </th>
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
  bulk,
  bulkBusy,
  onApplyBulk,
  onDismissBulk,
  payeeLabel,
  isEditingPayee,
  onStartEditPayee,
  onCancelEditPayee,
  onCorrectPayee,
  onInfo,
  onDelete,
}: {
  t: Transaction;
  catId: number | "";
  categories: Array<{ id: number; name: string }>;
  categoryName: (id: number) => string;
  onCorrect: (categoryId: number) => void;
  /** Set only on the row whose correction produced this prompt — renders inline here instead
   * of as a page-level banner, right next to the change the user just made. */
  bulk?: BulkPrompt;
  bulkBusy?: boolean;
  onApplyBulk?: () => void;
  onDismissBulk?: () => void;
  /** ADR-014 per-row payee rename — optional so GroupCard's own header controls don't force
   * every call site to wire these up. */
  payeeLabel?: string | null;
  isEditingPayee?: boolean;
  onStartEditPayee?: () => void;
  onCancelEditPayee?: () => void;
  onCorrectPayee?: (canonicalName: string) => void;
  onInfo?: () => void;
  onDelete?: () => void;
}) {
  return (
    <tr className="border-b border-border last:border-0 hover:bg-surface-muted/60">
      <td className="whitespace-nowrap px-4 py-3 text-foreground-muted">{formatDate(t.transactionDate)}</td>
      <td className="px-4 py-3">
        {isEditingPayee ? (
          <PayeeNameEditor
            initialValue={payeeLabel ?? ""}
            onSubmit={(value) => onCorrectPayee?.(value)}
            onCancel={() => onCancelEditPayee?.()}
          />
        ) : (
          <div className="group flex items-center gap-1.5">
            <span className="font-medium text-foreground">{payeeLabel ?? "—"}</span>
            {payeeLabel && onStartEditPayee && (
              <button
                type="button"
                onClick={onStartEditPayee}
                aria-label={`Rename payee ${payeeLabel}`}
                className="rounded p-0.5 text-foreground-muted opacity-0 transition-opacity hover:text-brand-600 group-hover:opacity-100 dark:hover:text-brand-300"
              >
                <Pencil className="size-3" />
              </button>
            )}
          </div>
        )}
        {(t.upiId || t.bank) && payeeLabel && !isEditingPayee && (
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
        <CategorySelect
          ariaLabel={`Category for transaction on ${formatDate(t.transactionDate)}`}
          categories={categories}
          value={catId}
          onChange={onCorrect}
          className="w-44"
        />
      </td>
      <td className="px-4 py-3 text-right">
        <div className="flex items-center justify-end gap-2 whitespace-nowrap">
          {bulk && (
            <>
              <span className="text-xs text-foreground-subtle">
                Apply to {bulk.ids.length} other {bulk.payee}?
              </span>
              <Button size="sm" onClick={onApplyBulk} disabled={bulkBusy}>
                {bulkBusy ? "Applying…" : "Apply to all"}
              </Button>
              <button
                type="button"
                onClick={onDismissBulk}
                aria-label="Dismiss"
                className="rounded p-1 text-foreground-subtle hover:text-foreground"
              >
                <X className="size-4" />
              </button>
            </>
          )}
          {onInfo && (
            <button
              type="button"
              onClick={onInfo}
              aria-label={`View details for transaction on ${formatDate(t.transactionDate)}`}
              className="rounded p-1 text-foreground-muted hover:bg-surface-muted hover:text-brand-600 dark:hover:text-brand-300"
            >
              <Info className="size-4" />
            </button>
          )}
          {onDelete && (
            <button
              type="button"
              onClick={onDelete}
              aria-label={`Delete transaction on ${formatDate(t.transactionDate)}`}
              className="rounded p-1 text-foreground-muted hover:bg-surface-muted hover:text-[var(--color-danger)]"
            >
              <Trash2 className="size-4" />
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}

/** Inline payee-rename control (ADR-014) shared by TransactionRow's per-row edit and GroupCard's
 * whole-group rename — Enter/the check button submits, Escape/the X button cancels without
 * committing. Deliberately does not act on blur: losing focus (e.g. to click Save/Cancel) should
 * neither silently commit nor discard, only the explicit controls below should. */
function PayeeNameEditor({
  initialValue,
  onSubmit,
  onCancel,
}: {
  initialValue: string;
  onSubmit: (value: string) => void;
  onCancel: () => void;
}) {
  const [value, setValue] = useState(initialValue);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(value);
      }}
      className="flex items-center gap-1.5"
    >
      <Input
        autoFocus
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Escape") onCancel();
        }}
        aria-label="Payee name"
        className="h-8 flex-1 text-sm"
      />
      <button
        type="submit"
        aria-label="Save"
        className="shrink-0 rounded p-1 text-brand-700 hover:bg-surface-muted dark:text-brand-300"
      >
        <Check className="size-4" />
      </button>
      <button
        type="button"
        onClick={onCancel}
        aria-label="Cancel"
        className="shrink-0 rounded p-1 text-foreground-subtle hover:text-foreground"
      >
        <X className="size-4" />
      </button>
    </form>
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
  bulk,
  bulkBusy,
  onApplyBulk,
  onDismissBulk,
  payeeOf,
  editingPayeeId,
  onStartEditPayee,
  onCorrectPayee,
  onRequestGroupCategory,
  onRequestGroupPayeeRename,
  groupPending,
  groupBusy,
  onConfirmGroupPending,
  onCancelGroupPending,
  onInfo,
  onDelete,
}: {
  group: TransactionGroup;
  expanded: boolean;
  onToggle: () => void;
  transactionsById: Map<string, Transaction>;
  categories: Array<{ id: number; name: string }>;
  categoryName: (id: number) => string;
  categoryOf: (t: Transaction) => number | "";
  onCorrect: (t: Transaction, categoryId: number) => void;
  bulk?: BulkPrompt | null;
  bulkBusy?: boolean;
  onApplyBulk?: () => void;
  onDismissBulk?: () => void;
  payeeOf: (t: Transaction) => string | null;
  editingPayeeId: string | null;
  onStartEditPayee: (id: string | null) => void;
  onCorrectPayee: (t: Transaction, canonicalName: string) => void;
  /** Proactive group-header actions (this GroupCard's own controls, distinct from the per-row
   * "keep the existing individual change, add a group-level one" request) — these stage into
   * groupPending and require the confirm bar below rather than applying instantly. */
  onRequestGroupCategory: (group: TransactionGroup, categoryId: number) => void;
  onRequestGroupPayeeRename: (group: TransactionGroup, canonicalName: string) => void;
  groupPending: GroupPending | null;
  groupBusy: boolean;
  onConfirmGroupPending: () => void;
  onCancelGroupPending: () => void;
  onInfo: (t: Transaction) => void;
  onDelete: (t: Transaction) => void;
}) {
  const [renamingGroup, setRenamingGroup] = useState(false);
  const transactions = group.transactionIds.map((id) => transactionsById.get(id)).filter((t): t is Transaction => t != null);

  return (
    <Card className="p-0">
      <div className="flex w-full items-center gap-3 px-4 py-3">
        <button
          type="button"
          onClick={onToggle}
          aria-expanded={expanded}
          aria-label={expanded ? "Collapse group" : "Expand group"}
          className="shrink-0 text-foreground-muted hover:text-brand-600 dark:hover:text-brand-300"
        >
          {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
        </button>

        {renamingGroup ? (
          <div className="min-w-0 flex-1">
            <PayeeNameEditor
              initialValue={group.label}
              onSubmit={(value) => {
                onRequestGroupPayeeRename(group, value);
                setRenamingGroup(false);
              }}
              onCancel={() => setRenamingGroup(false)}
            />
          </div>
        ) : (
          <button type="button" onClick={onToggle} className="min-w-0 flex-1 truncate text-left font-medium text-foreground">
            {group.label}
          </button>
        )}

        {group.relationshipType && <Badge tone="brand">{relationshipLabel(group.relationshipType)}</Badge>}
        <span className="tnum shrink-0 text-xs font-medium text-foreground-muted">
          {group.count} {group.count === 1 ? "txn" : "txns"}
        </span>
        <span
          className={cn(
            "tnum shrink-0 whitespace-nowrap text-sm font-semibold",
            group.amount < 0 ? "text-[var(--color-danger)]" : "text-brand-700 dark:text-brand-300",
          )}
        >
          {formatCurrency(group.amount, true)}
        </span>

        {!renamingGroup && (
          <div className="flex shrink-0 items-center gap-2 border-l border-border pl-3">
            <button
              type="button"
              onClick={() => setRenamingGroup(true)}
              aria-label={`Rename all transactions in ${group.label}`}
              className="rounded p-1.5 text-foreground-muted hover:bg-surface-muted hover:text-brand-600 dark:hover:text-brand-300"
            >
              <Pencil className="size-3.5" />
            </button>
            <CategorySelect
              ariaLabel={`Change category for all transactions in ${group.label}`}
              categories={categories}
              value=""
              placeholder="Change all…"
              onChange={(categoryId) => onRequestGroupCategory(group, categoryId)}
              variant="action"
              className="w-36"
            />
          </div>
        )}
      </div>

      {groupPending && (
        <div className="flex items-center justify-between gap-2 border-t border-brand-500/30 bg-surface-muted px-4 py-2 text-sm">
          <span className="font-medium text-foreground">{groupPending.label}</span>
          <div className="flex shrink-0 items-center gap-2">
            <Button size="sm" onClick={onConfirmGroupPending} disabled={groupBusy}>
              {groupBusy ? "Applying…" : "Confirm"}
            </Button>
            <button
              type="button"
              onClick={onCancelGroupPending}
              aria-label="Cancel"
              className="rounded p-1 text-foreground-subtle hover:text-foreground"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>
      )}

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
                  bulk={bulk?.sourceId === t.id ? bulk : undefined}
                  bulkBusy={bulkBusy}
                  onApplyBulk={onApplyBulk}
                  onDismissBulk={onDismissBulk}
                  payeeLabel={payeeOf(t)}
                  isEditingPayee={editingPayeeId === t.id}
                  onStartEditPayee={() => onStartEditPayee(t.id)}
                  onCancelEditPayee={() => onStartEditPayee(null)}
                  onCorrectPayee={(name) => onCorrectPayee(t, name)}
                  onInfo={() => onInfo(t)}
                  onDelete={() => onDelete(t)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

/** Full transaction detail modal, opened by the "i" button on any row. Fetches lazily via
 * `GET /transactions/:id` (already returns every field shown here) each time a new transaction
 * is selected, rather than requiring the list to carry these fields up front. */
function TransactionDetailDialog({
  transaction,
  onClose,
  categoryName,
}: {
  transaction: Transaction | null;
  onClose: () => void;
  categoryName: (id: number) => string;
}) {
  // The Dialog shell stays mounted while `transaction` is null so Radix can play its close
  // animation; the fetching body below is keyed by transaction id so each opened transaction gets
  // a fresh mount (loading→fetch→detail) rather than the effect resetting state synchronously,
  // which the react-hooks `set-state-in-effect` rule flags. Behaviour is unchanged: the previous
  // effect also cleared `detail` to null when the dialog closed, so the body was already empty
  // during the close animation.
  return (
    <Dialog open={transaction != null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Transaction details</DialogTitle>
          {transaction && <DialogDescription>{formatDate(transaction.transactionDate)}</DialogDescription>}
        </DialogHeader>
        {transaction && <TransactionDetailBody key={transaction.id} transaction={transaction} categoryName={categoryName} />}
      </DialogContent>
    </Dialog>
  );
}

/** Fetches and renders one transaction's full detail. Mounted only while the dialog is open and
 * keyed by transaction id (see {@link TransactionDetailDialog}), so its fetch effect runs exactly
 * once per opened transaction with fresh initial state — no synchronous state reset in the effect. */
function TransactionDetailBody({
  transaction,
  categoryName,
}: {
  transaction: Transaction;
  categoryName: (id: number) => string;
}) {
  const [detail, setDetail] = useState<TransactionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<TransactionDetail>(`/transactions/${transaction.id}`)
      .then((data) => {
        if (!cancelled) setDetail(data);
      })
      .catch(() => {
        if (!cancelled) setError("Could not load transaction details.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [transaction.id]);

  if (loading) return <Spinner />;
  if (error) return <ErrorState message={error} />;
  if (!detail) return null;

  const payee = detail.recipientCanonical ?? detail.recipientName;

  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
      <DetailField label="Payee" value={payee ?? "—"} />
      <DetailField label="Amount" value={formatCurrency(detail.amount, true)} />
      <DetailField label="UPI ID" value={detail.upiId ?? "—"} />
      <DetailField label="Bank" value={detail.bank ?? "—"} />
      <DetailField label="Balance after" value={detail.balance != null ? formatCurrency(detail.balance, true) : "—"} />
      <DetailField label="Mode" value={detail.transactionMode ?? "—"} />
      <DetailField label="Type" value={detail.drCrIndicator === "CR" ? "Credit" : "Debit"} />
      <DetailField label="Bank reference" value={detail.transactionId} />
      <DetailField label="Source" value={sourceLabel(detail.source)} />
      <DetailField label="Recorded" value={formatDate(detail.parsedAt)} />
      <DetailField label="Category" value={detail.categoryId != null ? categoryName(detail.categoryId) : "Uncategorized"} />
      <DetailField label="Categorized by" value={categorizationLabel(detail)} />
      {detail.note && <DetailField label="Note" value={detail.note} full />}
    </dl>
  );
}

function DetailField({ label, value, full }: { label: string; value: string; full?: boolean }) {
  return (
    <div className={full ? "col-span-2" : undefined}>
      <dt className="text-xs font-medium uppercase tracking-wide text-foreground-subtle">{label}</dt>
      <dd className="mt-0.5 text-foreground">{value}</dd>
    </div>
  );
}

function sourceLabel(source: string): string {
  switch (source) {
    case "sms":
      return "SMS";
    case "bank_statement":
      return "Bank statement";
    case "manual":
      return "Manual entry";
    default:
      return source;
  }
}

function categorizationLabel(detail: TransactionDetail): string {
  if (detail.categoryId == null) return "—";
  if (detail.assignedBy === "ml") {
    return detail.confidenceScore != null
      ? `Automatic (${Math.round(detail.confidenceScore * 100)}% confidence)`
      : "Automatic";
  }
  if (detail.assignedBy === "user") return "Manual";
  return "—";
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
