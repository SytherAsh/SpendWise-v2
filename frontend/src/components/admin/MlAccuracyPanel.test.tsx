import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MlAccuracyPanel } from "@/components/admin/MlAccuracyPanel";

const post = vi.fn();
vi.mock("@/lib/adminApiClient", () => ({
  adminApiClient: { post: (...a: unknown[]) => post(...a) },
}));

const refresh = vi.fn();
const useAdminApi = vi.fn();
vi.mock("@/lib/useAdminApi", () => ({
  useAdminApi: () => useAdminApi(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

const metrics = {
  generated_at: "2026-07-01T00:00:00Z",
  n_samples: 1810,
  accuracy: 0.912,
  per_category: [{ category_id: 1, category_name: "Shopping", precision: 0.9, recall: 0.85, f1_score: 0.87, support: 100 }],
  report_path: "reports/2026-07-01.txt",
};

describe("MlAccuracyPanel", () => {
  it("renders accuracy and per-category metrics", () => {
    useAdminApi.mockReturnValue({ data: metrics, error: undefined, isLoading: false, refresh });

    render(<MlAccuracyPanel />);

    expect(screen.getByText("91.2%")).toBeInTheDocument();
    expect(screen.getByText("Shopping")).toBeInTheDocument();
  });

  it("triggers a retrain via POST /admin/ml/retrain and refreshes", async () => {
    const user = userEvent.setup();
    useAdminApi.mockReturnValue({ data: metrics, error: undefined, isLoading: false, refresh });
    post.mockResolvedValue(undefined);

    render(<MlAccuracyPanel />);
    await user.click(screen.getByRole("button", { name: /trigger retrain/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/admin/ml/retrain");
      expect(refresh).toHaveBeenCalled();
    });
    // Raised from the 5s default — this test flaked once under full-suite load, confirmed to pass reliably in isolation.
  }, 15_000);
});
