package com.spendwise.ui.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the OkHttp layer (which discovers session expiry synchronously on a worker thread)
 * to the Compose shell, which watches [sessionExpired] and routes back to sign-up.
 */
class SessionEvents {

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired

    fun notifySessionExpired() {
        _sessionExpired.value = true
    }

    /** Called once the UI has returned to sign-up so a later login isn't insta-bounced. */
    fun acknowledgeSessionExpired() {
        _sessionExpired.value = false
    }
}
