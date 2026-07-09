import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TransactionsHeaderStats } from "@/components/transactions/TransactionsHeaderStats";

/**
 * Required tests for the Transactions page's header-right hero figures: money spent and
 * money received render as distinct cards (not plain text) once `/analytics/summary`
 * resolves, and nothing renders before the first successful load.
 */

const useApiMock = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: (...args: unknown[]) => useApiMock(...args),
}));

// MiniStat's count-up animates 0 -> value via requestAnimationFrame, which doesn't resolve
// synchronously in jsdom. Make it deterministic for assertions (mirrors DashboardView.test.tsx's
// partial recharts mock).
vi.mock("@/components/shared/StatTile", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/shared/StatTile")>();
  return { ...actual, useCountUp: (value: number) => value };
});

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

describe("TransactionsHeaderStats", () => {
  it("renders money spent and money received as cards with the formatted amounts", () => {
    useApiMock.mockReturnValue(apiState({ data: { totalSpend: 1173, totalIncome: 640 } }));
    render(<TransactionsHeaderStats />);

    const wrap = screen.getByTestId("transactions-header-stats");
    expect(wrap).toHaveTextContent(/money spent/i);
    expect(wrap).toHaveTextContent("₹1,173");
    expect(wrap).toHaveTextContent(/money received/i);
    expect(wrap).toHaveTextContent("₹640");
  });

  it("renders nothing before the first successful load", () => {
    useApiMock.mockReturnValue(apiState({ isLoading: false, data: undefined }));
    render(<TransactionsHeaderStats />);
    expect(screen.queryByTestId("transactions-header-stats")).not.toBeInTheDocument();
  });
});
