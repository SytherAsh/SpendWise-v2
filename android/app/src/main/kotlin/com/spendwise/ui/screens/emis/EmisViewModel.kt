package com.spendwise.ui.screens.emis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.ui.api.EmiResponse
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.UpdateEmiRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S2-T4 — active EMIs/subscriptions (`GET /emis` returns active-only), edit
 * (`PUT /emis/:id`), deactivate (`PATCH /emis/:id` — record retained server-side for
 * history; DoD: it just disappears from the active list, no deletion).
 */
@HiltViewModel
class EmisViewModel @Inject constructor(private val api: SpendWiseApi) : ViewModel() {

    data class UiState(
        val emis: List<EmiResponse> = emptyList(),
        val isLoading: Boolean = true,
        val isMutating: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                _state.value = UiState(emis = api.listEmis().filter { it.isActive }, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Couldn't load subscriptions")
            }
        }
    }

    fun update(emiId: String, label: String, amount: Double, dueDay: Int?) {
        _state.value = _state.value.copy(isMutating = true, error = null)
        viewModelScope.launch {
            try {
                val updated = api.updateEmi(emiId, UpdateEmiRequest(label, amount, dueDay))
                _state.value = _state.value.copy(
                    emis = _state.value.emis.map { if (it.id == emiId) updated else it },
                    isMutating = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isMutating = false, error = e.message ?: "Couldn't save changes")
            }
        }
    }

    fun deactivate(emiId: String) {
        _state.value = _state.value.copy(isMutating = true, error = null)
        viewModelScope.launch {
            try {
                api.deactivateEmi(emiId)
                _state.value = _state.value.copy(
                    emis = _state.value.emis.filterNot { it.id == emiId },
                    isMutating = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isMutating = false, error = e.message ?: "Couldn't deactivate")
            }
        }
    }
}
