import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BudgetTotalStat } from "@/components/budget/BudgetTotalStat";

/**
 * Required test for Planning's header-right hero figure: the total budget renders as a
 * card (not plain text), summing every category's monthly limit from `/budgets/progress`.
 */

const useApiMock = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: (...args: unknown[]) => useApiMock(...args),
}));

// MiniStat's count-up animates 0 -> value via requestAnimationFrame, which doesn't resolve
// synchronously in jsdom. Make it deterministic for assertions.
vi.mock("@/components/shared/StatTile", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/shared/StatTile")>();
  return { ...actual, useCountUp: (value: number) => value };
});

function apiState(overrides: Record<string, unknown> = {}) {
  return { data: undefined, error: undefined, isLoading: false, isValidating: false, isStale: false, refresh: vi.fn(), ...overrides };
}

describe("BudgetTotalStat", () => {
  it("renders the sum of every category's monthly limit as a card", () => {
    useApiMock.mockReturnValue(
      apiState({
        data: [
          { categoryId: 5, monthlyLimit: 12000, spent: 6000, percentSpent: 50 },
          { categoryId: 7, monthlyLimit: 8000, spent: 2000, percentSpent: 25 },
        ],
      }),
    );
    render(<BudgetTotalStat />);

    const wrap = screen.getByTestId("budget-total-stat");
    expect(wrap).toHaveTextContent(/total budget this month/i);
    expect(wrap).toHaveTextContent("₹20,000");
  });

  it("renders nothing before the first successful load", () => {
    useApiMock.mockReturnValue(apiState({ isLoading: false, data: undefined }));
    render(<BudgetTotalStat />);
    expect(screen.queryByTestId("budget-total-stat")).not.toBeInTheDocument();
  });
});
