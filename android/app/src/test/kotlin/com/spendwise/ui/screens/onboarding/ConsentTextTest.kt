package com.spendwise.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E9-S1-T2 Required Test — the consent text constant sent to the API matches what the
 * screen renders. `ConsentScreen` renders [ConsentText.HEADING], [ConsentText.INTRO], and
 * each of [ConsentText.PURPOSES] directly; the submitted payload is [ConsentText.FULL_TEXT].
 * These tests pin the invariant that FULL_TEXT is assembled from exactly those rendered
 * constants and that the API payload carries it verbatim.
 */
class ConsentTextTest {

    @Test
    fun `full text contains the rendered heading and intro verbatim`() {
        assertTrue(ConsentText.FULL_TEXT.contains(ConsentText.HEADING))
        assertTrue(ConsentText.FULL_TEXT.contains(ConsentText.INTRO))
    }

    @Test
    fun `full text contains every rendered purpose verbatim`() {
        ConsentText.PURPOSES.forEach { purpose ->
            assertTrue("missing purpose: $purpose", ConsentText.FULL_TEXT.contains(purpose))
        }
    }

    @Test
    fun `full text is exactly the rendered pieces in render order`() {
        val expected = ConsentText.HEADING + "\n\n" + ConsentText.INTRO + "\n\n" +
            ConsentText.PURPOSES.joinToString("\n\n") { "• $it" }
        assertEquals(expected, ConsentText.FULL_TEXT)
    }

    @Test
    fun `all three DPDP purposes are covered`() {
        // docs/security.md "Consent at Onboarding" — SMS read, server storage, ML training.
        assertEquals(3, ConsentText.PURPOSES.size)
        assertTrue(ConsentText.PURPOSES[0].contains("SMS read access"))
        assertTrue(ConsentText.PURPOSES[1].contains("Server-side data storage"))
        assertTrue(ConsentText.PURPOSES[2].contains("ML model improvement"))
    }

    @Test
    fun `api payload carries the full consent snapshot verbatim`() {
        val payload = ConsentViewModel.consentPayload(appVersion = "0.1.0")
        assertEquals(ConsentText.FULL_TEXT, payload.consentText)
        assertEquals("0.1.0", payload.appVersion)
    }
}
