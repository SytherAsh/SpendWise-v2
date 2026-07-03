package com.spendwise.ui.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire DTOs for the backend REST API, mirrored field-for-field from the backend modules'
 * dto packages (Jackson serializes record component names as-is, camelCase, except where
 * the backend uses an explicit `@JsonProperty` — mirrored here with `@SerialName`).
 * Timestamps are ISO-8601 strings; UUIDs are strings; money is Double (display-only on
 * this client).
 */

// --- /auth (backend auth/dto) ---

@Serializable
data class OtpSendRequest(val phone: String)

@Serializable
data class OtpVerifyRequest(val phone: String, val idToken: String)

@Serializable
data class GoogleLoginRequest(val idToken: String)

@Serializable
data class AuthUserSummary(val id: String, val phone: String? = null, val email: String? = null)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: AuthUserSummary,
)

@Serializable
data class TokenRefreshRequest(val refreshToken: String)

@Serializable
data class TokenRefreshResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long)

@Serializable
data class LogoutRequest(val refreshToken: String)

// --- /users/me (backend user/dto) ---

@Serializable
data class OnboardingRequest(
    val consentText: String,
    val appVersion: String? = null,
    val selectedApps: List<String>? = null,
    val selectedBanks: List<String>? = null,
    val monthlySpendEstimate: Double? = null,
)

@Serializable
data class OnboardingUserIdentity(val id: String, val phone: String? = null)

@Serializable
data class OnboardingResponse(val deviceApiKey: String, val user: OnboardingUserIdentity)

@Serializable
data class UserProfileResponse(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class UserPreferences(
    val alertChannels: Map<String, Boolean>? = null,
    val selectedApps: List<String>? = null,
    val selectedBanks: List<String>? = null,
    val monthlySpendEstimate: Double? = null,
)

// --- /transactions, /categories, /emis (backend transaction/dto) ---

@Serializable
data class TransactionResponse(
    val id: String,
    val transactionDate: String,
    val debit: Double? = null,
    val credit: Double? = null,
    val amount: Double,
    val balance: Double? = null,
    val transactionMode: String? = null,
    val drCrIndicator: String? = null,
    val transactionId: String? = null,
    val recipientName: String? = null,
    val bank: String? = null,
    val upiId: String? = null,
    val note: String? = null,
    val source: String? = null,
    val parsedAt: String? = null,
    val categoryId: Int? = null,
    val confidenceScore: Float? = null,
    val assignedBy: String? = null,
)

@Serializable
data class TransactionListResponse(
    val data: List<TransactionResponse>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)

@Serializable
data class CorrectCategoryRequest(@SerialName("category_id") val categoryId: Int)

@Serializable
data class CategoryCorrectionResponse(val transactionId: String, val categoryId: Int)

@Serializable
data class Category(val id: Int, val name: String, val icon: String? = null)

@Serializable
data class EmiResponse(
    val id: String,
    val label: String,
    val amount: Double,
    val dueDay: Int? = null,
    val detectedFromSms: Boolean,
    val isActive: Boolean,
    val sourceTransactionId: String? = null,
)

@Serializable
data class UpdateEmiRequest(val label: String, val amount: Double, val dueDay: Int? = null)

// --- /budgets (backend budget/dto) ---

@Serializable
data class CreateBudgetRequest(val categoryId: Int, val monthlyLimit: Double)

@Serializable
data class BudgetResponse(
    val id: String,
    val categoryId: Int,
    val monthlyLimit: Double,
    val month: Int,
    val year: Int,
)

@Serializable
data class BudgetProgressResponse(
    val categoryId: Int,
    val monthlyLimit: Double,
    val spent: Double,
    val percentSpent: Double,
)

@Serializable
data class BudgetSuggestionResponse(
    val categoryId: Int,
    val suggestedMonthlyLimit: Double? = null,
    val available: Boolean,
)

// --- /alerts (backend alerts/dto) ---

@Serializable
data class AlertResponse(
    val id: String,
    val type: String,
    val priority: String,
    val triggeredAt: String,
    val deliveredAt: String? = null,
    val isRead: Boolean,
    val payload: JsonObject? = null,
)

@Serializable
data class AlertListResponse(
    val data: List<AlertResponse>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)

// --- /recommendations (backend recommendations/dto) ---

@Serializable
data class RecommendationResponse(
    val id: String,
    val categoryId: Int? = null,
    val text: String,
    val priority: String? = null,
    val generatedAt: String? = null,
)

// --- /chatbot (backend chatbot/dto) ---

@Serializable
data class ChatbotSessionResponse(val id: String, val createdAt: String, val lastActiveAt: String)

@Serializable
data class ChatbotMessageResponse(
    val id: String,
    val role: String,
    val message: String,
    val createdAt: String,
)

@Serializable
data class ChatbotSessionHistoryResponse(
    val id: String,
    val createdAt: String,
    val lastActiveAt: String,
    val messages: List<ChatbotMessageResponse>,
)

@Serializable
data class ChatbotMessageRequest(val sessionId: String, val message: String)

// --- /analytics (backend analytics/dto) ---

@Serializable
data class TrendBucketResponse(val bucketStart: String, val totalSpend: Double)

@Serializable
data class AnalyticsTrendsResponse(val granularity: String, val buckets: List<TrendBucketResponse>)

// --- Error shape (docs/development_guidelines.md API rules) ---

@Serializable
data class ApiErrorResponse(val error: String? = null, val message: String? = null, val status: Int? = null)
