package com.spendwise.chatbot;

import com.spendwise.analytics.AnalyticsService;
import com.spendwise.analytics.AnalyticsSummary;
import com.spendwise.common.llm.LlmClient;
import com.spendwise.common.llm.LlmResponse;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E8-S3-T2 — context injection has no NLU to parse a date range out of the user's actual question
 * ("How much did I spend on food last month?"), so every message pulls the same fixed window
 * (current + previous calendar month) regardless of wording — an explicit project-owner default
 * confirmed during the Epic 8 handoff review, directly answering the epic's own milestone
 * question without building any date-range parsing.
 */
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 50;
    private static final String CURRENT_MONTH_SUMMARY_KEY = "currentMonthSummary";
    private static final String PREVIOUS_MONTH_SUMMARY_KEY = "previousMonthSummary";
    private static final String RECENT_TRANSACTIONS_KEY = "recentTransactions";

    private final ChatbotSessionRepository sessionRepository;
    private final ChatbotConversationRepository conversationRepository;
    private final AnalyticsService analyticsService;
    private final TransactionService transactionService;
    private final LlmClient llmClient;

    public ChatbotServiceImpl(
            ChatbotSessionRepository sessionRepository,
            ChatbotConversationRepository conversationRepository,
            AnalyticsService analyticsService,
            TransactionService transactionService,
            LlmClient llmClient) {
        this.sessionRepository = sessionRepository;
        this.conversationRepository = conversationRepository;
        this.analyticsService = analyticsService;
        this.transactionService = transactionService;
        this.llmClient = llmClient;
    }

    @Override
    @Transactional
    public ChatbotSession createSession(UUID userId) {
        return sessionRepository.insert(userId);
    }

    @Override
    @Transactional
    public List<ChatbotSession> listSessions(UUID userId) {
        return sessionRepository.findByUser(userId);
    }

    @Override
    @Transactional
    public ChatbotSessionDetail getSessionHistory(UUID userId, UUID sessionId) {
        ChatbotSession session = sessionRepository.findById(userId, sessionId).orElseThrow(ChatbotSessionNotFoundException::new);
        List<ChatbotConversation> messages = conversationRepository.findBySession(userId, sessionId);
        return new ChatbotSessionDetail(session, messages);
    }

    @Override
    @Transactional
    public ChatbotConversation sendMessage(UUID userId, UUID sessionId, String message) {
        sessionRepository.findById(userId, sessionId).orElseThrow(ChatbotSessionNotFoundException::new);
        conversationRepository.insert(userId, sessionId, ChatRole.USER, message);

        Map<String, Object> context = buildContext(userId);
        LlmResponse response = llmClient.complete(message, context);

        ChatbotConversation assistantMessage = conversationRepository.insert(userId, sessionId, ChatRole.ASSISTANT, response.text());
        sessionRepository.touchLastActive(userId, sessionId);
        return assistantMessage;
    }

    private Map<String, Object> buildContext(UUID userId) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        Instant currentMonthStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant previousMonthStart = previousMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = Instant.now();

        AnalyticsSummary currentMonthSummary = analyticsService.summary(userId, currentMonthStart, now);
        AnalyticsSummary previousMonthSummary =
                analyticsService.summary(userId, previousMonthStart, currentMonthStart.minusMillis(1));
        List<Transaction> recentTransactions = transactionService
                .list(userId, RECENT_TRANSACTIONS_LIMIT, null, null, false, previousMonthStart, now, null, null)
                .data();

        return Map.of(
                CURRENT_MONTH_SUMMARY_KEY, currentMonthSummary,
                PREVIOUS_MONTH_SUMMARY_KEY, previousMonthSummary,
                RECENT_TRANSACTIONS_KEY, recentTransactions);
    }
}
