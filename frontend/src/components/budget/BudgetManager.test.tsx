import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BudgetManager } from "@/components/budget/BudgetManager";

/**
 * Required tests for the Planning page's redesigned Budgets tab: the category grid (no scroll,
 * one tile per category with spend-vs-budget progress), the click-to-expand slider (starting at
 * the existing budget, or the suggestion for an unbudgeted category), the "Use" suggestion
 * shortcut, zero-limit validation, and the per-tile drill-through link to Transactions. The
 * "total budget this month" header figure lives in BudgetTotalStat (see its own test).
 */

const post = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  apiClient: { post: (...a: unknown[]) => post(...a) },
}));

const refresh = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: (key: string | null) => {
    if (key === "/budgets/progress") {
      return {
        data: [{ categoryId: 5, monthlyLimit: 2000, spent: 1200, percentSpent: 60 }],
        error: undefined,
        isLoading: false,
        refresh,
      };
    }
    if (key === "/budgets/suggestions") {
      return {
        data: [
          { categoryId: 5, suggestedMonthlyLimit: 2500, available: true },
          { categoryId: 7, suggestedMonthlyLimit: 0, available: false },
        ],
        error: undefined,
        isLoading: false,
        refresh: vi.fn(),
      };
    }
    return { data: undefined, error: undefined, isLoading: false, refresh: vi.fn() };
  },
}));

vi.mock("@/lib/useCategories", () => ({
  useCategories: () => ({
    categories: [
      { id: 5, name: "Travel", icon: "flight" },
      { id: 7, name: "Food / Dine Out", icon: "restaurant" },
    ],
    categoryName: (id: number) => (id === 5 ? "Travel" : "Food / Dine Out"),
    isLoading: false,
    error: undefined,
  }),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("BudgetManager", () => {
  it("renders every category as a tile with spend-vs-budget progress", () => {
    render(<BudgetManager />);
    expect(screen.getByText("Travel")).toBeInTheDocument();
    expect(screen.getByText(/60%/)).toBeInTheDocument();
    // A category with no budget set yet still renders, zero-filled.
    expect(screen.getByText("Food / Dine Out")).toBeInTheDocument();
    expect(screen.getByText(/no budget set/i)).toBeInTheDocument();
  });

  it("expands the slider starting at the existing budget, applies the suggestion, and saves", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({});

    render(<BudgetManager />);
    await user.click(screen.getByText("Travel").closest("button")!);

    const slider = screen.getByLabelText(/monthly budget for travel/i) as HTMLInputElement;
    expect(slider.value).toBe("2000"); // starts at the existing budget, not the suggestion

    await user.click(screen.getByRole("button", { name: /^use$/i }));
    expect(slider.value).toBe("2500");

    fireEvent.change(slider, { target: { value: "3000" } });
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/budgets", { categoryId: 5, monthlyLimit: 3000 });
      expect(refresh).toHaveBeenCalled();
    });
  });

  it("starts an unbudgeted, no-history category's slider at zero (no suggestion available)", async () => {
    const user = userEvent.setup();
    render(<BudgetManager />);

    await user.click(screen.getByText("Food / Dine Out").closest("button")!);
    const slider = screen.getByLabelText(/monthly budget for food \/ dine out/i) as HTMLInputElement;
    expect(slider.value).toBe("0");
    expect(slider.max).toBe("10000"); // fallback max with no suggestion to scale from
  });

  it("rejects a zero limit without calling the API", async () => {
    const user = userEvent.setup();
    render(<BudgetManager />);

    await user.click(screen.getByText("Travel").closest("button")!);
    const slider = screen.getByLabelText(/monthly budget for travel/i);
    fireEvent.change(slider, { target: { value: "0" } });
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/greater than zero/i);
    expect(post).not.toHaveBeenCalled();
  });

  it("links each tile to that category's filtered transactions", () => {
    render(<BudgetManager />);
    const link = screen.getByRole("link", { name: /view travel transactions/i });
    expect(link).toHaveAttribute("href", "/transactions?category=5");
  });
});
