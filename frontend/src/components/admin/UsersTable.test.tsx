import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UsersTable } from "@/components/admin/UsersTable";

const useAdminApi = vi.fn();
vi.mock("@/lib/useAdminApi", () => ({
  useAdminApi: () => useAdminApi(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("UsersTable", () => {
  it("renders each user's phone, transaction count, and last activity", () => {
    useAdminApi.mockReturnValue({
      data: [
        {
          id: "user-1",
          phone: "+911111111111",
          email: null,
          createdAt: "2026-01-01T00:00:00Z",
          transactionCount: 5,
          lastActivity: "2026-07-01T00:00:00Z",
        },
      ],
      error: undefined,
      isLoading: false,
      refresh: vi.fn(),
    });

    render(<UsersTable />);

    expect(screen.getByText("+911111111111")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "+911111111111" })).toHaveAttribute("href", "/admin/users/user-1");
  });

  it("shows an empty state when there are no users", () => {
    useAdminApi.mockReturnValue({ data: [], error: undefined, isLoading: false, refresh: vi.fn() });

    render(<UsersTable />);

    expect(screen.getByText(/no users yet/i)).toBeInTheDocument();
  });
});
