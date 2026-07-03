package com.spendwise.ui.screens.onboarding

/**
 * The canonical DPDP consent copy (docs/security.md "Consent at Onboarding"; ADR-005 single
 * all-or-nothing screen). The consent screen renders **these constants** and the ViewModel
 * sends [FULL_TEXT] verbatim to `POST /users/me/onboarding` — one source of truth, so the
 * text a user saw is exactly the snapshot persisted server-side (E9-S1-T2 DoD). Any copy
 * change here IS a consent-text change and must be treated as such.
 */
object ConsentText {

    const val HEADING = "Your data, your consent"

    const val INTRO =
        "To work, SpendWise needs your explicit consent for all three of the following. " +
            "SpendWise is non-functional without them, so consent is all-or-nothing."

    val PURPOSES = listOf(
        "SMS read access — SpendWise reads financial transaction SMS on your device. " +
            "Raw SMS text never leaves your phone; only structured fields " +
            "(amount, recipient, date) are synced.",
        "Server-side data storage — your parsed transaction data is stored encrypted " +
            "on SpendWise servers so it can be shown across your devices.",
        "ML model improvement — anonymized transaction data and your category " +
            "corrections are used to retrain SpendWise's categorization model.",
    )

    /** The exact string submitted as the consent snapshot. */
    val FULL_TEXT: String =
        HEADING + "\n\n" + INTRO + "\n\n" + PURPOSES.joinToString("\n\n") { "• $it" }
}

/**
 * Single source for the privacy-policy link (docs/security.md: linked in consent, reachable
 * from Settings). No public URL is hosted yet — hosting it is an Epic 12 launch item
 * (docs/roadmap.md "Privacy policy hosted at a public URL"); update this constant then.
 */
object PrivacyPolicy {
    const val URL = "https://github.com/SytherAsh/SpendWise-v2/blob/main/docs/security.md"
}
