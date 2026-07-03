package com.spendwise.ui

import com.spendwise.ui.api.AlertListResponse
import com.spendwise.ui.api.AnalyticsTrendsResponse
import com.spendwise.ui.api.AuthTokenResponse
import com.spendwise.ui.api.BudgetProgressResponse
import com.spendwise.ui.api.BudgetResponse
import com.spendwise.ui.api.BudgetSuggestionResponse
import com.spendwise.ui.api.Category
import com.spendwise.ui.api.CategoryCorrectionResponse
import com.spendwise.ui.api.ChatbotMessageRequest
import com.spendwise.ui.api.ChatbotMessageResponse
import com.spendwise.ui.api.ChatbotSessionHistoryResponse
import com.spendwise.ui.api.ChatbotSessionResponse
import com.spendwise.ui.api.CorrectCategoryRequest
import com.spendwise.ui.api.CreateBudgetRequest
import com.spendwise.ui.api.EmiResponse
import com.spendwise.ui.api.GoogleLoginRequest
import com.spendwise.ui.api.LogoutRequest
import com.spendwise.ui.api.OnboardingRequest
import com.spendwise.ui.api.OnboardingResponse
import com.spendwise.ui.api.OtpSendRequest
import com.spendwise.ui.api.OtpVerifyRequest
import com.spendwise.ui.api.RecommendationResponse
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.TransactionListResponse
import com.spendwise.ui.api.TransactionResponse
import com.spendwise.ui.api.UserPreferences
import com.spendwise.ui.api.UserProfileResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Base fake for ViewModel unit tests: every endpoint fails loudly unless a test overrides
 * it, so a test can't silently depend on an endpoint it didn't stub.
 */
open class FakeSpendWiseApi : SpendWiseApi {
    private fun nothing(): Nothing = throw UnsupportedOperationException("not stubbed in this test")

    override suspend fun sendOtp(body: OtpSendRequest) = nothing()

    override suspend fun verifyOtp(body: OtpVerifyRequest): AuthTokenResponse = nothing()

    override suspend fun googleLogin(body: GoogleLoginRequest): AuthTokenResponse = nothing()

    override suspend fun logout(body: LogoutRequest) = nothing()

    override suspend fun getProfile(): UserProfileResponse = nothing()

    override suspend fun submitOnboarding(body: OnboardingRequest): OnboardingResponse = nothing()

    override suspend fun getPreferences(): UserPreferences = nothing()

    override suspend fun updatePreferences(body: UserPreferences): UserPreferences = nothing()

    override suspend fun uploadBankStatement(file: MultipartBody.Part): Response<Unit> = nothing()

    override suspend fun listTransactions(
        limit: Int?,
        cursor: String?,
        category: Int?,
        from: String?,
        to: String?,
    ): TransactionListResponse = nothing()

    override suspend fun getTransaction(id: String): TransactionResponse = nothing()

    override suspend fun correctCategory(id: String, body: CorrectCategoryRequest): CategoryCorrectionResponse =
        nothing()

    override suspend fun listCategories(): List<Category> = nothing()

    override suspend fun upsertBudget(body: CreateBudgetRequest): BudgetResponse = nothing()

    override suspend fun listBudgets(): List<BudgetResponse> = nothing()

    override suspend fun budgetProgress(): List<BudgetProgressResponse> = nothing()

    override suspend fun budgetSuggestions(): List<BudgetSuggestionResponse> = nothing()

    override suspend fun listEmis(): List<EmiResponse> = nothing()

    override suspend fun updateEmi(id: String, body: com.spendwise.ui.api.UpdateEmiRequest): EmiResponse = nothing()

    override suspend fun deactivateEmi(id: String): EmiResponse = nothing()

    override suspend fun listAlerts(limit: Int?, cursor: String?, isRead: Boolean?): AlertListResponse = nothing()

    override suspend fun markAlertRead(id: String) = nothing()

    override suspend fun confirmRecurringPayment(id: String): EmiResponse = nothing()

    override suspend fun listRecommendations(): List<RecommendationResponse> = nothing()

    override suspend fun dismissRecommendation(id: String) = nothing()

    override suspend fun createChatSession(): ChatbotSessionResponse = nothing()

    override suspend fun listChatSessions(): List<ChatbotSessionResponse> = nothing()

    override suspend fun getChatSession(id: String): ChatbotSessionHistoryResponse = nothing()

    override suspend fun sendChatMessage(body: ChatbotMessageRequest): ChatbotMessageResponse = nothing()

    override suspend fun trends(
        granularity: String?,
        from: String?,
        to: String?,
        category: Int?,
    ): AnalyticsTrendsResponse = nothing()

    override suspend fun exportCsv(from: String, to: String): ResponseBody = nothing()

    override suspend fun exportPdf(from: String?, to: String?, financialYear: Int?): ResponseBody = nothing()
}
