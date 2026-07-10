import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MonthStepper } from "@/components/shared/MonthStepper";

/**
 * Steps the shared global date range one calendar month at a time. Reads "which month" off
 * `range.from` (not its own state), and disables "Next" once it reaches the current real-world
 * month — there's nothing to page forward into.
 */

const setMonth = vi.fn();
const useDateRangeMock = vi.fn();
vi.mock("@/lib/date-range", () => ({
  useDateRange: () => useDateRangeMock(),
}));

function mockRange(from: string, to: string) {
  useDateRangeMock.mockReturnValue({
    range: { from, to, preset: "month", label: "" },
    setPreset: vi.fn(),
    setCustom: vi.fn(),
    setMonth,
  });
}

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("MonthStepper", () => {
  it("shows the month/year for the current range and steps backward/forward", async () => {
    // Fake only Date (not setTimeout/setInterval) — userEvent v14 schedules its own real
    // timers internally for click delays, which hang indefinitely under a fully-faked clock
    // even with `advanceTimers` wired up. Scoping the fake to Date avoids that entirely.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 15)); // "now" = October 2026, well after the June range below
    const user = userEvent.setup();
    mockRange("2026-06-01", "2026-06-30");

    render(<MonthStepper />);

    expect(screen.getByText("June 2026")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /previous month/i }));
    expect(setMonth).toHaveBeenCalledWith(2026, 4); // May (0-indexed)

    await user.click(screen.getByRole("button", { name: /next month/i }));
    expect(setMonth).toHaveBeenCalledWith(2026, 6); // July (0-indexed)
  });

  it('disables "Next month" once the range is already the current real-world month', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 20)); // "now" is inside the same June 2026 the range covers
    mockRange("2026-06-01", "2026-06-20");

    render(<MonthStepper />);

    expect(screen.getByRole("button", { name: /next month/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /previous month/i })).toBeEnabled();
  });
});
