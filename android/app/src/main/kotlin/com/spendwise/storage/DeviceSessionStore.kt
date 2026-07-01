package com.spendwise.storage

import android.content.Context

/**
 * Local device session accessor (current user id, device API key) backing the SMS/Sync
 * pipeline. Epic 1's Android auth flow and Epic 9's onboarding UI are what actually populate
 * these values at login/onboarding time — this scaffold is the seam they write into; Epic 2
 * itself has zero backend dependency and only reads whatever is already stored locally.
 */
class DeviceSessionStore(context: Context) : UserSessionProvider {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getDeviceApiKey(): String? = prefs.getString(KEY_DEVICE_API_KEY, null)

    fun getUserJwt(): String? = prefs.getString(KEY_USER_JWT, null)

    private companion object {
        const val PREFS_NAME = "spendwise_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_DEVICE_API_KEY = "device_api_key"
        const val KEY_USER_JWT = "user_jwt"
    }
}
