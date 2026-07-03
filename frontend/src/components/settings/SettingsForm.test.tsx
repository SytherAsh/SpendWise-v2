import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SettingsForm } from "@/components/settings/SettingsForm";

/** E10-S2-T7 required test: the preferences form save flow. */

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

describe("SettingsForm", () => {
  it("saves edited preferences and shows a confirmation", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: initialPrefs, error: undefined, isLoading: false, refresh: vi.fn() });
    put.mockResolvedValue({ ...initialPrefs, selectedApps: ["paytm", "gpay"], alertChannels: { push: false, email: true } });

    render(<SettingsForm />);

    // Toggle email... actually toggle push off, and add GPay.
    await user.click(screen.getByLabelText(/push notifications/i));
    await user.click(screen.getByLabelText(/google pay/i));
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

    render(<SettingsForm />);
    await user.click(screen.getByRole("button", { name: /save preferences/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/could not save/i);
  });

  it("renders a privacy policy link", () => {
    useApi.mockReturnValue({ data: initialPrefs, error: undefined, isLoading: false, refresh: vi.fn() });
    render(<SettingsForm />);
    expect(screen.getByRole("link", { name: /privacy policy/i })).toHaveAttribute("href");
  });
});
