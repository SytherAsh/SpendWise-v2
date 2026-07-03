import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DashboardView } from "@/components/dashboard/DashboardView";

/**
 * E10-S3 on the dashboard page: when a section is serving stale data (a failed fetch after
 * a successful one), the page shows the stale banner while the last-loaded data still
 * renders — not an error screen.
 *
 * Recharts ResponsiveContainer needs a measured parent; give it a fixed size in jsdom.
 */
vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactElement }) => (
      <actual.ResponsiveContainer width={800} height={300}>
        {children}
      </actual.ResponsiveContainer>
    ),
  };
});

vi.mock("@/lib/useCategories", () => ({
  useCategories: () => ({
    categories: [{ id: 5, name: "Travel", icon: "flight" }],
    categoryName: (id: number) => (id === 5 ? "Travel" : `Category ${id}`),
    isLoading: false,
    error: undefined,
  }),
}));

vi.mock("@/lib/apiClient", () => ({
  apiClient: { put: vi.fn() },
}));

// useApi returns per-endpoint state; the budgets endpoint is stale (error + last-good data).
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

describe("DashboardView stale handling (E10-S3)", () => {
  it("renders the stale banner while still showing last-loaded budget data", () => {
    useApi.mockImplementation((key: string) => {
      if (key === "/budgets/progress") {
        // Stale: a revalidation failed but we still hold the last-good data.
        return {
          data: [{ categoryId: 5, monthlyLimit: 2000, spent: 1200, percentSpent: 60 }],
          error: new Error("backend down"),
          isLoading: false,
          isValidating: false,
          isStale: true,
          refresh: vi.fn(),
        };
      }
      if (key === "/alerts?limit=20") return ok({ data: [], nextCursor: null, hasMore: false });
      if (key === "/recommendations") return ok([]);
      if (key.startsWith("/analytics/summary")) return ok({ totalSpend: 0, totalIncome: 0, categories: [] });
      if (key.startsWith("/analytics/trends")) return ok({ granularity: "month", buckets: [] });
      return ok(undefined);
    });

    render(<DashboardView />);

    // Stale indicator present…
    expect(screen.getByRole("status")).toHaveTextContent(/last-loaded data/i);
    // …and the last-good budget data still renders (not an error state).
    expect(screen.getByText("Travel")).toBeInTheDocument();
    expect(screen.getByText(/₹1,200/)).toBeInTheDocument();
  });

  it("does not show the stale banner when all sections are healthy", () => {
    useApi.mockImplementation((key: string) => {
      if (key === "/alerts?limit=20") return ok({ data: [], nextCursor: null, hasMore: false });
      if (key === "/recommendations") return ok([]);
      if (key === "/budgets/progress") return ok([{ categoryId: 5, monthlyLimit: 2000, spent: 1200, percentSpent: 60 }]);
      if (key.startsWith("/analytics/summary")) return ok({ totalSpend: 0, totalIncome: 0, categories: [] });
      if (key.startsWith("/analytics/trends")) return ok({ granularity: "month", buckets: [] });
      return ok(undefined);
    });

    render(<DashboardView />);
    expect(screen.queryByText(/last-loaded data/i)).not.toBeInTheDocument();
  });
});
