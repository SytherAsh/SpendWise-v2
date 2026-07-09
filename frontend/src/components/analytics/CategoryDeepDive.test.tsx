import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CategoryDeepDive } from "@/components/analytics/CategoryDeepDive";

/**
 * The category deep-dive reached by clicking a CategoryTrendGrid tile: trend chart, a
 * this-period-vs-previous delta (computed client-side, not via /analytics/comparison — see
 * docs/api.md's "category is /analytics/trends-only" note), budget progress, biggest
 * transactions, and any recommendations scoped to this category. The "uncategorized" sentinel
 * gets a reduced view — no trend/budget/recommendations, since none of those concepts apply.
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
  trend = { granularity: "day", buckets: [] },
  budgets = [],
  recommendations = [],
  transactions = [],
}: {
  now: unknown[];
  prev: unknown[];
  trend?: { granularity: string; buckets: unknown[] };
  budgets?: unknown[];
  recommendations?: unknown[];
  transactions?: unknown[];
}) {
  useApi.mockImplementation((key: string | null) => {
    if (key === null) return ok(undefined);
    if (key.includes("from=2026-06-01") && key.startsWith("/analytics/categories")) return ok(now);
    if (key.startsWith("/analytics/categories")) return ok(prev);
    if (key.startsWith("/analytics/trends")) return ok(trend);
    if (key === "/budgets/progress") return ok(budgets);
    if (key === "/recommendations") return ok(recommendations);
    if (key.startsWith("/transactions")) return ok({ data: transactions });
    return ok(undefined);
  });
}

describe("CategoryDeepDive", () => {
  it("renders the category header, spend, delta, budget progress, top transactions, and its own recommendations only", () => {
    mockEndpoints({
      now: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }],
      prev: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 400, totalIncome: 0, transactionCount: 2 }],
      trend: { granularity: "day", buckets: [{ bucketStart: "2026-06-01", totalSpend: 800 }] },
      budgets: [{ categoryId: 1, monthlyLimit: 2000, spent: 800, percentSpent: 40 }],
      recommendations: [
        { id: "r1", categoryId: 1, text: "Cut back on groceries", priority: "medium" },
        { id: "r2", categoryId: 2, text: "Unrelated category tip", priority: "low" },
      ],
      transactions: [{ id: "t1", transactionDate: "2026-06-10T00:00:00Z", amount: -500, recipientName: "BigMart", upiId: null, note: null }],
    });

    render(<CategoryDeepDive categoryId={1} onClose={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Groceries" })).toBeInTheDocument();
    // The trend chart's own axis tick can also read "₹800" — scope to the header's MiniStat.
    expect(within(screen.getByText("Spent").closest("div")!).getByText("₹800")).toBeInTheDocument();
    expect(screen.getByText(/▲ 100% vs previous period/)).toBeInTheDocument();
    expect(screen.getByText(/₹800 \/ ₹2,000/)).toBeInTheDocument();
    expect(screen.getByText("BigMart")).toBeInTheDocument();
    expect(screen.getByText("Cut back on groceries")).toBeInTheDocument();
    expect(screen.queryByText("Unrelated category tip")).not.toBeInTheDocument();
  });

  it('shows a "set a budget" prompt when this category has no budget', () => {
    mockEndpoints({
      now: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }],
      prev: [],
      budgets: [],
    });

    render(<CategoryDeepDive categoryId={1} onClose={vi.fn()} />);

    expect(screen.getByText(/no budget set for this category/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /set a budget/i })).toHaveAttribute("href", "/planning");
  });

  it("the uncategorized view has no trend, budget, or recommendations sections, but still shows totals and transactions", () => {
    mockEndpoints({
      now: [{ categoryId: null, categoryName: "Uncategorized", totalSpend: 300, totalIncome: 0, transactionCount: 2 }],
      prev: [],
      transactions: [{ id: "t2", transactionDate: "2026-06-05T00:00:00Z", amount: -300, recipientName: null, upiId: "someone@upi", note: null }],
    });

    render(<CategoryDeepDive categoryId="uncategorized" onClose={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Uncategorized" })).toBeInTheDocument();
    // The one transaction fixture also totals ₹300 — scope to the header's MiniStat.
    expect(within(screen.getByText("Spent").closest("div")!).getByText("₹300")).toBeInTheDocument();
    expect(screen.getByText(/don't have a category-level trend/i)).toBeInTheDocument();
    expect(screen.queryByText(/budget/i)).not.toBeInTheDocument();
    expect(screen.getByText("someone@upi")).toBeInTheDocument();
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
