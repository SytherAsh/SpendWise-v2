import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ChatbotView } from "@/components/chatbot/ChatbotView";

/**
 * E10-S2-T5 required tests: sending a message and rendering the response; resuming a
 * session's history.
 */

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/apiClient", () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
  },
}));

const refresh = vi.fn();
const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: () => useApi(),
}));

const sessions = [
  { id: "s1", createdAt: "2026-06-01T10:00:00Z", lastActiveAt: "2026-06-20T10:00:00Z" },
  { id: "s2", createdAt: "2026-05-01T10:00:00Z", lastActiveAt: "2026-05-10T10:00:00Z" },
];

afterEach(() => {
  vi.clearAllMocks();
});

describe("ChatbotView", () => {
  it("resumes a past session's history when selected", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: sessions, error: undefined, isLoading: false, isStale: false, refresh });
    get.mockResolvedValue({
      id: "s1",
      createdAt: "2026-06-01T10:00:00Z",
      lastActiveAt: "2026-06-20T10:00:00Z",
      messages: [
        { id: "m1", role: "user", message: "How much did I spend on food?", createdAt: "2026-06-20T10:00:00Z" },
        { id: "m2", role: "assistant", message: "You spent ₹3,240 on Food last month.", createdAt: "2026-06-20T10:00:05Z" },
      ],
    });

    render(<ChatbotView />);
    await user.click(screen.getAllByRole("button", { name: /chat ·/i })[0]);

    await waitFor(() => expect(get).toHaveBeenCalledWith("/chatbot/sessions/s1"));
    const thread = screen.getByTestId("message-thread");
    expect(within(thread).getByText(/how much did i spend on food/i)).toBeInTheDocument();
    expect(within(thread).getByText(/₹3,240 on food/i)).toBeInTheDocument();
  });

  it("sends a message and renders the assistant response", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: sessions, error: undefined, isLoading: false, isStale: false, refresh });
    get.mockResolvedValue({ id: "s1", createdAt: "", lastActiveAt: "", messages: [] });
    post.mockResolvedValue({
      id: "reply-1",
      role: "assistant",
      message: "You spent ₹1,200 on Travel in June.",
      createdAt: "2026-06-21T10:00:00Z",
    });

    render(<ChatbotView />);
    // Open a session so the composer is available.
    await user.click(screen.getAllByRole("button", { name: /chat ·/i })[0]);
    await waitFor(() => expect(get).toHaveBeenCalled());

    await user.type(screen.getByLabelText(/message/i), "How much on travel?");
    await user.click(screen.getByRole("button", { name: /send/i }));

    const thread = screen.getByTestId("message-thread");
    // User's message shows immediately (optimistic)…
    expect(within(thread).getByText(/how much on travel\?/i)).toBeInTheDocument();
    // …and the assistant reply renders once resolved.
    expect(await within(thread).findByText(/₹1,200 on travel/i)).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/chatbot/message", { sessionId: "s1", message: "How much on travel?" });
  });

  it("starts a new chat via POST /chatbot/sessions", async () => {
    const user = userEvent.setup();
    useApi.mockReturnValue({ data: [], error: undefined, isLoading: false, isStale: false, refresh });
    post.mockResolvedValue({ id: "new-1", createdAt: "", lastActiveAt: "" });

    render(<ChatbotView />);
    await user.click(screen.getByRole("button", { name: /new chat/i }));

    await waitFor(() => expect(post).toHaveBeenCalledWith("/chatbot/sessions"));
    expect(refresh).toHaveBeenCalled();
  });
});
