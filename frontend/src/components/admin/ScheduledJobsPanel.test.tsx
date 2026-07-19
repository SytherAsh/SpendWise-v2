import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScheduledJobsPanel } from "@/components/admin/ScheduledJobsPanel";

const post = vi.fn();
vi.mock("@/lib/adminApiClient", () => ({
  adminApiClient: { post: (...a: unknown[]) => post(...a) },
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("ScheduledJobsPanel", () => {
  it("renders one row per job with a Run now button", () => {
    render(<ScheduledJobsPanel />);

    expect(screen.getByText("Recipient canonicalization sweep")).toBeInTheDocument();
    expect(screen.getByText("Categorization retry")).toBeInTheDocument();
    expect(screen.getByText("Alert + recurring-payment evaluator")).toBeInTheDocument();
    expect(screen.getByText("Recommendation generator")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /run now/i })).toHaveLength(4);
  });

  it("triggers the canonicalization sweep via POST /admin/ml/canonicalize-recipients", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue(undefined);
    render(<ScheduledJobsPanel />);

    await user.click(screen.getAllByRole("button", { name: /run now/i })[0]);

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/admin/ml/canonicalize-recipients");
      expect(screen.getByText(/triggered/i)).toBeInTheDocument();
    });
  });

  it("shows a distinct cost note only under the recommendation generator row", () => {
    render(<ScheduledJobsPanel />);

    expect(screen.getByText(/real llm call per candidate/i)).toBeInTheDocument();
  });

  it("shows an error message when the trigger request fails", async () => {
    const user = userEvent.setup();
    post.mockRejectedValue(new Error("500"));
    render(<ScheduledJobsPanel />);

    await user.click(screen.getAllByRole("button", { name: /run now/i })[1]);

    await waitFor(() => {
      expect(screen.getByText(/could not trigger this job/i)).toBeInTheDocument();
    });
  });
});
