import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminLoginForm } from "@/components/admin/AdminLoginForm";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

const post = vi.fn();
vi.mock("@/lib/adminApiClient", () => ({
  adminApiClient: {
    post: (...args: unknown[]) => post(...args),
  },
}));

const setAdminAccessToken = vi.fn();
vi.mock("@/lib/adminAuth", () => ({
  setAdminAccessToken: (...args: unknown[]) => setAdminAccessToken(...args),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("AdminLoginForm", () => {
  it("stores the admin token and redirects to the users list on success", async () => {
    const user = userEvent.setup();
    post.mockResolvedValue({ accessToken: "admin-token", expiresIn: 86_400 });

    render(<AdminLoginForm />);
    await user.type(screen.getByLabelText(/username/i), "admin");
    await user.type(screen.getByLabelText(/password/i), "correct-horse-battery-staple");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/admin/auth/login",
        { username: "admin", password: "correct-horse-battery-staple" },
        { auth: false },
      );
      expect(setAdminAccessToken).toHaveBeenCalledWith("admin-token");
      expect(replace).toHaveBeenCalledWith("/admin/users");
    });
  });

  it("shows an inline error and does not redirect on invalid credentials", async () => {
    const user = userEvent.setup();
    post.mockRejectedValue(new Error("Invalid admin username or password"));

    render(<AdminLoginForm />);
    await user.type(screen.getByLabelText(/username/i), "admin");
    await user.type(screen.getByLabelText(/password/i), "wrong-password");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/invalid admin username or password/i);
    expect(replace).not.toHaveBeenCalled();
  });
});
