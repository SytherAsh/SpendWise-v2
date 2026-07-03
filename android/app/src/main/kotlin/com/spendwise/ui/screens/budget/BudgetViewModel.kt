package com.spendwise.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.ui.api.BudgetProgressResponse
import com.spendwise.ui.api.Category
import com.spendwise.ui.api.CreateBudgetRequest
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.data.CategoriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S2-T3 — current-month budgets with progress bars, edit via `POST /budgets` (idempotent
 * upsert), and suggestion pre-fill from `GET /budgets/suggestions` (available only where
 * history exists — E5's per-category `available` flag).
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val categoriesRepository: CategoriesRepository,
) : ViewModel() {

    data class CategoryBudget(
        val category: Category,
        val progress: BudgetProgressResponse?,
        val suggestedLimit: Double?,
    )

    data class UiState(
        val rows: List<CategoryBudget> = emptyList(),
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                coroutineScope {
                    val categories = async { categoriesRepository.all() }
                    val progress = async { api.budgetProgress() }
                    val suggestions = async { runCatching { api.budgetSuggestions() }.getOrDefault(emptyList()) }

                    val progressByCategory = progress.await().associateBy { it.categoryId }
                    val suggestionByCategory = suggestions.await()
                        .filter { it.available && it.suggestedMonthlyLimit != null }
                        .associateBy { it.categoryId }

                    _state.value = UiState(
                        rows = categories.await().map { category ->
                            CategoryBudget(
                                category = category,
                                progress = progressByCategory[category.id],
                                suggestedLimit = suggestionByCategory[category.id]?.suggestedMonthlyLimit,
                            )
                        },
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Couldn't load budgets")
            }
        }
    }

    /** DoD: saving persists and the progress bar updates immediately (refetch after upsert). */
    fun saveBudget(categoryId: Int, monthlyLimit: Double) {
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                api.upsertBudget(CreateBudgetRequest(categoryId, monthlyLimit))
                _state.value = _state.value.copy(isSaving = false)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.message ?: "Couldn't save budget")
            }
        }
    }
}
