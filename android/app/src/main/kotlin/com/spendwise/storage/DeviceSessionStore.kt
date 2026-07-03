package com.spendwise.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local device session accessor (current user id, tokens, device API key) backing both the
 * Epic 2 SMS/Sync pipeline and Epic 9's UI. Epic 9's onboarding populates these values at
 * login/onboarding time; Epic 2 components only read them.
 *
 * Backed by [EncryptedSharedPreferences] per docs/api.md ("store immediately in device
 * secure storage") and E9-S1-T1's DoD. Jetpack Security is deprecated upstream but remains
 * the epic-specified mechanism; a DataStore+Keystore migration is a post-MVP concern.
 * The constructor accepts any [SharedPreferences] so unit tests (Robolectric has no real
 * Keystore) can substitute an ordinary in-memory instance — production wiring goes through
 * [create].
 */
class DeviceSessionStore(private val prefs: SharedPreferences) : UserSessionProvider {

    constructor(context: Context) : this(encryptedPrefs(context.applicationContext))

    override fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getDeviceApiKey(): String? = prefs.getString(KEY_DEVICE_API_KEY, null)

    fun getUserJwt(): String? = prefs.getString(KEY_USER_JWT, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    /** Persists the session issued by `/auth/otp/verify` or `/auth/google` (E9-S1-T1). */
    fun saveLoginSession(userId: String, accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_JWT, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    /** Persists the rotated pair from `/auth/token/refresh`. */
    fun saveRotatedTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_USER_JWT, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    /** Persists the raw device API key — returned exactly once by `/users/me/onboarding`. */
    fun saveDeviceApiKey(deviceApiKey: String) {
        prefs.edit().putString(KEY_DEVICE_API_KEY, deviceApiKey).apply()
    }

    /** Onboarding wizard progress flags so a killed app resumes at the right step (E9-S1). */
    fun isQuestionnaireDone(): Boolean = prefs.getBoolean(KEY_QUESTIONNAIRE_DONE, false)

    fun setQuestionnaireDone() {
        prefs.edit().putBoolean(KEY_QUESTIONNAIRE_DONE, true).apply()
    }

    fun isBackfillDone(): Boolean = prefs.getBoolean(KEY_BACKFILL_DONE, false)

    fun setBackfillDone() {
        prefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
    }

    /** Clears everything at logout (E9-S2-T6 DoD: "logout clears the local session"). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "spendwise_session"
        private const val ENCRYPTED_PREFS_NAME = "spendwise_session_secure"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_API_KEY = "device_api_key"
        private const val KEY_USER_JWT = "user_jwt"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_QUESTIONNAIRE_DONE = "questionnaire_done"
        private const val KEY_BACKFILL_DONE = "backfill_done"

        @Suppress("DEPRECATION")
        private fun encryptedPrefs(appContext: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            migrateLegacyPlainPrefs(appContext, encrypted)
            return encrypted
        }

        /**
         * Epic 2 stored this data in plain SharedPreferences on dev devices. One-time copy
         * into the encrypted store, then wipe the plain file so no token lingers unencrypted.
         */
        private fun migrateLegacyPlainPrefs(appContext: Context, encrypted: SharedPreferences) {
            val legacy = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isEmpty()) return
            val editor = encrypted.edit()
            legacy.getString(KEY_USER_ID, null)?.let { editor.putString(KEY_USER_ID, it) }
            legacy.getString(KEY_DEVICE_API_KEY, null)?.let { editor.putString(KEY_DEVICE_API_KEY, it) }
            legacy.getString(KEY_USER_JWT, null)?.let { editor.putString(KEY_USER_JWT, it) }
            editor.apply()
            legacy.edit().clear().apply()
        }
    }
}
