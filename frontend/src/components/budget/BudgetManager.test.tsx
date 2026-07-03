import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BudgetManager } from "@/components/budget/BudgetManager";

/** E10-S2-T3 required test: the edit form and suggestion-accept flow. */

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
        data: [{ categoryId: 5, suggestedMonthlyLimit: 2500, available: true }],
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
    categories: [{ id: 5, name: "Travel", icon: "flight" }],
    categoryName: (id: number) => (id === 5 ? "Travel" : `Category ${id}`),
    isLoading: false,
    error: undefined,
  }),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("BudgetManager", () => {
  it("renders current budget progress", () => {
    render(<BudgetManager />);
    expect(screen.getByText("Travel")).toBeInTheDocument();
    expect(screen.getByText(/60%/)).toBeInTheDocument();
  });

  it("accepts a suggestion, edits, and saves the budget", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({});

    render(<BudgetManager />);

    await user.click(screen.getByRole("button", { name: /edit budget/i }));

    // Accept the suggestion — fills the limit input with the suggested value.
    await user.click(screen.getByRole("button", { name: /accept/i }));
    const input = screen.getByLabelText(/monthly limit for travel/i) as HTMLInputElement;
    expect(input.value).toBe("2500");

    // Override to a custom value, then save.
    await user.clear(input);
    await user.type(input, "3000");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/budgets", { categoryId: 5, monthlyLimit: 3000 });
      expect(refresh).toHaveBeenCalled();
    });
  });

  it("rejects a non-positive limit without calling the API", async () => {
    const user = userEvent.setup();
    render(<BudgetManager />);

    await user.click(screen.getByRole("button", { name: /edit budget/i }));
    const input = screen.getByLabelText(/monthly limit for travel/i);
    await user.clear(input);
    await user.type(input, "0");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/greater than zero/i);
    expect(post).not.toHaveBeenCalled();
  });
});
