package com.spendwise.ui.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of requesting an OTP: either auto-verified (instant on some devices) or code-sent. */
sealed interface OtpRequestResult {
    /** Play services auto-retrieved/instant-verified the SMS — no manual code entry needed. */
    data class AutoVerified(val credential: PhoneAuthCredential) : OtpRequestResult

    data class CodeSent(val verificationId: String) : OtpRequestResult
}

/**
 * Wraps the Firebase client SDK for E9-S1-T1. The backend (`AuthController`) expects a
 * **Firebase** ID token — for phone, produced by OTP sign-in; for Google, the Credential
 * Manager's Google ID token is first exchanged for a Firebase session, whose ID token is
 * what gets POSTed to `/auth/google` (Firebase Admin `verifyIdToken` accepts only Firebase
 * ID tokens, not raw Google ones).
 */
@Singleton
class FirebaseAuthClient @Inject constructor() {

    private val firebaseAuth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /**
     * Starts phone-number verification. Firebase delivers the OTP SMS itself (the backend's
     * `/auth/otp/send` is only the rate-limit backstop, called separately by the ViewModel).
     */
    suspend fun requestOtp(activity: Activity, phoneE164: String): OtpRequestResult =
        suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (cont.isActive) cont.resume(OtpRequestResult.AutoVerified(credential))
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (cont.isActive) cont.resume(OtpRequestResult.CodeSent(verificationId))
                }
            }
            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    /** Completes OTP sign-in with the user-typed code and returns the Firebase ID token. */
    suspend fun signInWithOtp(verificationId: String, code: String): String {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return signInAndGetIdToken(credential)
    }

    /** Completes sign-in for the auto-verified path. */
    suspend fun signInWithAutoCredential(credential: PhoneAuthCredential): String =
        signInAndGetIdToken(credential)

    /** Full Google Sign-In: Credential Manager → Google ID token → Firebase ID token. */
    suspend fun signInWithGoogle(activityContext: Context): String {
        val webClientId = resolveWebClientId(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
        val googleIdToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        return signInAndGetIdToken(firebaseCredential)
    }

    private suspend fun signInAndGetIdToken(credential: com.google.firebase.auth.AuthCredential): String {
        val result = firebaseAuth.signInWithCredential(credential).await()
        val user = result.user ?: throw IllegalStateException("Firebase sign-in returned no user")
        return user.getIdToken(false).await().token
            ?: throw IllegalStateException("Firebase returned no ID token")
    }

    /**
     * `default_web_client_id` is generated by the google-services Gradle plugin, which is
     * only applied when google-services.json is present (it's gitignored — CI builds without
     * it). Resolved by name at runtime instead of `R.string.` so CI still compiles.
     */
    private fun resolveWebClientId(context: Context): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        check(id != 0) { "default_web_client_id missing — was the app built without google-services.json?" }
        return context.getString(id)
    }
}
