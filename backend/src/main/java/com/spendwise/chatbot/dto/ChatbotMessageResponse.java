package com.spendwise.chatbot.dto;

import com.spendwise.chatbot.ChatbotConversation;

import java.time.Instant;
import java.util.UUID;

public record ChatbotMessageResponse(UUID id, String role, String message, Instant createdAt) {

    public static ChatbotMessageResponse from(ChatbotConversation conversation) {
        return new ChatbotMessageResponse(
                conversation.id(), conversation.role().dbValue(), conversation.message(), conversation.createdAt());
    }
}
