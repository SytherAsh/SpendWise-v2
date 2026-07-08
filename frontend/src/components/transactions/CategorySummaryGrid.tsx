"use client";

import { useMemo } from "react";
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
import { useApi } from "@/lib/useApi";
import { useCategories } from "@/lib/useCategories";
import { useDateRange } from "@/lib/date-range";
import { categoryColor } from "@/lib/categories";
import { formatCurrency } from "@/lib/format";
import { ErrorState, StaleBanner } from "@/components/shared/ui";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/cn";

export type CategorySelection = number | "uncategorized" | null;

interface CategoryTotalRow {
  categoryId: number | null;
  categoryName: string;
  totalSpend: number;
  totalIncome: number;
  transactionCount: number;
}

interface SummaryOverall {
  totalSpend: number;
  totalIncome: number;
}

interface Tile {
  key: number | "uncategorized";
  name: string;
  icon: LucideIcon;
  color: string;
  amount: number;
  count: number;
  sharePct: number;
}

// The backend seeds `categories.icon` as Google Material Icons identifiers (docs/database.md) —
// there's no Material Icons font loaded in this app, so rendering that string directly just
// prints it as text. Map each known identifier to the equivalent lucide-react icon (already the
// app's icon set everywhere else) instead.
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

function resolveIcon(icon: string | null | undefined): LucideIcon {
  return (icon && MATERIAL_ICON_TO_LUCIDE[icon]) || HelpCircle;
}

const TILE_COUNT_ESTIMATE = 13; // 12 fixed categories + Uncategorized — used only to size the loading skeleton

/**
 * "Where did my money go" summary strip for the Transactions page — every category (plus
 * Uncategorized) as a tile with its spend and share of the period's total. Deliberately not a
 * trends/comparison view (that's Analytics); clicking a tile just filters the list below.
 *
 * Tiles represent spend only — money received (refunds, incoming transfers) is never attributed
 * to a category tile, even if the underlying transaction happens to carry one. It's surfaced
 * instead as the header's separate "money received" figure, and category-filtered views of the
 * list below never include it (see TransactionRepository.listPage).
 */
export function CategorySummaryGrid({
  selected,
  onSelect,
}: {
  selected: CategorySelection;
  onSelect: (value: CategorySelection) => void;
}) {
  const { range } = useDateRange();
  const { categories, isLoading: categoriesLoading } = useCategories();
  const {
    data: rows,
    error,
    isLoading: rowsLoading,
    isStale,
    refresh,
  } = useApi<CategoryTotalRow[]>(`/analytics/categories?from=${range.from}&to=${range.to}`);
  const { data: summary, isLoading: summaryLoading } = useApi<SummaryOverall>(
    `/analytics/summary?from=${range.from}&to=${range.to}`,
  );

  const tiles = useMemo<Tile[]>(() => {
    if (categories.length === 0) return [];
    const byId = new Map(
      (rows ?? []).filter((r) => r.categoryId !== null).map((r) => [r.categoryId as number, r]),
    );
    const uncategorizedRow = (rows ?? []).find((r) => r.categoryId === null);

    const real: Tile[] = categories.map((c) => {
      const row = byId.get(c.id);
      return {
        key: c.id,
        name: c.name,
        icon: resolveIcon(c.icon),
        color: categoryColor(c.name, c.id),
        amount: row?.totalSpend ?? 0,
        count: row?.transactionCount ?? 0,
        sharePct: 0,
      };
    });

    const uncategorized: Tile = {
      key: "uncategorized",
      name: "Uncategorized",
      icon: HelpCircle,
      color: categoryColor("Uncategorized"),
      amount: uncategorizedRow?.totalSpend ?? 0,
      count: uncategorizedRow?.transactionCount ?? 0,
      sharePct: 0,
    };

    const all = [...real, uncategorized];
    const total = all.reduce((sum, t) => sum + t.amount, 0);
    for (const t of all) {
      t.sharePct = total > 0 ? (t.amount / total) * 100 : 0;
    }
    return all.sort((a, b) => b.amount - a.amount);
  }, [categories, rows]);

  const moneySpent = summary?.totalSpend ?? 0;
  const moneyReceived = summary?.totalIncome ?? 0;
  const initialLoading = categoriesLoading || rowsLoading || summaryLoading;

  if (error && !rows) {
    return <ErrorState message="Could not load category totals." onRetry={refresh} />;
  }

  return (
    <div className="space-y-3">
      {isStale && <StaleBanner onRetry={refresh} />}
      {!initialLoading && (
        <div data-testid="category-summary-totals" className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
          <p className="text-foreground-muted">
            Money spent <span className="font-medium text-foreground">{formatCurrency(moneySpent)}</span>
          </p>
          <p className="text-foreground-muted">
            Money received <span className="font-medium text-foreground">{formatCurrency(moneyReceived)}</span>
          </p>
        </div>
      )}
      <div className="grid grid-cols-[repeat(auto-fit,minmax(11rem,1fr))] gap-3">
        {initialLoading
          ? Array.from({ length: TILE_COUNT_ESTIMATE }).map((_, i) => <Skeleton key={i} className="h-[104px]" />)
          : tiles.map((tile) => (
              <CategoryTile
                key={tile.key}
                tile={tile}
                active={selected === tile.key}
                onClick={() => onSelect(selected === tile.key ? null : tile.key)}
              />
            ))}
      </div>
    </div>
  );
}

function CategoryTile({ tile, active, onClick }: { tile: Tile; active: boolean; onClick: () => void }) {
  const Icon = tile.icon;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "flex flex-col gap-2 rounded-[var(--radius)] border p-3.5 text-left transition-shadow",
        active ? "ring-2 ring-offset-2 ring-offset-surface" : "hover:shadow-[var(--shadow-sm)]",
      )}
      style={
        {
          borderColor: `color-mix(in srgb, ${tile.color} 30%, transparent)`,
          backgroundColor: `color-mix(in srgb, ${tile.color} 8%, transparent)`,
          ...(active ? { "--tw-ring-color": tile.color } : {}),
        } as React.CSSProperties
      }
    >
      <div className="flex items-center gap-2">
        <span
          aria-hidden
          className="flex size-7 shrink-0 items-center justify-center rounded-full"
          style={{ backgroundColor: `color-mix(in srgb, ${tile.color} 18%, transparent)`, color: tile.color }}
        >
          <Icon className="size-4" />
        </span>
        <span className="truncate text-sm font-medium text-foreground">{tile.name}</span>
      </div>
      <div className="tnum text-lg font-semibold text-foreground">{formatCurrency(tile.amount)}</div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-muted">
        <div
          className="h-full rounded-full transition-[width] duration-500"
          style={{ width: `${Math.min(tile.sharePct, 100)}%`, backgroundColor: tile.color }}
        />
      </div>
      <div className="flex items-center justify-between text-xs text-foreground-subtle">
        <span>{tile.sharePct.toFixed(0)}%</span>
        <span>
          {tile.count} {tile.count === 1 ? "txn" : "txns"}
        </span>
      </div>
    </button>
  );
}
