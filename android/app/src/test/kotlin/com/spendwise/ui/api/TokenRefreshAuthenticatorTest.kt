package com.spendwise.ui.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spendwise.storage.DeviceSessionStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 401 → `POST /auth/token/refresh` → retry-once path, against a real HTTP client and
 * MockWebServer (docs/security.md rotation semantics; CLAUDE.md JWT session pattern).
 */
@RunWith(RobolectricTestRunner::class)
class TokenRefreshAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var session: DeviceSessionStore
    private var expired = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        session = DeviceSessionStore(context.getSharedPreferences("auth_test", Context.MODE_PRIVATE))
        expired = false
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(SessionAuthInterceptor(session))
        .authenticator(TokenRefreshAuthenticator(session, server.url("/").toString()) { expired = true })
        .build()

    @Test
    fun `on 401 rotates the refresh token and retries with the new access token`() {
        session.saveLoginSession("user-1", "stale-access", "refresh-1")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"fresh-access","refreshToken":"refresh-2","expiresIn":604800}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val response = client().newCall(Request.Builder().url(server.url("/api/v1/budgets")).build()).execute()

        assertEquals(200, response.code)
        // Request 1: original with the stale token.
        assertEquals("Bearer stale-access", server.takeRequest().getHeader("Authorization"))
        // Request 2: the refresh call carrying the old refresh token.
        val refreshRequest = server.takeRequest()
        assertEquals("/auth/token/refresh", refreshRequest.path)
        assertTrue(refreshRequest.body.readUtf8().contains("refresh-1"))
        // Request 3: the retry with the fresh access token.
        assertEquals("Bearer fresh-access", server.takeRequest().getHeader("Authorization"))
        // Rotated pair persisted.
        assertEquals("fresh-access", session.getUserJwt())
        assertEquals("refresh-2", session.getRefreshToken())
    }

    @Test
    fun `failed rotation clears the session and signals expiry`() {
        session.saveLoginSession("user-1", "stale-access", "revoked-refresh")
        server.enqueue(MockResponse().setResponseCode(401)) // original request
        server.enqueue(MockResponse().setResponseCode(401)) // refresh rejected (replay/revoked)

        val response = client().newCall(Request.Builder().url(server.url("/api/v1/budgets")).build()).execute()

        assertEquals(401, response.code) // surfaced, not retried
        assertNull(session.getUserJwt())
        assertNull(session.getRefreshToken())
        assertTrue(expired)
    }

    @Test
    fun `gives up after a single retry instead of looping`() {
        session.saveLoginSession("user-1", "stale-access", "refresh-1")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"fresh-access","refreshToken":"refresh-2","expiresIn":604800}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(401)) // still 401 after refresh

        val response = client().newCall(Request.Builder().url(server.url("/api/v1/budgets")).build()).execute()

        assertEquals(401, response.code)
        assertEquals(3, server.requestCount) // original + refresh + one retry; no fourth call
    }

    @Test
    fun `no refresh token means no refresh attempt`() {
        session.saveLoginSession("user-1", "stale-access", "refresh-1")
        session.clear()
        server.enqueue(MockResponse().setResponseCode(401))

        val response = client().newCall(Request.Builder().url(server.url("/api/v1/budgets")).build()).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }
}
