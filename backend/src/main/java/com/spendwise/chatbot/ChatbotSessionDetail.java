package com.spendwise.chatbot;

import java.util.List;

/** Aggregate for {@code GET /chatbot/sessions/:id}'s full history response (E8-S3-T1). */
public record ChatbotSessionDetail(ChatbotSession session, List<ChatbotConversation> messages) {}
