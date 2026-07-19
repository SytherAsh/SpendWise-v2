import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  AlertsSection,
  BudgetSection,
  CategorySummarySection,
  RecommendationsSection,
  TrendSection,
} from "@/components/dashboard/sections";

/**
 * E10-S2-T1 required tests: each dashboard section renders from mocked API responses,
 * including the line (trend) and bar/progress (budget + category) chart types.
 *
 * Recharts' ResponsiveContainer measures its parent, which jsdom reports as 0×0, so we
 * give it a fixed size so the chart actually renders its SVG here.
 */
vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactElement }) => (
      <div style={{ width: 800, height: 300 }}>
        <actual.ResponsiveContainer width={800} height={300}>
          {children}
        </actual.ResponsiveContainer>
      </div>
    ),
  };
});

describe("dashboard sections", () => {
  it("AlertsSection renders alerts and priority", () => {
    render(
      <AlertsSection
        state={{
          data: [{ id: "a1", type: "budget_overspend", priority: "high", isRead: false, payload: { message: "Over budget on Food" } }],
          error: undefined,
          isLoading: false,
        }}
        onConfirm={vi.fn()}
        onDismiss={vi.fn()}
      />,
    );
    expect(screen.getByText(/budget overspend/i)).toBeInTheDocument();
    expect(screen.getByText(/over budget on food/i)).toBeInTheDocument();
    expect(screen.getByText("high")).toBeInTheDocument();
  });

  it("RecommendationsSection renders text and dismiss fires the callback", async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    render(
      <RecommendationsSection
        state={{ data: [{ id: "r1", categoryId: 7, text: "You spent 38% more on Food", priority: "medium" }], error: undefined, isLoading: false }}
        onDismiss={onDismiss}
      />,
    );
    expect(screen.getByText(/38% more on food/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /dismiss/i }));
    expect(onDismiss).toHaveBeenCalledWith("r1");
  });

  it("BudgetSection renders progress bars with category names", () => {
    render(
      <BudgetSection
        state={{ data: [{ categoryId: 5, monthlyLimit: 2000, spent: 1200, percentSpent: 60 }], error: undefined, isLoading: false }}
        categoryName={(id) => (id === 5 ? "Travel" : `Category ${id}`)}
      />,
    );
    expect(screen.getByText("Travel")).toBeInTheDocument();
  });

  it("CategorySummarySection renders the category donut from summary categories", () => {
    render(
      <CategorySummarySection
        state={{
          data: [
            { categoryId: 7, categoryName: "Food", totalSpend: 3200 },
            { categoryId: 5, categoryName: "Travel", totalSpend: 1500 },
          ],
          error: undefined,
          isLoading: false,
        }}
      />,
    );
    expect(screen.getByTestId("category-donut")).toBeInTheDocument();
    expect(screen.getByText(/spending by category across 2 categories/i)).toBeInTheDocument();
    // Ranked list carries category identity as the required secondary encoding.
    expect(screen.getByText("Food")).toBeInTheDocument();
  });

  it("TrendSection renders the line chart from trend buckets", () => {
    render(
      <TrendSection
        state={{
          data: {
            buckets: [
              { bucketStart: "2026-05-01T00:00:00Z", totalSpend: 4000 },
              { bucketStart: "2026-06-01T00:00:00Z", totalSpend: 5200 },
            ],
          },
          error: undefined,
          isLoading: false,
        }}
        span={12}
        onSpanChange={vi.fn()}
      />,
    );
    expect(screen.getByTestId("trend-line-chart")).toBeInTheDocument();
    expect(screen.getByText(/spending trend across 2 periods/i)).toBeInTheDocument();
  });

  it("shows an error state when a section fails with no data", () => {
    render(<AlertsSection state={{ data: undefined, error: new Error("boom"), isLoading: false }} onConfirm={vi.fn()} onDismiss={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load alerts/i);
  });
});
