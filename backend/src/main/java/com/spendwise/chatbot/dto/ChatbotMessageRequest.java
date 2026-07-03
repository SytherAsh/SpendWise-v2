package com.spendwise.chatbot.dto;

import java.util.UUID;

/** docs/api.md "POST /chatbot/message ... requires sessionId in request body". */
public record ChatbotMessageRequest(UUID sessionId, String message) {}
