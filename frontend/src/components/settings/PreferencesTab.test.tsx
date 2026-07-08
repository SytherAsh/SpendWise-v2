import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PreferencesTab } from "@/components/settings/PreferencesTab";

/** E10-S2-T7 required test coverage, split out of the old SettingsForm: the preferences save flow. */

const put = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  apiClient: { put: (...a: unknown[]) => put(...a) },
}));

const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: () => useApi(),
}));

const initialPrefs = {
  alertChannels: { push: true, email: true },
  selectedApps: ["paytm"],
  selectedBanks: ["sbi"],
  monthlySpendEstimate: 20000,
};

afterEach(() => {
  vi.clearAllMocks();
});

describe("PreferencesTab", () => {
  it("saves edited preferences and shows a confirmation", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: initialPrefs, error: undefined, isLoading: false, refresh: vi.fn() });
    put.mockResolvedValue({ ...initialPrefs, selectedApps: ["paytm", "gpay"], alertChannels: { push: false, email: true } });

    render(<PreferencesTab />);

    // Toggle push off, and add GPay.
    await user.click(screen.getByRole("button", { name: /push notifications/i }));
    await user.click(screen.getByRole("button", { name: /google pay/i }));
    await user.click(screen.getByRole("button", { name: /save preferences/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledTimes(1);
    });
    const [path, body] = put.mock.calls[0];
    expect(path).toBe("/users/me/preferences");
    expect(body.alertChannels).toEqual({ push: false, email: true });
    expect(body.selectedApps).toEqual(["paytm", "gpay"]);

    expect(await screen.findByText(/preferences saved/i)).toBeInTheDocument();
  });

  it("shows an error when the save fails", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: initialPrefs, error: undefined, isLoading: false, refresh: vi.fn() });
    put.mockRejectedValue(new Error("500"));

    render(<PreferencesTab />);
    await user.click(screen.getByRole("button", { name: /save preferences/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/could not save/i);
  });

  it("reflects the currently selected apps and banks as pressed chips", () => {
    useApi.mockReturnValue({ data: initialPrefs, error: undefined, isLoading: false, refresh: vi.fn() });
    render(<PreferencesTab />);

    expect(screen.getByRole("button", { name: /^paytm$/i })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /google pay/i })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: /^sbi$/i })).toHaveAttribute("aria-pressed", "true");
  });
});
