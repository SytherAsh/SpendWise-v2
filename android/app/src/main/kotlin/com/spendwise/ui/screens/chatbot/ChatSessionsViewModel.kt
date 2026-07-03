package com.spendwise.ui.screens.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.ui.api.ChatbotSessionResponse
import com.spendwise.ui.api.SpendWiseApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S2-T5 — chat session list (`GET /chatbot/sessions`, ordered by `last_active_at`
 * server-side) plus new-session creation.
 */
@HiltViewModel
class ChatSessionsViewModel @Inject constructor(private val api: SpendWiseApi) : ViewModel() {

    data class UiState(
        val sessions: List<ChatbotSessionResponse> = emptyList(),
        val isLoading: Boolean = true,
        val isCreating: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Emits the new session id so the screen can navigate straight into the thread. */
    private val _created = MutableSharedFlow<String>()
    val created: SharedFlow<String> = _created

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                _state.value = UiState(sessions = api.listChatSessions(), isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Couldn't load chats")
            }
        }
    }

    fun newChat() {
        if (_state.value.isCreating) return
        _state.value = _state.value.copy(isCreating = true, error = null)
        viewModelScope.launch {
            try {
                val session = api.createChatSession()
                _state.value = _state.value.copy(isCreating = false)
                _created.emit(session.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isCreating = false, error = e.message ?: "Couldn't start a chat")
            }
        }
    }
}
