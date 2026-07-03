package com.spendwise.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.ui.api.AuthTokenResponse
import com.spendwise.ui.api.GoogleLoginRequest
import com.spendwise.ui.api.OtpSendRequest
import com.spendwise.ui.api.OtpVerifyRequest
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.auth.FirebaseAuthClient
import com.spendwise.ui.auth.OtpRequestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * E9-S1-T1. Two auth paths, one outcome: a Firebase ID token exchanged at the backend for
 * the SpendWise JWT pair, stored via [DeviceSessionStore] (the Firebase token is never kept —
 * CLAUDE.md: "the Spring Boot JWT is the authoritative session credential").
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val firebaseAuthClient: FirebaseAuthClient,
    private val session: DeviceSessionStore,
) : ViewModel() {

    sealed interface UiState {
        data object EnterPhone : UiState

        data object SendingOtp : UiState

        data class EnterOtp(val verificationId: String) : UiState

        data object Verifying : UiState

        data object SignedUp : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.EnterPhone)
    val state: StateFlow<UiState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var phoneE164: String = ""

    fun sendOtp(activity: Activity, rawPhone: String) {
        val phone = normalizePhone(rawPhone) ?: run {
            _error.value = "Enter a valid 10-digit mobile number"
            return
        }
        phoneE164 = phone
        _error.value = null
        _state.value = UiState.SendingOtp
        viewModelScope.launch {
            try {
                // Server-side rate-limit backstop first (docs/api.md: 429 = too many requests).
                api.sendOtp(OtpSendRequest(phone))
                when (val result = firebaseAuthClient.requestOtp(activity, phone)) {
                    is OtpRequestResult.CodeSent -> _state.value = UiState.EnterOtp(result.verificationId)
                    is OtpRequestResult.AutoVerified -> {
                        _state.value = UiState.Verifying
                        completeLogin { api.verifyOtp(OtpVerifyRequest(phone, firebaseAuthClient.signInWithAutoCredential(result.credential))) }
                    }
                }
            } catch (e: HttpException) {
                _state.value = UiState.EnterPhone
                _error.value = if (e.code() == 429) {
                    "Too many OTP requests — try again in an hour"
                } else {
                    "Couldn't send OTP (${e.code()})"
                }
            } catch (e: Exception) {
                _state.value = UiState.EnterPhone
                _error.value = e.message ?: "Couldn't send OTP"
            }
        }
    }

    fun verifyOtp(code: String) {
        val current = _state.value as? UiState.EnterOtp ?: return
        if (code.length < 6) {
            _error.value = "Enter the 6-digit code"
            return
        }
        _error.value = null
        _state.value = UiState.Verifying
        viewModelScope.launch {
            try {
                val idToken = firebaseAuthClient.signInWithOtp(current.verificationId, code)
                completeLogin { api.verifyOtp(OtpVerifyRequest(phoneE164, idToken)) }
            } catch (e: HttpException) {
                _state.value = current
                _error.value = if (e.code() == 400) "OTP expired or invalid" else "Sign-in failed (${e.code()})"
            } catch (e: Exception) {
                // DoD: invalid OTP shows an inline error, not a crash.
                _state.value = current
                _error.value = "Invalid code — check and try again"
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        _error.value = null
        _state.value = UiState.Verifying
        viewModelScope.launch {
            try {
                val idToken = firebaseAuthClient.signInWithGoogle(activityContext)
                completeLogin { api.googleLogin(GoogleLoginRequest(idToken)) }
            } catch (e: HttpException) {
                _state.value = UiState.EnterPhone
                _error.value = "Sign-in failed (${e.code()})"
            } catch (e: Exception) {
                _state.value = UiState.EnterPhone
                _error.value = e.message ?: "Google sign-in was cancelled or failed"
            }
        }
    }

    private suspend fun completeLogin(login: suspend () -> AuthTokenResponse) {
        try {
            val tokens = login()
            session.saveLoginSession(tokens.user.id, tokens.accessToken, tokens.refreshToken)
            _state.value = UiState.SignedUp
        } catch (e: HttpException) {
            _state.value = UiState.EnterPhone
            _error.value = "Sign-in failed (${e.code()})"
        } catch (e: Exception) {
            _state.value = UiState.EnterPhone
            _error.value = e.message ?: "Sign-in failed"
        }
    }

    /** Accepts `9876543210`, `+919876543210`, or `919876543210`; returns E.164 or null. */
    private fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.startsWith("+") && digits.length in 11..15 -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> null
        }
    }
}
