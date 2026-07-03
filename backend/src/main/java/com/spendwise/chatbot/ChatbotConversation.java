package com.spendwise.chatbot;

import java.time.Instant;
import java.util.UUID;

public record ChatbotConversation(UUID id, UUID userId, UUID sessionId, ChatRole role, String message, Instant createdAt) {}
