package com.spendwise.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.sms.SmsForegroundService
import com.spendwise.sms.SmsInboxBackfill
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S1-T5 — runs Epic 2's first-launch inbox backfill ([SmsInboxBackfill], which already
 * chains filter → parse → dedup → Room queue → one immediate sync), then starts the ongoing
 * foreground monitoring service and the periodic sync schedule (docs/user_flows.md
 * Onboarding steps 8-10).
 */
@HiltViewModel
class BackfillViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val session: DeviceSessionStore,
) : ViewModel() {

    sealed interface UiState {
        data object Running : UiState

        data object Complete : UiState

        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Running)
    val state: StateFlow<UiState> = _state

    private var started = false

    fun runBackfill() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                SmsInboxBackfill.create(appContext).run(appContext)
                startMonitoring()
                session.setBackfillDone()
                _state.value = UiState.Complete
            } catch (e: Exception) {
                started = false
                _state.value = UiState.Failed(e.message ?: "Couldn't scan your SMS inbox")
            }
        }
    }

    private fun startMonitoring() {
        ContextCompat.startForegroundService(appContext, Intent(appContext, SmsForegroundService::class.java))
        SyncScheduler.schedulePeriodic(appContext)
    }
}
