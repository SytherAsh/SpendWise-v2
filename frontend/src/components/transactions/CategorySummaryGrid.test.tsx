import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CategorySummaryGrid } from "@/components/transactions/CategorySummaryGrid";

/**
 * Required tests for the Transactions page's category summary strip: zero-filling categories
 * absent from `/analytics/categories`, the synthetic Uncategorized tile, highest-spend-first
 * sorting, the click-to-select / click-again-to-clear interaction, and mapping the backend's
 * Material Icons identifiers to a real rendered icon instead of raw text. The money-spent/
 * money-received header figures live in TransactionsHeaderStats (see its own test) — this
 * component fetches `/analytics/categories` only.
 */

const useApiMock = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: (...args: unknown[]) => useApiMock(...args),
}));

vi.mock("@/lib/useCategories", () => ({
  useCategories: () => ({
    categories: [
      { id: 1, name: "Groceries", icon: "local_grocery_store" },
      { id: 2, name: "Travel", icon: "flight" },
      { id: 3, name: "Fun", icon: "movie" },
    ],
    categoryName: (id: number) => ((({ 1: "Groceries", 2: "Travel", 3: "Fun" }) as Record<number, string>)[id] ?? `Category ${id}`),
    isLoading: false,
    error: undefined,
  }),
}));

vi.mock("@/lib/date-range", () => ({
  useDateRange: () => ({
    range: { from: "2026-06-01", to: "2026-06-30", preset: "this-month", label: "This month" },
    setPreset: vi.fn(),
    setCustom: vi.fn(),
  }),
}));

function apiState(overrides: Record<string, unknown> = {}) {
  return { data: undefined, error: undefined, isLoading: false, isValidating: false, isStale: false, refresh: vi.fn(), ...overrides };
}

function mockApi(overrides: Record<string, unknown> = {}) {
  useApiMock.mockImplementation(() => apiState(overrides));
}

describe("CategorySummaryGrid", () => {
  it("zero-fills categories absent from the response, includes Uncategorized, sorts by spend descending, and renders a real icon (not raw icon-name text)", () => {
    mockApi({
      data: [
        { categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 },
        { categoryId: null, categoryName: "Uncategorized", totalSpend: 200, totalIncome: 0, transactionCount: 1 },
      ],
    });

    render(<CategorySummaryGrid selected={null} onSelect={vi.fn()} />);

    const buttons = screen.getAllByRole("button");
    expect(within(buttons[0]).getByText("Groceries")).toBeInTheDocument();
    expect(within(buttons[0]).getByText("₹800")).toBeInTheDocument();
    // The backend's icon field is a Material Icons identifier ("local_grocery_store") — it must
    // never render as literal text, only as a mapped icon (svg).
    expect(within(buttons[0]).queryByText("local_grocery_store")).not.toBeInTheDocument();
    expect(buttons[0].querySelector("svg")).toBeInTheDocument();
    expect(within(buttons[1]).getByText("Uncategorized")).toBeInTheDocument();
    // Categories with no transactions this period still render, zero-filled.
    expect(screen.getByText("Travel")).toBeInTheDocument();
    expect(screen.getByText("Fun")).toBeInTheDocument();
  });

  it("clicking a tile selects it; clicking the already-selected tile clears it", async () => {
    const user = userEvent.setup();
    mockApi({ data: [{ categoryId: 1, categoryName: "Groceries", totalSpend: 800, totalIncome: 0, transactionCount: 4 }] });
    const onSelect = vi.fn();
    const { rerender } = render(<CategorySummaryGrid selected={null} onSelect={onSelect} />);

    await user.click(screen.getByText("Groceries").closest("button")!);
    expect(onSelect).toHaveBeenCalledWith(1);

    rerender(<CategorySummaryGrid selected={1} onSelect={onSelect} />);
    await user.click(screen.getByText("Groceries").closest("button")!);
    expect(onSelect).toHaveBeenCalledWith(null);
  });

  it('selecting the Uncategorized tile reports the "uncategorized" sentinel', async () => {
    const user = userEvent.setup();
    mockApi({ data: [{ categoryId: null, categoryName: "Uncategorized", totalSpend: 200, totalIncome: 0, transactionCount: 1 }] });
    const onSelect = vi.fn();
    render(<CategorySummaryGrid selected={null} onSelect={onSelect} />);

    await user.click(screen.getByText("Uncategorized").closest("button")!);
    expect(onSelect).toHaveBeenCalledWith("uncategorized");
  });

  it("shows an error state when the category-totals fetch fails with no data", () => {
    mockApi({ error: new Error("boom") });
    render(<CategorySummaryGrid selected={null} onSelect={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load category totals/i);
  });
});
