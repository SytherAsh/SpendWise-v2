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

/** E9-S2-T2 detail view — `GET /transactions/:id` + the "Change Category" action. */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val api: SpendWiseApi,
    private val categoriesRepository: CategoriesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val transaction: TransactionResponse? = null,
        val categories: List<Category> = emptyList(),
        val isLoading: Boolean = true,
        val isSavingCategory: Boolean = false,
        val error: String? = null,
    )

    private val transactionId: String = checkNotNull(savedStateHandle["transactionId"])

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            try {
                val txn = api.getTransaction(transactionId)
                val cats = runCatching { categoriesRepository.all() }.getOrDefault(emptyList())
                _state.value = UiState(transaction = txn, categories = cats, isLoading = false)
            } catch (e: Exception) {
                _state.value = UiState(isLoading = false, error = e.message ?: "Couldn't load transaction")
            }
        }
    }

    fun correctCategory(categoryId: Int) {
        val current = _state.value.transaction ?: return
        _state.value = _state.value.copy(isSavingCategory = true, error = null)
        viewModelScope.launch {
            try {
                api.correctCategory(current.id, CorrectCategoryRequest(categoryId))
                _state.value = _state.value.copy(
                    transaction = current.copy(categoryId = categoryId, assignedBy = "user"),
                    isSavingCategory = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSavingCategory = false,
                    error = e.message ?: "Couldn't change category",
                )
            }
        }
    }
}
