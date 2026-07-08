import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProfileTab } from "@/components/settings/ProfileTab";

/** E10-S2-T7 required test coverage, split out of the old SettingsForm: profile load/edit/save. */

const put = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  apiClient: { put: (...a: unknown[]) => put(...a) },
}));

const refresh = vi.fn();
const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: () => useApi(),
}));

vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: "system", setTheme: vi.fn() }),
}));

const profile = {
  id: "user-1",
  phone: "+919876543210",
  email: "user@example.com",
  // Noon UTC, not midnight — stays "15 Jan" under any realistic local test-runner timezone
  // (no TZ pin exists in this project's vitest config).
  createdAt: "2026-01-15T12:00:00Z",
};

afterEach(() => {
  vi.clearAllMocks();
});

describe("ProfileTab", () => {
  it("shows the read-only phone number and member-since date", () => {
    useApi.mockReturnValue({ data: profile, error: undefined, isLoading: false, refresh });
    render(<ProfileTab />);

    expect(screen.getByText("+919876543210")).toBeInTheDocument();
    expect(screen.getByText(/15 jan 2026/i)).toBeInTheDocument();
  });

  it("saves an edited email and shows a confirmation", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: profile, error: undefined, isLoading: false, refresh });
    put.mockResolvedValue({ ...profile, email: "new@example.com" });

    render(<ProfileTab />);
    const emailInput = screen.getByLabelText(/^email$/i);
    await user.clear(emailInput);
    await user.type(emailInput, "new@example.com");
    await user.click(screen.getByRole("button", { name: /save email/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith("/users/me", { email: "new@example.com" });
    });
    expect(await screen.findByText(/email saved/i)).toBeInTheDocument();
  });

  it("shows an error when the save fails", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: profile, error: undefined, isLoading: false, refresh });
    put.mockRejectedValue(new Error("500"));

    render(<ProfileTab />);
    await user.click(screen.getByRole("button", { name: /save email/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/could not save/i);
  });

  it("renders a privacy policy link and the appearance toggle", () => {
    useApi.mockReturnValue({ data: profile, error: undefined, isLoading: false, refresh });
    render(<ProfileTab />);

    expect(screen.getByRole("link", { name: /privacy policy/i })).toHaveAttribute("href");
    expect(screen.getByRole("button", { name: /^light$/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^dark$/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^system$/i })).toBeInTheDocument();
  });
});
