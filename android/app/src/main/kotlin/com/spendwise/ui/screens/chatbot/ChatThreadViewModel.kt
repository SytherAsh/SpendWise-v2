package com.spendwise.ui.screens.chatbot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.ui.api.ChatbotMessageRequest
import com.spendwise.ui.api.ChatbotMessageResponse
import com.spendwise.ui.api.SpendWiseApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S2-T5 — one chat session's thread. Resuming loads full history
 * (`GET /chatbot/sessions/:id`); sends go through `POST /chatbot/message`, and both the
 * user message and the assistant reply are persisted server-side (DoD: history survives
 * app restarts because the server owns it — nothing is stored locally).
 */
@HiltViewModel
class ChatThreadViewModel @Inject constructor(
    private val api: SpendWiseApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val messages: List<ChatbotMessageResponse> = emptyList(),
        val isLoading: Boolean = true,
        val isSending: Boolean = false,
        val error: String? = null,
    )

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            try {
                val history = api.getChatSession(sessionId)
                _state.value = UiState(messages = history.messages, isLoading = false)
            } catch (e: Exception) {
                _state.value = UiState(isLoading = false, error = e.message ?: "Couldn't load this chat")
            }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.isSending) return
        // Optimistic local echo of the user's message; replaced by server truth on reply.
        val localEcho = ChatbotMessageResponse(
            id = "local-${System.nanoTime()}",
            role = "user",
            message = message,
            createdAt = "",
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + localEcho,
            isSending = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                val reply = api.sendChatMessage(ChatbotMessageRequest(sessionId, message))
                _state.value = _state.value.copy(messages = _state.value.messages + reply, isSending = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages - localEcho,
                    isSending = false,
                    error = e.message ?: "Message didn't send",
                )
            }
        }
    }
}
