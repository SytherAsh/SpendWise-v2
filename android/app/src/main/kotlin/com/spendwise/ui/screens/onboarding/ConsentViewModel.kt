package com.spendwise.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.ui.api.OnboardingRequest
import com.spendwise.ui.api.SpendWiseApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S1-T2. Accepting POSTs the consent snapshot to `/users/me/onboarding` and stores the
 * returned device API key immediately (docs/api.md: "only occurrence — store immediately in
 * device secure storage"). Declining does nothing server-side — the screen blocks.
 */
@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val session: DeviceSessionStore,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState

        data object Submitting : UiState

        data object Accepted : UiState

        /** ADR-005: declined = blocked; the app is non-functional without consent. */
        data object Declined : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun accept(appVersion: String?) {
        if (_state.value == UiState.Submitting) return
        _error.value = null
        _state.value = UiState.Submitting
        viewModelScope.launch {
            try {
                val response = api.submitOnboarding(Companion.consentPayload(appVersion))
                session.saveDeviceApiKey(response.deviceApiKey)
                _state.value = UiState.Accepted
            } catch (e: Exception) {
                _state.value = UiState.Idle
                _error.value = e.message ?: "Couldn't record consent — check your connection"
            }
        }
    }

    fun decline() {
        _state.value = UiState.Declined
    }

    fun backToConsent() {
        _state.value = UiState.Idle
    }

    companion object {
        /** The exact request sent to the API — pure so the E9-S1-T2 unit test can assert it. */
        fun consentPayload(appVersion: String?): OnboardingRequest =
            OnboardingRequest(consentText = ConsentText.FULL_TEXT, appVersion = appVersion)
    }
}
