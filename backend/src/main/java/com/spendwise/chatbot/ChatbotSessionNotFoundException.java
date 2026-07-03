package com.spendwise.chatbot;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class ChatbotSessionNotFoundException extends ApiException {

    public ChatbotSessionNotFoundException() {
        super("CHATBOT_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "Chat session not found");
    }
}
