package com.spendwise.ui.data

import com.spendwise.ui.api.Category
import com.spendwise.ui.api.SpendWiseApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-lifetime cache of `GET /categories` — the fixed category list backs pickers and
 * id→name lookups on most screens, so it's fetched once and shared.
 */
@Singleton
class CategoriesRepository @Inject constructor(private val api: SpendWiseApi) {

    private val mutex = Mutex()
    private var cached: List<Category>? = null

    suspend fun all(): List<Category> = mutex.withLock {
        cached ?: api.listCategories().also { cached = it }
    }

    suspend fun nameOf(categoryId: Int?): String =
        categoryId?.let { id -> all().firstOrNull { it.id == id }?.name } ?: "Uncategorized"
}
