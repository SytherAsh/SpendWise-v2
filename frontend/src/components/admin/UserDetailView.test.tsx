import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UserDetailView } from "@/components/admin/UserDetailView";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

const deleteFn = vi.fn();
vi.mock("@/lib/adminApiClient", () => ({
  adminApiClient: { delete: (...a: unknown[]) => deleteFn(...a) },
}));

const useAdminApi = vi.fn();
vi.mock("@/lib/useAdminApi", () => ({
  useAdminApi: () => useAdminApi(),
}));

const detail = {
  id: "user-1",
  phone: "+911111111111",
  email: null,
  createdAt: "2026-01-01T00:00:00Z",
  transactions: [{ id: "t1", transactionDate: "2026-06-01T00:00:00Z", amount: -500, recipientName: "Swiggy", categoryId: 7 }],
  budgets: [{ id: "b1", categoryId: 1, monthlyLimit: 2000 }],
  alerts: [{ id: "a1", type: "category_overspend", priority: "high", triggeredAt: "2026-06-15T00:00:00Z", isRead: false }],
};

afterEach(() => {
  vi.clearAllMocks();
});

describe("UserDetailView", () => {
  it("renders transactions, budgets, and alerts for the target user", () => {
    useAdminApi.mockReturnValue({ data: detail, error: undefined, isLoading: false, refresh: vi.fn() });

    render(<UserDetailView userId="user-1" />);

    expect(screen.getByText("Swiggy")).toBeInTheDocument();
    expect(screen.getByText("category_overspend")).toBeInTheDocument();
  });

  it("requires typing the exact identifier before the delete request fires, then redirects on success", async () => {
    // Several userEvent interactions plus an async delete round-trip in one test — the default
    // 5s timeout is occasionally too tight on a loaded machine (confirmed to pass reliably in
    // isolation), so it's raised here rather than left flaky.
    const user = userEvent.setup();
    useAdminApi.mockReturnValue({ data: detail, error: undefined, isLoading: false, refresh: vi.fn() });
    deleteFn.mockResolvedValue(undefined);

    render(<UserDetailView userId="user-1" />);
    await user.click(screen.getByRole("button", { name: /delete user/i }));

    const confirmButton = screen.getByRole("button", { name: /permanently delete user/i });
    expect(confirmButton).toBeDisabled();
    expect(deleteFn).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText(/type/i), "+911111111111");
    await user.click(confirmButton);

    await waitFor(() => {
      expect(deleteFn).toHaveBeenCalledWith("/admin/users/user-1");
      expect(replace).toHaveBeenCalledWith("/admin/users");
    });
  }, 15_000);
});
