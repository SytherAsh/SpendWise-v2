package com.spendwise.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * E9-S1-T1 Required Test — the token-storage helper round-trips the login session, rotated
 * tokens, device API key, and wizard flags, and [DeviceSessionStore.clear] wipes all of it.
 * Uses a plain SharedPreferences through the injectable constructor (Robolectric has no
 * real Keystore); the encrypted wiring is exercised on-device via the manual QA checklist.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceSessionStoreTest {

    private lateinit var store: DeviceSessionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = DeviceSessionStore(context.getSharedPreferences("test_session", Context.MODE_PRIVATE))
    }

    @Test
    fun `empty store returns nulls and false flags`() {
        assertNull(store.getUserId())
        assertNull(store.getUserJwt())
        assertNull(store.getRefreshToken())
        assertNull(store.getDeviceApiKey())
        assertFalse(store.isQuestionnaireDone())
        assertFalse(store.isBackfillDone())
    }

    @Test
    fun `login session round-trips`() {
        store.saveLoginSession("user-1", "access-token", "refresh-token")

        assertEquals("user-1", store.getUserId())
        assertEquals("access-token", store.getUserJwt())
        assertEquals("refresh-token", store.getRefreshToken())
    }

    @Test
    fun `token rotation replaces the pair but keeps the user`() {
        store.saveLoginSession("user-1", "access-1", "refresh-1")
        store.saveRotatedTokens("access-2", "refresh-2")

        assertEquals("user-1", store.getUserId())
        assertEquals("access-2", store.getUserJwt())
        assertEquals("refresh-2", store.getRefreshToken())
    }

    @Test
    fun `device api key and wizard flags round-trip`() {
        store.saveDeviceApiKey("raw-device-key")
        store.setQuestionnaireDone()
        store.setBackfillDone()

        assertEquals("raw-device-key", store.getDeviceApiKey())
        assertTrue(store.isQuestionnaireDone())
        assertTrue(store.isBackfillDone())
    }

    @Test
    fun `clear wipes everything`() {
        store.saveLoginSession("user-1", "access", "refresh")
        store.saveDeviceApiKey("key")
        store.setQuestionnaireDone()
        store.setBackfillDone()

        store.clear()

        assertNull(store.getUserId())
        assertNull(store.getUserJwt())
        assertNull(store.getRefreshToken())
        assertNull(store.getDeviceApiKey())
        assertFalse(store.isQuestionnaireDone())
        assertFalse(store.isBackfillDone())
    }
}
