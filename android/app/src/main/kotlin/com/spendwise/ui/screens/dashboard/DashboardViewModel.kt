package com.spendwise.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.ui.api.AlertResponse
import com.spendwise.ui.api.BudgetProgressResponse
import com.spendwise.ui.api.Category
import com.spendwise.ui.api.RecommendationResponse
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.TrendBucketResponse
import com.spendwise.ui.data.CategoriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * E9-S2-T1 — composes the four dashboard sections (`/alerts`, `/recommendations`,
 * `/budgets/progress`, `/analytics/trends`). Sections load in parallel and fail
 * independently: one endpoint erroring shows that section's error, not a blank dashboard.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val categoriesRepository: CategoriesRepository,
) : ViewModel() {

    data class Section<T>(val data: T? = null, val isLoading: Boolean = true, val error: String? = null)

    data class UiState(
        val alerts: Section<List<AlertResponse>> = Section(),
        val recommendations: Section<List<RecommendationResponse>> = Section(),
        val budgetProgress: Section<List<BudgetProgressResponse>> = Section(),
        val trend: Section<List<TrendBucketResponse>> = Section(),
        val categories: List<Category> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState(categories = _state.value.categories)

        viewModelScope.launch {
            runCatching { categoriesRepository.all() }
                .onSuccess { _state.value = _state.value.copy(categories = it) }
        }
        viewModelScope.launch {
            // Unread alerts only — the dashboard alert panel is the "needs attention" surface.
            val section = load { api.listAlerts(isRead = false).data }
            _state.value = _state.value.copy(alerts = section)
        }
        viewModelScope.launch {
            val section = load { api.listRecommendations() }
            _state.value = _state.value.copy(recommendations = section)
        }
        viewModelScope.launch {
            val section = load { api.budgetProgress() }
            _state.value = _state.value.copy(budgetProgress = section)
        }
        viewModelScope.launch {
            // Daily spend for the trailing 30 days.
            val today = LocalDate.now(ZoneOffset.UTC)
            val section = load {
                api.trends(
                    granularity = "day",
                    from = today.minusDays(30).toString(),
                    to = today.toString(),
                ).buckets
            }
            _state.value = _state.value.copy(trend = section)
        }
    }

    /** docs/user_flows.md "Handling an Alert": dismissing marks it read and drops it. */
    fun dismissAlert(alertId: String) {
        viewModelScope.launch {
            runCatching { api.markAlertRead(alertId) }.onSuccess {
                _state.value = _state.value.copy(
                    alerts = _state.value.alerts.copy(
                        data = _state.value.alerts.data?.filterNot { it.id == alertId },
                    ),
                )
            }
        }
    }

    /**
     * docs/user_flows.md "Recurring Payment Detection": confirming a recurring_payment alert
     * creates the linked EMI server-side (E6-S2-T2) and marks the alert read.
     */
    fun confirmRecurringPayment(alertId: String) {
        viewModelScope.launch {
            runCatching { api.confirmRecurringPayment(alertId) }.onSuccess {
                _state.value = _state.value.copy(
                    alerts = _state.value.alerts.copy(
                        data = _state.value.alerts.data?.filterNot { it.id == alertId },
                    ),
                )
            }
        }
    }

    /** docs/user_flows.md "Viewing Savings Recommendations": dismiss removes the card. */
    fun dismissRecommendation(recommendationId: String) {
        viewModelScope.launch {
            runCatching { api.dismissRecommendation(recommendationId) }.onSuccess {
                _state.value = _state.value.copy(
                    recommendations = _state.value.recommendations.copy(
                        data = _state.value.recommendations.data?.filterNot { it.id == recommendationId },
                    ),
                )
            }
        }
    }

    private inline fun <T> load(block: () -> T): Section<T> =
        try {
            Section(data = block(), isLoading = false)
        } catch (e: Exception) {
            Section(isLoading = false, error = e.message ?: "Couldn't load")
        }
}
