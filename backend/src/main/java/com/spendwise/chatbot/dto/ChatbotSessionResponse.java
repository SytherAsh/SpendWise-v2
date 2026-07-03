package com.spendwise.chatbot.dto;

import com.spendwise.chatbot.ChatbotSession;

import java.time.Instant;
import java.util.UUID;

public record ChatbotSessionResponse(UUID id, Instant createdAt, Instant lastActiveAt) {

    public static ChatbotSessionResponse from(ChatbotSession session) {
        return new ChatbotSessionResponse(session.id(), session.createdAt(), session.lastActiveAt());
    }
}
