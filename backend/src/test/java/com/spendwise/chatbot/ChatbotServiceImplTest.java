package com.spendwise.chatbot;

import com.spendwise.analytics.AnalyticsService;
import com.spendwise.analytics.AnalyticsSummary;
import com.spendwise.analytics.OverallTotals;
import com.spendwise.common.llm.LlmClient;
import com.spendwise.common.llm.LlmResponse;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionPage;
import com.spendwise.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Required tests for E8-S3-T1/T2 (docs/testing.md Chatbot unit tests). */
class ChatbotServiceImplTest {

    private final ChatbotSessionRepository sessionRepository = mock(ChatbotSessionRepository.class);
    private final ChatbotConversationRepository conversationRepository = mock(ChatbotConversationRepository.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final ChatbotServiceImpl service =
            new ChatbotServiceImpl(sessionRepository, conversationRepository, analyticsService, transactionService, llmClient);

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void getSessionHistoryThrowsWhenAbsentOrOwnedByAnotherUser() {
        given(sessionRepository.findById(userId, sessionId)).willReturn(Optional.empty());

        assertThrows(ChatbotSessionNotFoundException.class, () -> service.getSessionHistory(userId, sessionId));
    }

    @Test
    void getSessionHistoryReturnsMessagesInChronologicalOrder() {
        ChatbotSession session = new ChatbotSession(sessionId, userId, Instant.now(), Instant.now());
        given(sessionRepository.findById(userId, sessionId)).willReturn(Optional.of(session));
        ChatbotConversation first = new ChatbotConversation(UUID.randomUUID(), userId, sessionId, ChatRole.USER, "hi", Instant.now());
        ChatbotConversation second =
                new ChatbotConversation(UUID.randomUUID(), userId, sessionId, ChatRole.ASSISTANT, "hello", Instant.now());
        given(conversationRepository.findBySession(userId, sessionId)).willReturn(List.of(first, second));

        ChatbotSessionDetail detail = service.getSessionHistory(userId, sessionId);

        assertThat(detail.session()).isEqualTo(session);
        assertThat(detail.messages()).containsExactly(first, second);
    }

    @Test
    void sendMessageThrowsWhenSessionAbsentOrOwnedByAnotherUser() {
        given(sessionRepository.findById(userId, sessionId)).willReturn(Optional.empty());

        assertThrows(ChatbotSessionNotFoundException.class, () -> service.sendMessage(userId, sessionId, "hi"));
        verify(conversationRepository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void sendMessagePersistsBothTheUserMessageAndTheAssistantResponseInOrderAndTouchesLastActive() {
        stubSession();
        stubGroundingData();
        given(llmClient.complete(eq("How much did I spend on food last month?"), any())).willReturn(new LlmResponse("You spent 3240."));
        ChatbotConversation assistantRow =
                new ChatbotConversation(UUID.randomUUID(), userId, sessionId, ChatRole.ASSISTANT, "You spent 3240.", Instant.now());
        given(conversationRepository.insert(userId, sessionId, ChatRole.ASSISTANT, "You spent 3240.")).willReturn(assistantRow);

        ChatbotConversation result = service.sendMessage(userId, sessionId, "How much did I spend on food last month?");

        verify(conversationRepository).insert(userId, sessionId, ChatRole.USER, "How much did I spend on food last month?");
        verify(conversationRepository).insert(userId, sessionId, ChatRole.ASSISTANT, "You spent 3240.");
        verify(sessionRepository).touchLastActive(userId, sessionId);
        assertThat(result).isEqualTo(assistantRow);
    }

    @Test
    void theContextPassedToLlmClientContainsCurrentAndPreviousMonthGroundingData() {
        stubSession();
        AnalyticsSummary currentSummary = stubGroundingData();
        given(llmClient.complete(any(), any())).willReturn(new LlmResponse("text"));
        given(conversationRepository.insert(eq(userId), eq(sessionId), eq(ChatRole.ASSISTANT), any()))
                .willReturn(new ChatbotConversation(UUID.randomUUID(), userId, sessionId, ChatRole.ASSISTANT, "text", Instant.now()));

        service.sendMessage(userId, sessionId, "How much did I spend on food last month?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(llmClient).complete(eq("How much did I spend on food last month?"), contextCaptor.capture());
        Map<String, Object> context = contextCaptor.getValue();
        assertThat(context.get("currentMonthSummary")).isEqualTo(currentSummary);
        assertThat(context).containsKeys("previousMonthSummary", "recentTransactions");
        @SuppressWarnings("unchecked")
        List<Transaction> recentTransactions = (List<Transaction>) context.get("recentTransactions");
        assertThat(recentTransactions).hasSize(1);
        assertThat(recentTransactions.get(0).recipientName()).isEqualTo("Swiggy");
    }

    private void stubSession() {
        ChatbotSession session = new ChatbotSession(sessionId, userId, Instant.now(), Instant.now());
        given(sessionRepository.findById(userId, sessionId)).willReturn(Optional.of(session));
        given(conversationRepository.insert(eq(userId), eq(sessionId), eq(ChatRole.USER), any()))
                .willReturn(new ChatbotConversation(UUID.randomUUID(), userId, sessionId, ChatRole.USER, "msg", Instant.now()));
    }

    private AnalyticsSummary stubGroundingData() {
        AnalyticsSummary currentSummary = new AnalyticsSummary(new OverallTotals(BigDecimal.valueOf(3240), BigDecimal.ZERO), List.of());
        AnalyticsSummary previousSummary = new AnalyticsSummary(new OverallTotals(BigDecimal.valueOf(2350), BigDecimal.ZERO), List.of());
        given(analyticsService.summary(eq(userId), any(), any())).willReturn(currentSummary, previousSummary);
        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                userId,
                Instant.now(),
                BigDecimal.valueOf(250),
                BigDecimal.ZERO,
                BigDecimal.valueOf(-250),
                BigDecimal.valueOf(1000),
                "UPI",
                "DR",
                UUID.randomUUID().toString(),
                "Swiggy",
                "ICICI",
                "swiggy@upi",
                null,
                com.spendwise.transaction.TransactionSource.SMS,
                Instant.now(),
                7,
                0.9f,
                "ml",
                null);
        given(transactionService.list(eq(userId), anyInt(), isNull(), isNull(), eq(false), any(), any(), isNull()))
                .willReturn(new TransactionPage(List.of(transaction), null, false));
        return currentSummary;
    }
}
