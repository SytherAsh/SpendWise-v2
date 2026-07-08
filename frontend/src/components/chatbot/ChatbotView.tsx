"use client";

import { useState } from "react";
import { apiClient } from "@/lib/apiClient";
import { useApi } from "@/lib/useApi";
import { formatDate } from "@/lib/format";
import { Card, EmptyState, ErrorState, Spinner } from "@/components/shared/ui";

interface Session {
  id: string;
  createdAt: string;
  lastActiveAt: string;
}
interface Message {
  id: string;
  role: string;
  message: string;
  createdAt: string;
}
interface SessionHistory {
  id: string;
  createdAt: string;
  lastActiveAt: string;
  messages: Message[];
}

export function ChatbotView() {
  const sessions = useApi<Session[]>("/chatbot/sessions");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function selectSession(id: string) {
    setSelectedId(id);
    setError(null);
    setLoadingHistory(true);
    try {
      const history = await apiClient.get<SessionHistory>(`/chatbot/sessions/${id}`);
      setMessages(history.messages);
    } catch {
      setError("Could not load this conversation.");
      setMessages([]);
    } finally {
      setLoadingHistory(false);
    }
  }

  async function newChat() {
    setError(null);
    try {
      const session = await apiClient.post<Session>("/chatbot/sessions");
      setSelectedId(session.id);
      setMessages([]);
      sessions.refresh();
    } catch {
      setError("Could not start a new chat.");
    }
  }

  async function send(e: React.FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || !selectedId) return;

    // Optimistically show the user's message immediately.
    const optimistic: Message = {
      id: `local-${Date.now()}`,
      role: "user",
      message: text,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimistic]);
    setInput("");
    setSending(true);
    setError(null);
    try {
      const reply = await apiClient.post<Message>("/chatbot/message", { sessionId: selectedId, message: text });
      setMessages((prev) => [...prev, reply]);
      sessions.refresh(); // last_active_at changed → reorder the session list
    } catch {
      setError("Could not send your message. Please try again.");
    } finally {
      setSending(false);
    }
  }

  const sessionList = sessions.data ?? [];

  return (
    <div className="grid gap-4 md:grid-cols-[16rem_1fr]">
      <Card className="h-fit">
        <button
          type="button"
          onClick={newChat}
          className="mb-3 w-full rounded-md bg-brand-700 px-3 py-2 text-sm font-medium text-white"
        >
          New chat
        </button>
        {sessions.isLoading && !sessions.data ? (
          <Spinner />
        ) : sessionList.length === 0 ? (
          <EmptyState message="No conversations yet." />
        ) : (
          <ul className="space-y-1">
            {sessionList.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  onClick={() => selectSession(s.id)}
                  aria-current={selectedId === s.id ? "true" : undefined}
                  className={`w-full rounded-md px-3 py-2 text-left text-sm ${
                    selectedId === s.id ? "bg-brand-700 text-white" : "hover:bg-black/5 dark:hover:bg-white/5"
                  }`}
                >
                  Chat · {formatDate(s.lastActiveAt)}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card className="flex min-h-[28rem] flex-col">
        {!selectedId ? (
          <EmptyState message="Start a new chat or pick a past conversation." />
        ) : (
          <>
            <div className="flex-1 space-y-3 overflow-y-auto" data-testid="message-thread">
              {loadingHistory ? (
                <Spinner />
              ) : messages.length === 0 ? (
                <p className="py-8 text-center text-sm text-foreground-subtle">
                  Ask about your spending, e.g. “How much did I spend on food last month?”
                </p>
              ) : (
                messages.map((m) => (
                  <div
                    key={m.id}
                    className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${
                      m.role === "user"
                        ? "ml-auto bg-brand-700 text-white"
                        : "mr-auto bg-black/[0.05] dark:bg-white/[0.08]"
                    }`}
                  >
                    {m.message}
                  </div>
                ))
              )}
            </div>

            {error && <div className="mt-3"><ErrorState message={error} /></div>}

            <form onSubmit={send} className="mt-3 flex gap-2">
              <input
                type="text"
                aria-label="Message"
                placeholder="Type a message…"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                className="flex-1 rounded-md border border-black/15 px-3 py-2 text-sm dark:border-white/15 dark:bg-neutral-800"
              />
              <button
                type="submit"
                disabled={sending || !input.trim()}
                className="rounded-md bg-brand-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {sending ? "…" : "Send"}
              </button>
            </form>
          </>
        )}
      </Card>
    </div>
  );
}
