package com.spendwise.ui.screens.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * E9-S1-T4 — questionnaire (`PUT /users/me/preferences`) plus optional bank-statement
 * upload. The upload endpoint is documented in docs/api.md but not implemented server-side
 * yet (flagged at the Epic 9 handoff review) — failures are soft: the user is told and can
 * always skip.
 */
@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val session: DeviceSessionStore,
) : ViewModel() {

    sealed interface UiState {
        data object Editing : UiState

        data object Saving : UiState

        /** Preferences saved; the optional upload step is showing. */
        data object UploadOffer : UiState

        data object Uploading : UiState

        data object UploadSucceeded : UiState

        data object Done : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Editing)
    val state: StateFlow<UiState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun savePreferences(apps: List<String>, banks: List<String>, monthlyEstimate: Double?) {
        _error.value = null
        _state.value = UiState.Saving
        viewModelScope.launch {
            try {
                api.updatePreferences(
                    UserPreferences(
                        selectedApps = apps.ifEmpty { null },
                        selectedBanks = banks.ifEmpty { null },
                        monthlySpendEstimate = monthlyEstimate,
                    ),
                )
                _state.value = UiState.UploadOffer
            } catch (e: Exception) {
                _state.value = UiState.Editing
                _error.value = e.message ?: "Couldn't save your answers — check your connection"
            }
        }
    }

    fun uploadStatement(context: Context, uri: Uri) {
        _error.value = null
        _state.value = UiState.Uploading
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Couldn't read the selected file")
                val part = MultipartBody.Part.createFormData(
                    "file",
                    "bank-statement.pdf",
                    bytes.toRequestBody("application/pdf".toMediaType()),
                )
                val response = api.uploadBankStatement(part)
                if (response.isSuccessful) {
                    _state.value = UiState.UploadSucceeded
                } else {
                    _state.value = UiState.UploadOffer
                    _error.value = "Upload isn't available yet (${response.code()}) — you can skip this step"
                }
            } catch (e: Exception) {
                _state.value = UiState.UploadOffer
                _error.value = e.message ?: "Upload failed — you can skip this step"
            }
        }
    }

    /** Skip (docs/user_flows.md step 7: "User can skip this step") or finish after upload. */
    fun finish() {
        session.setQuestionnaireDone()
        _state.value = UiState.Done
    }
}
