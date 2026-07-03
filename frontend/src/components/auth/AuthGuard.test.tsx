import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthGuard } from "@/components/auth/AuthGuard";
import { setTokens } from "@/lib/auth";

/** E10-S1-T2: unauthenticated direct navigation to a protected route redirects to login. */

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

afterEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
});

describe("AuthGuard", () => {
  it("redirects to /login when there is no session token", async () => {
    render(
      <AuthGuard>
        <div>protected content</div>
      </AuthGuard>,
    );

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByText("protected content")).not.toBeInTheDocument();
  });

  it("renders children when a session token is present", async () => {
    setTokens({ accessToken: "a", refreshToken: "r" });

    render(
      <AuthGuard>
        <div>protected content</div>
      </AuthGuard>,
    );

    expect(await screen.findByText("protected content")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
