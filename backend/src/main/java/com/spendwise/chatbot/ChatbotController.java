package com.spendwise.chatbot;

import com.spendwise.chatbot.dto.ChatbotMessageRequest;
import com.spendwise.chatbot.dto.ChatbotMessageResponse;
import com.spendwise.chatbot.dto.ChatbotSessionHistoryResponse;
import com.spendwise.chatbot.dto.ChatbotSessionResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** docs/api.md "/chatbot" — owned by the Chatbot module. */
@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/sessions")
    public ChatbotSessionResponse createSession(@AuthenticationPrincipal UUID userId) {
        return ChatbotSessionResponse.from(chatbotService.createSession(userId));
    }

    @GetMapping("/sessions")
    public List<ChatbotSessionResponse> listSessions(@AuthenticationPrincipal UUID userId) {
        return chatbotService.listSessions(userId).stream().map(ChatbotSessionResponse::from).toList();
    }

    @GetMapping("/sessions/{id}")
    public ChatbotSessionHistoryResponse getSession(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ChatbotSessionHistoryResponse.from(chatbotService.getSessionHistory(userId, id));
    }

    @PostMapping("/message")
    public ChatbotMessageResponse sendMessage(@AuthenticationPrincipal UUID userId, @RequestBody ChatbotMessageRequest request) {
        return ChatbotMessageResponse.from(chatbotService.sendMessage(userId, request.sessionId(), request.message()));
    }
}
