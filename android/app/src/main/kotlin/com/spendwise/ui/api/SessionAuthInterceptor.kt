package com.spendwise.ui.api

import com.spendwise.storage.DeviceSessionStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the SpendWise JWT (never the Firebase ID token — CLAUDE.md auth pattern) to every
 * request that has a stored session. Public auth endpoints simply have no token yet.
 */
class SessionAuthInterceptor(private val session: DeviceSessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val jwt = session.getUserJwt() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $jwt")
            .build()
        return chain.proceed(request)
    }
}
