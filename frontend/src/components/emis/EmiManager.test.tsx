import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EmiManager } from "@/components/emis/EmiManager";

/** E10-S2-T4 required test: the deactivate flow (plus edit for coverage). */

const patch = vi.fn();
const put = vi.fn();
const post = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  apiClient: {
    patch: (...a: unknown[]) => patch(...a),
    put: (...a: unknown[]) => put(...a),
    post: (...a: unknown[]) => post(...a),
  },
}));

const refresh = vi.fn();
const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: () => useApi(),
}));

const emi = {
  id: "emi-1",
  label: "Netflix",
  amount: 199,
  dueDay: 5,
  detectedFromSms: true,
  isActive: true,
  sourceTransactionId: "txn-1",
};

afterEach(() => {
  vi.clearAllMocks();
});

describe("EmiManager", () => {
  it("deactivates an EMI via PATCH and refreshes the list", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: [emi], error: undefined, isLoading: false, refresh });
    patch.mockResolvedValue(undefined);

    render(<EmiManager />);
    expect(screen.getByText("Netflix")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /deactivate netflix/i }));

    await waitFor(() => {
      expect(patch).toHaveBeenCalledWith("/emis/emi-1");
      expect(refresh).toHaveBeenCalled();
    });
    expect(put).not.toHaveBeenCalled();
  });

  it("edits an EMI via PUT with the full label/amount/dueDay body", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: [emi], error: undefined, isLoading: false, refresh });
    put.mockResolvedValue(undefined);

    render(<EmiManager />);
    await user.click(screen.getByRole("button", { name: /^edit$/i }));

    const amount = screen.getByLabelText(/^amount$/i);
    await user.clear(amount);
    await user.type(amount, "249");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith("/emis/emi-1", { label: "Netflix", amount: 249, dueDay: 5 });
    });
  });
});
