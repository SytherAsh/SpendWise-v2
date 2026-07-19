import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { JobSchedulesPanel } from "@/components/admin/JobSchedulesPanel";

const put = vi.fn();
vi.mock("@/lib/adminApiClient", () => ({
  adminApiClient: { put: (...a: unknown[]) => put(...a) },
}));

const refresh = vi.fn();
const useAdminApi = vi.fn();
vi.mock("@/lib/useAdminApi", () => ({
  useAdminApi: () => useAdminApi(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

const schedules = [
  {
    jobKey: "categorization_retry",
    displayName: "Categorization retry",
    scheduleType: "INTERVAL",
    intervalValue: 30,
    intervalUnit: "MINUTES",
    dayOfWeek: null,
    hourOfDay: null,
    updatedAt: "2026-07-19T00:00:00Z",
  },
  {
    jobKey: "canonicalization",
    displayName: "Recipient canonicalization sweep",
    scheduleType: "WEEKLY",
    intervalValue: null,
    intervalUnit: null,
    dayOfWeek: "SUN",
    hourOfDay: 4,
    updatedAt: "2026-07-19T00:00:00Z",
  },
];

describe("JobSchedulesPanel", () => {
  it("renders one row per job showing its current human-readable schedule", () => {
    useAdminApi.mockReturnValue({ data: schedules, error: undefined, isLoading: false, refresh });

    render(<JobSchedulesPanel />);

    expect(screen.getByText("Categorization retry")).toBeInTheDocument();
    expect(screen.getByText(/every 30 minutes/i)).toBeInTheDocument();
    expect(screen.getByText("Recipient canonicalization sweep")).toBeInTheDocument();
    expect(screen.getByText(/every sunday at 04:00 utc/i)).toBeInTheDocument();
  });

  it("Save is disabled until a value actually changes", () => {
    useAdminApi.mockReturnValue({ data: schedules, error: undefined, isLoading: false, refresh });

    render(<JobSchedulesPanel />);

    const saveButtons = screen.getAllByRole("button", { name: /save/i });
    expect(saveButtons[0]).toBeDisabled();
  });

  it("saves an interval change via PUT and shows a confirmation", async () => {
    const user = userEvent.setup();
    useAdminApi.mockReturnValue({ data: schedules, error: undefined, isLoading: false, refresh });
    put.mockResolvedValue(undefined);
    render(<JobSchedulesPanel />);

    const intervalInputs = screen.getAllByRole("spinbutton");
    await user.clear(intervalInputs[0]);
    await user.type(intervalInputs[0], "45");
    await user.click(screen.getAllByRole("button", { name: /save/i })[0]);

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith("/admin/job-schedules/categorization_retry", {
        scheduleType: "INTERVAL",
        intervalValue: 45,
        intervalUnit: "MINUTES",
      });
      expect(screen.getByText(/takes effect immediately/i)).toBeInTheDocument();
      expect(refresh).toHaveBeenCalled();
    });
  });

  it("shows an error message when saving fails", async () => {
    const user = userEvent.setup();
    useAdminApi.mockReturnValue({ data: schedules, error: undefined, isLoading: false, refresh });
    put.mockRejectedValue(new Error("400"));
    render(<JobSchedulesPanel />);

    const intervalInputs = screen.getAllByRole("spinbutton");
    await user.clear(intervalInputs[0]);
    await user.type(intervalInputs[0], "45");
    await user.click(screen.getAllByRole("button", { name: /save/i })[0]);

    await waitFor(() => {
      expect(screen.getByText(/could not save this schedule/i)).toBeInTheDocument();
    });
  });
});
