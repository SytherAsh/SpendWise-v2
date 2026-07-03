import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SWRConfig } from "swr";
import { TransactionsBrowser } from "@/components/transactions/TransactionsBrowser";

/**
 * E10-S2-T2 required tests: pagination, filter state, and the category-correction flow
 * (mocked API). The paginated list is driven by useSWRInfinite → swrFetcher, so we mock
 * swrFetcher (by URL) and apiClient.put (for the correction).
 */

const fetcher = vi.fn();
const put = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  apiClient: { put: (...a: unknown[]) => put(...a) },
  swrFetcher: (key: string) => fetcher(key),
}));

vi.mock("@/lib/useCategories", () => ({
  useCategories: () => ({
    categories: [
      { id: 5, name: "Travel", icon: "flight" },
      { id: 7, name: "Food / Dine Out", icon: "restaurant" },
    ],
    categoryName: (id: number) => (id === 7 ? "Food / Dine Out" : "Travel"),
    isLoading: false,
    error: undefined,
  }),
}));

function txn(id: string, categoryId: number | null, recipient: string) {
  return {
    id,
    transactionDate: "2026-06-15T10:00:00Z",
    amount: -350,
    recipientName: recipient,
    upiId: null,
    bank: "SBI",
    note: null,
    categoryId,
  };
}

// Fresh SWR cache per render so paginated state never bleeds between tests.
function renderIsolated(ui: React.ReactElement) {
  return render(<SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>);
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("TransactionsBrowser", () => {
  it("loads the first page and appends the next page on Load more (cursor pagination)", async () => {
    const user = userEvent.setup();
    fetcher.mockImplementation((key: string) =>
      key.includes("cursor=t1")
        ? Promise.resolve({ data: [txn("t2", 5, "Uber")], nextCursor: null, hasMore: false })
        : Promise.resolve({ data: [txn("t1", 7, "Swiggy")], nextCursor: "t1", hasMore: true }),
    );

    renderIsolated(<TransactionsBrowser />);

    expect(await screen.findByText("Swiggy")).toBeInTheDocument();
    expect(fetcher.mock.calls[0][0]).toContain("limit=50");
    expect(fetcher.mock.calls[0][0]).not.toContain("cursor=");

    await user.click(screen.getByRole("button", { name: /load more/i }));

    expect(await screen.findByText("Uber")).toBeInTheDocument();
    expect(screen.getByText("Swiggy")).toBeInTheDocument(); // appended, not replaced
    expect(fetcher.mock.calls.some((c) => (c[0] as string).includes("cursor=t1"))).toBe(true);
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /load more/i })).not.toBeInTheDocument(),
    );
  });

  it("re-fetches with the category filter param when filters are applied", async () => {
    const user = userEvent.setup();
    fetcher.mockResolvedValue({ data: [txn("t1", 7, "Swiggy")], nextCursor: null, hasMore: false });

    renderIsolated(<TransactionsBrowser />);
    await screen.findByText("Swiggy");

    await user.selectOptions(screen.getByLabelText(/filter by category/i), "5");
    await user.click(screen.getByRole("button", { name: /^apply$/i }));

    await waitFor(() => {
      expect(fetcher.mock.calls.some((c) => (c[0] as string).includes("category=5"))).toBe(true);
    });
  });

  it("corrects a transaction category via PUT and reflects it immediately", async () => {
    const user = userEvent.setup();
    fetcher.mockResolvedValue({ data: [txn("t1", 7, "Swiggy")], nextCursor: null, hasMore: false });
    put.mockResolvedValue({ transactionId: "t1", categoryId: 5 });

    renderIsolated(<TransactionsBrowser />);
    const row = (await screen.findByText("Swiggy")).closest("tr")!;
    const select = within(row).getByRole("combobox") as HTMLSelectElement;
    expect(select.value).toBe("7");

    await user.selectOptions(select, "5");

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith("/transactions/t1/category", { category_id: 5 });
    });
    expect(select.value).toBe("5"); // reflected immediately via optimistic override
  });
});
