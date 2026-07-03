package com.spendwise.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.ui.api.LogoutRequest
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * E9-S2-T6 — alert-channel preferences, report export (CSV/PDF via the Analytics export
 * endpoints), logout, privacy-policy access.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val session: DeviceSessionStore,
) : ViewModel() {

    data class UiState(
        val pushEnabled: Boolean = true,
        val emailEnabled: Boolean = true,
        val isLoading: Boolean = true,
        val isExporting: Boolean = false,
        val loggedOut: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Emits the exported file ready to be handed to the Android share sheet. */
    private val _exportReady = MutableSharedFlow<File>()
    val exportReady: SharedFlow<File> = _exportReady

    init {
        viewModelScope.launch {
            try {
                val prefs = api.getPreferences()
                _state.value = _state.value.copy(
                    pushEnabled = prefs.alertChannels?.get("push") ?: true,
                    emailEnabled = prefs.alertChannels?.get("email") ?: true,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Couldn't load preferences")
            }
        }
    }

    fun setAlertChannels(push: Boolean, email: Boolean) {
        val previous = _state.value
        _state.value = previous.copy(pushEnabled = push, emailEnabled = email, error = null)
        viewModelScope.launch {
            try {
                // Partial update: only alertChannels — other preference fields stay untouched.
                api.updatePreferences(UserPreferences(alertChannels = mapOf("push" to push, "email" to email)))
            } catch (e: Exception) {
                _state.value = previous.copy(error = e.message ?: "Couldn't save preferences")
            }
        }
    }

    /**
     * Downloads the report into the app cache and emits it for the share sheet
     * (docs/user_flows.md "Exporting a Report": custom range or financial year, PDF or CSV).
     */
    fun export(context: Context, format: ExportFormat, fromIso: String, toIso: String) {
        _state.value = _state.value.copy(isExporting = true, error = null)
        viewModelScope.launch {
            try {
                val body = when (format) {
                    ExportFormat.CSV -> api.exportCsv(fromIso, toIso)
                    ExportFormat.PDF -> api.exportPdf(from = fromIso, to = toIso)
                }
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "spendwise-report.${format.extension}").apply {
                        outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                    }
                }
                _state.value = _state.value.copy(isExporting = false)
                _exportReady.emit(file)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isExporting = false, error = e.message ?: "Export failed")
            }
        }
    }

    /** DoD: logout clears the local session and returns to sign-up. */
    fun logout() {
        viewModelScope.launch {
            val refreshToken = session.getRefreshToken()
            // Best-effort server-side revocation; local clearing happens regardless.
            if (refreshToken != null) {
                runCatching { api.logout(LogoutRequest(refreshToken)) }
            }
            session.clear()
            _state.value = _state.value.copy(loggedOut = true)
        }
    }

    enum class ExportFormat(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        PDF("pdf", "application/pdf"),
    }
}
