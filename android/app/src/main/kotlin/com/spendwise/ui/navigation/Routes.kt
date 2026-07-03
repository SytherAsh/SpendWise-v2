package com.spendwise.ui.navigation

/**
 * Navigation routes. The onboarding wizard (E9-S1) is a linear graph; the main app (E9-S2)
 * is a bottom-nav scaffold with five primary destinations plus Settings via the top bar.
 */
object Routes {
    // Onboarding wizard (docs/user_flows.md Onboarding steps 2-10)
    const val SIGN_UP = "onboarding/sign-up"
    const val CONSENT = "onboarding/consent"
    const val PERMISSIONS = "onboarding/permissions"
    const val QUESTIONNAIRE = "onboarding/questionnaire"
    const val BACKFILL = "onboarding/backfill"

    // Main app (bottom nav)
    const val DASHBOARD = "main/dashboard"
    const val TRANSACTIONS = "main/transactions"
    const val BUDGET = "main/budget"
    const val EMIS = "main/emis"
    const val CHATBOT = "main/chatbot"

    // Top-bar destination
    const val SETTINGS = "main/settings"

    // Detail routes
    const val TRANSACTION_DETAIL = "main/transactions/{transactionId}"
    fun transactionDetail(transactionId: String) = "main/transactions/$transactionId"

    const val CHAT_THREAD = "main/chatbot/{sessionId}"
    fun chatThread(sessionId: String) = "main/chatbot/$sessionId"

    /** Transactions list pre-filtered to a category (dashboard drilldown, E9-S2-T1 DoD). */
    const val TRANSACTIONS_BY_CATEGORY = "main/transactions?category={categoryId}"
    fun transactionsByCategory(categoryId: Int) = "main/transactions?category=$categoryId"
}
