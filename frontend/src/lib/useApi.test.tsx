import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SWRConfig } from "swr";
import { useApi } from "@/lib/useApi";

/**
 * E10-S3 core: a failed fetch after a successful one keeps the last-good data and flips
 * `isStale`. This is the mechanism the dashboard's stale banner is built on.
 */

const fetcher = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  swrFetcher: (key: string) => fetcher(key),
}));

function Harness() {
  const { data, isStale, refresh } = useApi<{ v: number }>("/x");
  return (
    <div>
      {data && <span>value {data.v}</span>}
      {isStale && <span role="status">stale</span>}
      <button type="button" onClick={refresh}>
        refresh
      </button>
    </div>
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("useApi stale-while-error", () => {
  it("keeps last-good data and reports isStale when a later fetch fails", async () => {
    const user = userEvent.setup();
    fetcher.mockResolvedValueOnce({ v: 1 }); // first load succeeds
    fetcher.mockRejectedValue(new Error("backend down")); // every later revalidation fails

    render(
      <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
        <Harness />
      </SWRConfig>,
    );

    // Successful first load.
    expect(await screen.findByText("value 1")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    // Backend goes down mid-session; a revalidation now fails.
    await user.click(screen.getByRole("button", { name: /refresh/i }));

    // Stale indicator appears AND the last-good data is still on screen.
    expect(await screen.findByRole("status")).toHaveTextContent("stale");
    expect(screen.getByText("value 1")).toBeInTheDocument();
  });
});
