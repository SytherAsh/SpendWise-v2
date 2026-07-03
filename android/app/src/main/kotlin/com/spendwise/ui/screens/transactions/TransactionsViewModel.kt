package com.spendwise.ui.screens.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.ui.api.Category
import com.spendwise.ui.api.CorrectCategoryRequest
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.TransactionResponse
import com.spendwise.ui.data.CategoriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * E9-S2-T2 — cursor-paginated transaction list (`GET /transactions`, 50/page server
 * default) with date-range/category filters and in-list category correction.
 *
 * Cursor rules (unit-tested per the task's Required Tests): the next page request carries
 * `nextCursor` from the previous response; a filter change resets the cursor and the
 * accumulated list; `hasMore=false` stops further loads; a stale response for a superseded
 * filter generation is dropped.
 */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val categoriesRepository: CategoriesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class Filters(val categoryId: Int? = null, val fromIso: String? = null, val toIso: String? = null)

    data class UiState(
        val transactions: List<TransactionResponse> = emptyList(),
        val categories: List<Category> = emptyList(),
        val filters: Filters = Filters(),
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(
        // Dashboard drilldown arrives as ?category=<id> (E9-S2-T1 DoD).
        UiState(filters = Filters(categoryId = savedStateHandle.get<String?>("categoryId")?.toIntOrNull())),
    )
    val state: StateFlow<UiState> = _state

    private var nextCursor: String? = null

    /** Incremented on every filter reset so in-flight stale page loads get dropped. */
    private var generation = 0

    init {
        refresh()
        viewModelScope.launch {
            runCatching { categoriesRepository.all() }
                .onSuccess { cats -> _state.value = _state.value.copy(categories = cats) }
        }
    }

    fun setFilters(filters: Filters) {
        _state.value = _state.value.copy(filters = filters)
        refresh()
    }

    fun refresh() {
        generation++
        val gen = generation
        nextCursor = null
        _state.value = _state.value.copy(transactions = emptyList(), isLoading = true, error = null, hasMore = false)
        loadPage(gen)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.value = s.copy(isLoadingMore = true)
        loadPage(generation)
    }

    private fun loadPage(gen: Int) {
        val filters = _state.value.filters
        val cursor = nextCursor
        viewModelScope.launch {
            try {
                val page = api.listTransactions(
                    cursor = cursor,
                    category = filters.categoryId,
                    from = filters.fromIso,
                    to = filters.toIso,
                )
                if (gen != generation) return@launch // superseded by a filter change
                nextCursor = page.nextCursor
                _state.value = _state.value.copy(
                    transactions = _state.value.transactions + page.data,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = page.hasMore && page.nextCursor != null,
                )
            } catch (e: Exception) {
                if (gen != generation) return@launch
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = e.message ?: "Couldn't load transactions",
                )
            }
        }
    }

    /** DoD: the correction reflects immediately in the list (optimistic local update). */
    fun correctCategory(transactionId: String, categoryId: Int) {
        viewModelScope.launch {
            try {
                api.correctCategory(transactionId, CorrectCategoryRequest(categoryId))
                _state.value = _state.value.copy(
                    transactions = _state.value.transactions.map {
                        if (it.id == transactionId) it.copy(categoryId = categoryId, assignedBy = "user") else it
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Couldn't change category")
            }
        }
    }
}
