package com.spendwise.ui.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The backend REST contract (docs/api.md) as consumed by the Android UI. Base URL already
 * includes the `/api/v1/` prefix (see `local.properties` `API_BASE_URL` / [SyncConfig]).
 * `/ingest` is deliberately absent — that belongs to the Sync module's own client.
 */
interface SpendWiseApi {

    // --- Auth ---

    @POST("auth/otp/send")
    suspend fun sendOtp(@Body body: OtpSendRequest)

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): AuthTokenResponse

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): AuthTokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    // --- Users ---

    @GET("users/me")
    suspend fun getProfile(): UserProfileResponse

    @POST("users/me/onboarding")
    suspend fun submitOnboarding(@Body body: OnboardingRequest): OnboardingResponse

    @GET("users/me/preferences")
    suspend fun getPreferences(): UserPreferences

    @PUT("users/me/preferences")
    suspend fun updatePreferences(@Body body: UserPreferences): UserPreferences

    /**
     * Documented in docs/api.md but not yet implemented server-side (flagged during the
     * Epic 9 handoff review) — calls fail with 404 until the backend endpoint lands; the
     * questionnaire screen treats that as a soft failure and offers skip.
     */
    @Multipart
    @POST("users/me/bank-statement")
    suspend fun uploadBankStatement(@Part file: MultipartBody.Part): Response<Unit>

    // --- Transactions & categories ---

    @GET("transactions")
    suspend fun listTransactions(
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("category") category: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): TransactionListResponse

    @GET("transactions/{id}")
    suspend fun getTransaction(@Path("id") id: String): TransactionResponse

    @PUT("transactions/{id}/category")
    suspend fun correctCategory(
        @Path("id") id: String,
        @Body body: CorrectCategoryRequest,
    ): CategoryCorrectionResponse

    @GET("categories")
    suspend fun listCategories(): List<Category>

    // --- Budgets ---

    @POST("budgets")
    suspend fun upsertBudget(@Body body: CreateBudgetRequest): BudgetResponse

    @GET("budgets")
    suspend fun listBudgets(): List<BudgetResponse>

    @GET("budgets/progress")
    suspend fun budgetProgress(): List<BudgetProgressResponse>

    @GET("budgets/suggestions")
    suspend fun budgetSuggestions(): List<BudgetSuggestionResponse>

    // --- EMIs ---

    @GET("emis")
    suspend fun listEmis(): List<EmiResponse>

    @PUT("emis/{id}")
    suspend fun updateEmi(@Path("id") id: String, @Body body: UpdateEmiRequest): EmiResponse

    @PATCH("emis/{id}")
    suspend fun deactivateEmi(@Path("id") id: String): EmiResponse

    // --- Alerts ---

    @GET("alerts")
    suspend fun listAlerts(
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("is_read") isRead: Boolean? = null,
    ): AlertListResponse

    @PUT("alerts/{id}/read")
    suspend fun markAlertRead(@Path("id") id: String)

    @POST("alerts/{id}/confirm")
    suspend fun confirmRecurringPayment(@Path("id") id: String): EmiResponse

    // --- Recommendations ---

    @GET("recommendations")
    suspend fun listRecommendations(): List<RecommendationResponse>

    @PUT("recommendations/{id}/dismiss")
    suspend fun dismissRecommendation(@Path("id") id: String)

    // --- Chatbot ---

    @POST("chatbot/sessions")
    suspend fun createChatSession(): ChatbotSessionResponse

    @GET("chatbot/sessions")
    suspend fun listChatSessions(): List<ChatbotSessionResponse>

    @GET("chatbot/sessions/{id}")
    suspend fun getChatSession(@Path("id") id: String): ChatbotSessionHistoryResponse

    @POST("chatbot/message")
    suspend fun sendChatMessage(@Body body: ChatbotMessageRequest): ChatbotMessageResponse

    // --- Analytics (dashboard trends + settings export) ---

    @GET("analytics/trends")
    suspend fun trends(
        @Query("granularity") granularity: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("category") category: Int? = null,
    ): AnalyticsTrendsResponse

    @Streaming
    @GET("analytics/export/csv")
    suspend fun exportCsv(@Query("from") from: String, @Query("to") to: String): ResponseBody

    @Streaming
    @GET("analytics/export/pdf")
    suspend fun exportPdf(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("financialYear") financialYear: Int? = null,
    ): ResponseBody
}
