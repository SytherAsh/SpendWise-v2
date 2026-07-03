package com.spendwise.ui.api

import com.spendwise.storage.DeviceSessionStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * On a 401, rotates the refresh token via `POST /auth/token/refresh` (docs/security.md
 * rotation + replay semantics) and retries the failed request once with the new access
 * token. If rotation itself fails (revoked/replayed/expired), the session is cleared so the
 * UI's session watcher drops the user back to sign-up.
 *
 * Uses its own bare [OkHttpClient] for the refresh call — routing it through the main client
 * would re-enter this authenticator on failure and loop.
 */
class TokenRefreshAuthenticator(
    private val session: DeviceSessionStore,
    private val baseUrl: String,
    private val onSessionExpired: () -> Unit = {},
) : Authenticator {

    private val refreshClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never try to re-auth the refresh call itself, and give up after one retry.
        if (response.request.url.encodedPath.endsWith("/auth/token/refresh")) return null
        if (response.priorResponse != null) return null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(this) {
            // Another thread may have already rotated while we waited on the lock.
            val current = session.getUserJwt()
            if (current != null && current != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .build()
            }

            val refreshToken = session.getRefreshToken() ?: return null
            val rotated = rotate(refreshToken) ?: run {
                session.clear()
                onSessionExpired()
                return null
            }
            session.saveRotatedTokens(rotated.accessToken, rotated.refreshToken)
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${rotated.accessToken}")
                .build()
        }
    }

    private fun rotate(refreshToken: String): TokenRefreshResponse? {
        val body = json.encodeToString(TokenRefreshRequest.serializer(), TokenRefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/auth/token/refresh")
            .post(body)
            .build()
        return runCatching {
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                json.decodeFromString(TokenRefreshResponse.serializer(), resp.body.string())
            }
        }.getOrNull()
    }
}
