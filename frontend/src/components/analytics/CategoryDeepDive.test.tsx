import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CategoryDeepDive } from "@/components/analytics/CategoryDeepDive";

/**
 * The category deep-dive reached by clicking a CategoryTrendGrid tile: trend chart, a
 * this-period-vs-previous delta (computed client-side, not via /analytics/comparison — see
 * docs/api.md's "category is /analytics/trends-only" note), average spend per transaction,
 * a daily/monthly/share-of-total picture, counterparties grouped by person (not one row per
 * transaction — that detail lives on the Transactions page), and any recommendations scoped to
 * this category. No budget section — deliberately removed per user feedback that it didn't
 * belong on this page. The "uncategorized" sentinel gets a reduced view (no trend/pattern/
 * share/monthly charts, no recommendations) but still totals and counterparties.
 */

vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactElement }) => (
      <actual.ResponsiveContainer width={800} height={260}>
        {children}
      </actual.ResponsiveContainer>
    ),
  };
});

vi.mock("@/components/shared/StatTile", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/shared/StatTile")>();
  return { ...actual, useCountUp: (value: number) => value };
});

vi.mock("@/lib/useCategories", () => ({
  useCategories: () => ({
    categories: [{ id: 1, name: "Groceries", icon: "local_grocery_store" }],
    categoryName: (id: number) => (id === 1 ? "Groceries" : `Category ${id}`),
    isLoading: false,
    error: undefined,
  }),
}));

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

function mockEndpoints({
  now,
  prev,
  categoryTrend = { granularity: "day", buckets: [] },
  overallTrend = { granularity: "day", buckets: [] },
  monthlyTrend = { granularity: "month", buckets: [] },
  recommendations = [],
  transactions = [],
}: {
  now: unknown[];
  prev: unknown[];
  categoryTrend?: { granularity: string; buckets: unknown[] };
  overallTrend?: { granularity: string; buckets: unknown[] };
  monthlyTrend?: { granularity: string; buckets: unknown[] };
  recommendations?: unknown[];
  transactions?: unknown[];
}) {
  useApi.mockImplementation((key: string | null) => {
    if (key === null) return ok(undefined);
    if (key.includes("from=2026-06-01") && key.startsWith("/analytics/categories")) return ok(now);
    if (key.startsWith("/analytics/categories")) return ok(prev);
    if (key.startsWith("/analytics/trends") && key.includes("granularity=month")) return ok(monthlyTrend);
    if (key.startsWith("/analytics/trends") && key.includes("category=")) return ok(categoryTrend);
    if (key.startsWith("/analytics/trends")) return ok(overallTrend);
    if (key === "/recommendations") return ok(recommendations);
    if (key.startsWith("/transactions")) return ok({ data: transactions });
    return ok(undefined);
  });
}

describe("CategoryDeepDive", () => {
  it("renders spend, average per transaction, delta, counterparties grouped by person (not one row per transaction), its own recommendations only, and no budget content", () => {
    mockEndpoints({
      now: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }],
      prev: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 400, totalIncome: 0, transactionCount: 2 }],
      categoryTrend: { granularity: "day", buckets: [{ bucketStart: "2026-06-01", totalSpend: 800 }] },
      overallTrend: { granularity: "day", buckets: [{ bucketStart: "2026-06-01", totalSpend: 2000 }] },
      recommendations: [
        { id: "r1", categoryId: 1, text: "Cut back on groceries", priority: "medium" },
        { id: "r2", categoryId: 2, text: "Unrelated category tip", priority: "low" },
      ],
      transactions: [
        { id: "t1", transactionDate: "2026-06-10T00:00:00Z", amount: -300, recipientName: "BigMart", upiId: null, note: null },
        { id: "t2", transactionDate: "2026-06-12T00:00:00Z", amount: -200, recipientName: "BigMart", upiId: null, note: null },
        { id: "t3", transactionDate: "2026-06-05T00:00:00Z", amount: -300, recipientName: "Zomato", upiId: null, note: null },
      ],
    });

    render(<CategoryDeepDive categoryId={1} onClose={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Groceries" })).toBeInTheDocument();
    // The trend/share charts' own axis ticks can also read these figures — scope to the MiniStats.
    expect(within(screen.getByText("Spent").closest("div")!).getByText("₹800")).toBeInTheDocument();
    expect(within(screen.getByText(/Avg/).closest("div")!).getByText("₹200")).toBeInTheDocument();
    expect(screen.getByText(/▲ 100% vs previous period/)).toBeInTheDocument();

    // BigMart's two transactions (₹300 + ₹200) collapse into one row summing to ₹500 — not
    // two separate rows, which is what the Transactions page is for.
    const bigMartRow = screen.getByText("BigMart").closest("li")!;
    expect(within(bigMartRow).getByText("2 txns")).toBeInTheDocument();
    expect(within(bigMartRow).getByText("₹500")).toBeInTheDocument();
    const zomatoRow = screen.getByText("Zomato").closest("li")!;
    expect(within(zomatoRow).getByText("1 txn")).toBeInTheDocument();
    expect(within(zomatoRow).getByText("₹300")).toBeInTheDocument();

    expect(screen.getByText("Cut back on groceries")).toBeInTheDocument();
    expect(screen.queryByText("Unrelated category tip")).not.toBeInTheDocument();

    expect(screen.queryByText(/budget/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /set a budget/i })).not.toBeInTheDocument();
  });

  it("renders the daily pattern, share-of-total, and last-6-months sections for a real category", () => {
    mockEndpoints({
      now: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }],
      prev: [],
      categoryTrend: { granularity: "day", buckets: [{ bucketStart: "2026-06-01", totalSpend: 800 }] },
      overallTrend: { granularity: "day", buckets: [{ bucketStart: "2026-06-01", totalSpend: 2000 }] },
      monthlyTrend: {
        granularity: "month",
        buckets: [
          { bucketStart: "2026-05-01", totalSpend: 600 },
          { bucketStart: "2026-06-01", totalSpend: 800 },
        ],
      },
    });

    render(<CategoryDeepDive categoryId={1} onClose={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Daily pattern" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Share of total spend" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Last 6 months" })).toBeInTheDocument();
  });

  it("the uncategorized view has no trend, pattern, share, monthly, budget, or recommendations sections, but still shows totals and grouped counterparties", () => {
    mockEndpoints({
      now: [{ categoryId: null, categoryName: "Uncategorized", totalSpend: 300, totalIncome: 0, transactionCount: 1 }],
      prev: [],
      transactions: [{ id: "t4", transactionDate: "2026-06-05T00:00:00Z", amount: -300, recipientName: null, upiId: "someone@upi", note: null }],
    });

    render(<CategoryDeepDive categoryId="uncategorized" onClose={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Uncategorized" })).toBeInTheDocument();
    // The one transaction fixture also totals ₹300 — scope to the header's MiniStat.
    expect(within(screen.getByText("Spent").closest("div")!).getByText("₹300")).toBeInTheDocument();
    expect(screen.getByText(/don't have a category-level trend/i)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Daily pattern" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Share of total spend" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Last 6 months" })).not.toBeInTheDocument();
    expect(screen.queryByText(/budget/i)).not.toBeInTheDocument();

    const row = screen.getByText("someone@upi").closest("li")!;
    expect(within(row).getByText("1 txn")).toBeInTheDocument();
    expect(within(row).getByText("₹300")).toBeInTheDocument();
  });

  it("the back button calls onClose", async () => {
    const user = userEvent.setup();
    mockEndpoints({ now: [], prev: [] });
    const onClose = vi.fn();
    render(<CategoryDeepDive categoryId={1} onClose={onClose} />);

    await user.click(screen.getByRole("button", { name: /all categories/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
