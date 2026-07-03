package com.spendwise.chatbot.dto;

import com.spendwise.chatbot.ChatbotSessionDetail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatbotSessionHistoryResponse(UUID id, Instant createdAt, Instant lastActiveAt, List<ChatbotMessageResponse> messages) {

    public static ChatbotSessionHistoryResponse from(ChatbotSessionDetail detail) {
        return new ChatbotSessionHistoryResponse(
                detail.session().id(),
                detail.session().createdAt(),
                detail.session().lastActiveAt(),
                detail.messages().stream().map(ChatbotMessageResponse::from).toList());
    }
}
