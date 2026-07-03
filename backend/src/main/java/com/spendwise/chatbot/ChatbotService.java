package com.spendwise.chatbot;

import java.util.List;
import java.util.UUID;

public interface ChatbotService {

    ChatbotSession createSession(UUID userId);

    /** Ordered by {@code last_active_at DESC} (docs/api.md). */
    List<ChatbotSession> listSessions(UUID userId);

    /** @throws ChatbotSessionNotFoundException if absent or owned by a different user */
    ChatbotSessionDetail getSessionHistory(UUID userId, UUID sessionId);

    /**
     * Persists the user's message, builds a grounding context from the user's current + previous
     * calendar month spend (Analytics) and recent transaction history (Transaction) — the
     * module's only two permitted reads, per docs/architecture.md's Chatbot row — calls {@link
     * com.spendwise.common.llm.LlmClient}, persists the response, and updates the session's
     * {@code last_active_at}. Returns the persisted assistant message.
     *
     * @throws ChatbotSessionNotFoundException if absent or owned by a different user
     */
    ChatbotConversation sendMessage(UUID userId, UUID sessionId, String message);
}
