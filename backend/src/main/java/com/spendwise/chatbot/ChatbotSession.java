package com.spendwise.chatbot;

import java.time.Instant;
import java.util.UUID;

public record ChatbotSession(UUID id, UUID userId, Instant createdAt, Instant lastActiveAt) {}
