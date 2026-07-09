import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CategoryTrendGrid } from "@/components/analytics/CategoryTrendGrid";

/**
 * Analytics' default (no category selected) view: a small-multiples grid, one tile per
 * category with a mono spend figure, a delta vs. the previous period (computed client-side
 * via previousPeriod(), since /analytics/comparison ignores the selected range), and a
 * sparkline from /analytics/trends?category=. Zero-spend categories are filtered out, mirroring
 * the pre-redesign AnalyticsView's `cats.filter((c) => c.totalSpend > 0)` behavior.
 */

vi.mock("@/lib/useCategories", () => ({
  useCategories: () => ({
    categories: [
      { id: 1, name: "Groceries", icon: "local_grocery_store" },
      { id: 2, name: "Travel", icon: "flight" },
    ],
    categoryName: (id: number) => ((({ 1: "Groceries", 2: "Travel" }) as Record<number, string>)[id] ?? `Category ${id}`),
    isLoading: false,
    error: undefined,
  }),
}));

const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: (key: string | null) => useApi(key),
}));

function ok<T>(data: T) {
  return { data, error: undefined, isLoading: false, isValidating: false, isStale: false, refresh: vi.fn() };
}

afterEach(() => {
  vi.clearAllMocks();
});

/** Fixed range so previousPeriod()/pickTrendGranularity() resolve deterministically in assertions. */
vi.mock("@/lib/date-range", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/date-range")>();
  return {
    ...actual,
    useDateRange: () => ({
      range: { from: "2026-06-01", to: "2026-06-30", preset: "this-month", label: "This month" },
      setPreset: vi.fn(),
      setCustom: vi.fn(),
      setMonth: vi.fn(),
    }),
  };
});

function mockEndpoints({
  now,
  prev,
  trends = {},
}: {
  now: unknown;
  prev: unknown;
  trends?: Record<number, { bucketStart: string; totalSpend: number }[]>;
}) {
  useApi.mockImplementation((key: string | null) => {
    if (key === null) return ok(undefined);
    if (key.includes("from=2026-06-01") && key.startsWith("/analytics/categories")) return ok(now);
    if (key.startsWith("/analytics/categories")) return ok(prev);
    const match = key.match(/^\/analytics\/trends\?category=(\d+)/);
    if (match) {
      const buckets = trends[Number(match[1])] ?? [];
      return ok({ granularity: "day", buckets });
    }
    return ok(undefined);
  });
}

describe("CategoryTrendGrid", () => {
  it("renders only categories with current-period spend, sorted highest first, with a delta vs. the previous period", () => {
    mockEndpoints({
      now: [
        { categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 },
        { categoryId: 2, categoryName: "Travel", totalSpend: 0, totalIncome: 0, transactionCount: 0 },
      ],
      prev: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 400, totalIncome: 0, transactionCount: 2 }],
      trends: { 1: [{ bucketStart: "2026-06-01", totalSpend: 100 }, { bucketStart: "2026-06-02", totalSpend: 700 }] },
    });

    render(<CategoryTrendGrid selected={null} onSelect={vi.fn()} />);

    expect(screen.getByText("Groceries")).toBeInTheDocument();
    expect(screen.getByText("₹800")).toBeInTheDocument();
    expect(screen.getByText(/▲ 100% vs prev\./)).toBeInTheDocument();
    // Travel had zero spend this period — filtered out entirely, not zero-filled like the
    // Transactions page's tile grid (this page is about categories that actually trended).
    expect(screen.queryByText("Travel")).not.toBeInTheDocument();
  });

  it("clicking a tile reports its category id to onSelect", async () => {
    const user = userEvent.setup();
    mockEndpoints({
      now: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }],
      prev: [],
    });
    const onSelect = vi.fn();
    render(<CategoryTrendGrid selected={null} onSelect={onSelect} />);

    await user.click(screen.getByText("Groceries").closest("button")!);
    expect(onSelect).toHaveBeenCalledWith(1);
  });

  it('"View as table" toggles a table view of the same rows', async () => {
    const user = userEvent.setup();
    mockEndpoints({
      now: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }],
      prev: [],
    });
    render(<CategoryTrendGrid selected={null} onSelect={vi.fn()} />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /view as table/i }));
    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: /category/i })).toBeInTheDocument();
  });

  it("shows an error state when the category-totals fetch fails with no data", () => {
    useApi.mockImplementation((key: string | null) => {
      if (key !== null && key.includes("from=2026-06-01")) {
        return { data: undefined, error: new Error("boom"), isLoading: false, isValidating: false, isStale: false, refresh: vi.fn() };
      }
      return ok(undefined);
    });

    render(<CategoryTrendGrid selected={null} onSelect={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load category totals/i);
  });
});
