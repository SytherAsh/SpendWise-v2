import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExportForm } from "@/components/export/ExportForm";

/** E10-S2-T6 required test: range-picker validation, plus format/mode → download URL. */

const downloadFile = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  downloadFile: (...a: unknown[]) => downloadFile(...a),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("ExportForm", () => {
  it("rejects an end date before the start date without downloading", async () => {
    const user = userEvent.setup();
    render(<ExportForm />);

    await user.type(screen.getByLabelText(/start date/i), "2026-06-30");
    await user.type(screen.getByLabelText(/end date/i), "2026-06-01");
    await user.click(screen.getByRole("button", { name: /download/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/end date must be on or after/i);
    expect(downloadFile).not.toHaveBeenCalled();
  });

  it("requires both dates in custom mode", async () => {
    const user = userEvent.setup();
    render(<ExportForm />);

    await user.type(screen.getByLabelText(/start date/i), "2026-06-01");
    await user.click(screen.getByRole("button", { name: /download/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/both a start and end date/i);
    expect(downloadFile).not.toHaveBeenCalled();
  });

  it("downloads a CSV for a valid custom range", async () => {
    const user = userEvent.setup();
    downloadFile.mockResolvedValue(undefined);
    render(<ExportForm />);

    await user.type(screen.getByLabelText(/start date/i), "2026-04-01");
    await user.type(screen.getByLabelText(/end date/i), "2026-06-30");
    await user.click(screen.getByLabelText(/csv data/i));
    await user.click(screen.getByRole("button", { name: /download/i }));

    await waitFor(() => {
      expect(downloadFile).toHaveBeenCalledWith(
        "/analytics/export/csv?from=2026-04-01&to=2026-06-30",
        expect.stringContaining(".csv"),
      );
    });
  });

  it("downloads a full financial year as a from/to range (PDF)", async () => {
    const user = userEvent.setup();
    downloadFile.mockResolvedValue(undefined);
    render(<ExportForm />);

    await user.click(screen.getByRole("radio", { name: /financial year/i }));
    await user.selectOptions(screen.getByRole("combobox", { name: /financial year/i }), "2025");
    await user.click(screen.getByRole("button", { name: /download/i }));

    await waitFor(() => {
      expect(downloadFile).toHaveBeenCalledWith(
        "/analytics/export/pdf?from=2025-04-01&to=2026-03-31",
        expect.stringContaining("FY2025-2026.pdf"),
      );
    });
  });
});
