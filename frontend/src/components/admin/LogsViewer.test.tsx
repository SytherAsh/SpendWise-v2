import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LogsViewer } from "@/components/admin/LogsViewer";

const useAdminApi = vi.fn();
vi.mock("@/lib/useAdminApi", () => ({
  useAdminApi: (key: string) => useAdminApi(key),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("LogsViewer", () => {
  it("renders log entries with event type and payload", () => {
    useAdminApi.mockReturnValue({
      data: [
        { id: "log-1", eventType: "parse_failure", userId: null, payload: { reason: "bad format" }, createdAt: "2026-07-01T00:00:00Z" },
      ],
      error: undefined,
      isLoading: false,
      refresh: vi.fn(),
    });

    render(<LogsViewer />);

    // "parse_failure" also appears as a <option> in the filter dropdown — scope to the log card's span.
    expect(screen.getByText("parse_failure", { selector: "span" })).toBeInTheDocument();
    expect(screen.getByText(/bad format/)).toBeInTheDocument();
  });

  it("re-queries with the eventType filter when a type is selected", async () => {
    const user = userEvent.setup();
    useAdminApi.mockReturnValue({ data: [], error: undefined, isLoading: false, refresh: vi.fn() });

    render(<LogsViewer />);
    await user.selectOptions(screen.getByLabelText(/filter by event type/i), "sync_error");

    expect(useAdminApi).toHaveBeenCalledWith("/admin/logs?eventType=sync_error");
  });
});
