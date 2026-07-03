package com.spendwise.ui.screens.transactions

import androidx.lifecycle.SavedStateHandle
import com.spendwise.ui.FakeSpendWiseApi
import com.spendwise.ui.api.Category
import com.spendwise.ui.api.TransactionListResponse
import com.spendwise.ui.api.TransactionResponse
import com.spendwise.ui.data.CategoriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E9-S2-T2 Required Test — the pagination cursor-handling logic in the view model:
 * cursor threading across pages, hasMore termination, filter-change reset, and
 * stale-response dropping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private open class PagingFakeApi : FakeSpendWiseApi() {
        val requestedCursors = mutableListOf<String?>()
        val requestedCategories = mutableListOf<Int?>()
        var pages = mutableMapOf<String?, TransactionListResponse>()

        override suspend fun listCategories(): List<Category> = emptyList()

        override suspend fun listTransactions(
            limit: Int?,
            cursor: String?,
            category: Int?,
            from: String?,
            to: String?,
        ): TransactionListResponse {
            requestedCursors += cursor
            requestedCategories += category
            return pages[cursor] ?: TransactionListResponse(emptyList(), null, false)
        }
    }

    private lateinit var api: PagingFakeApi

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = PagingFakeApi()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun txn(id: String) = TransactionResponse(id = id, transactionDate = "2026-07-01T00:00:00Z", amount = -100.0)

    private fun viewModel(categoryArg: String? = null) = TransactionsViewModel(
        api,
        CategoriesRepository(api),
        SavedStateHandle(mapOf("categoryId" to categoryArg)),
    )

    @Test
    fun `first load requests a null cursor and stores the returned one`() {
        api.pages[null] = TransactionListResponse(listOf(txn("a"), txn("b")), nextCursor = "cur-1", hasMore = true)

        val vm = viewModel()

        assertEquals(listOf<String?>(null), api.requestedCursors)
        assertEquals(listOf("a", "b"), vm.state.value.transactions.map { it.id })
        assertTrue(vm.state.value.hasMore)
    }

    @Test
    fun `loadMore carries the previous response's cursor and appends`() {
        api.pages[null] = TransactionListResponse(listOf(txn("a")), nextCursor = "cur-1", hasMore = true)
        api.pages["cur-1"] = TransactionListResponse(listOf(txn("b")), nextCursor = "cur-2", hasMore = true)

        val vm = viewModel()
        vm.loadMore()

        assertEquals(listOf(null, "cur-1"), api.requestedCursors)
        assertEquals(listOf("a", "b"), vm.state.value.transactions.map { it.id })
    }

    @Test
    fun `hasMore false stops further loads`() {
        api.pages[null] = TransactionListResponse(listOf(txn("a")), nextCursor = null, hasMore = false)

        val vm = viewModel()
        vm.loadMore()
        vm.loadMore()

        assertEquals(listOf<String?>(null), api.requestedCursors) // no extra requests
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun `a null nextCursor with hasMore true still stops paging`() {
        // Defensive: never re-request the first page in a loop on inconsistent server output.
        api.pages[null] = TransactionListResponse(listOf(txn("a")), nextCursor = null, hasMore = true)

        val vm = viewModel()
        vm.loadMore()

        assertEquals(listOf<String?>(null), api.requestedCursors)
    }

    @Test
    fun `filter change resets the cursor and the accumulated list`() {
        api.pages[null] = TransactionListResponse(listOf(txn("a")), nextCursor = "cur-1", hasMore = true)
        api.pages["cur-1"] = TransactionListResponse(listOf(txn("b")), nextCursor = null, hasMore = false)

        val vm = viewModel()
        vm.loadMore()
        assertEquals(2, vm.state.value.transactions.size)

        vm.setFilters(TransactionsViewModel.Filters(categoryId = 7))

        // The filtered reload starts from a null cursor again, with the category applied.
        assertEquals(listOf(null, "cur-1", null), api.requestedCursors)
        assertEquals(7, api.requestedCategories.last())
        assertEquals(listOf("a"), vm.state.value.transactions.map { it.id }) // only the fresh page
    }

    @Test
    fun `a stale in-flight response from a superseded filter generation is dropped`() {
        // A gated fake lets the first (pre-filter) request stay in flight while the filter
        // changes, then resolve late — the ViewModel must discard it, not append it.
        val gates = ArrayDeque<kotlinx.coroutines.CompletableDeferred<TransactionListResponse>>()
        val gatedCursors = mutableListOf<String?>()
        val gatedApi = object : FakeSpendWiseApi() {
            override suspend fun listCategories(): List<Category> = emptyList()

            override suspend fun listTransactions(
                limit: Int?,
                cursor: String?,
                category: Int?,
                from: String?,
                to: String?,
            ): TransactionListResponse {
                gatedCursors += cursor
                val gate = kotlinx.coroutines.CompletableDeferred<TransactionListResponse>()
                gates.addLast(gate)
                return gate.await()
            }
        }

        val vm = TransactionsViewModel(gatedApi, CategoriesRepository(gatedApi), SavedStateHandle())
        val firstLoadGate = gates.removeFirst() // gen 1's request, still in flight

        vm.setFilters(TransactionsViewModel.Filters(categoryId = 7)) // supersedes gen 1
        val filteredLoadGate = gates.removeFirst() // gen 2's request

        // The old generation's response lands late, carrying a poisoned cursor.
        firstLoadGate.complete(
            TransactionListResponse(listOf(txn("stale")), nextCursor = "stale-cursor", hasMore = true),
        )
        // Dropped: no data, no hasMore, no cursor from the stale response.
        assertTrue(vm.state.value.transactions.isEmpty())
        assertFalse(vm.state.value.hasMore)

        filteredLoadGate.complete(
            TransactionListResponse(listOf(txn("fresh")), nextCursor = "fresh-cursor", hasMore = true),
        )
        assertEquals(listOf("fresh"), vm.state.value.transactions.map { it.id })

        // And paging continues from the fresh cursor, never the stale one.
        vm.loadMore()
        gates.removeFirst().complete(TransactionListResponse(emptyList(), null, false))
        assertEquals(listOf(null, null, "fresh-cursor"), gatedCursors)
    }

    @Test
    fun `dashboard drilldown category arg seeds the initial filter`() {
        api.pages[null] = TransactionListResponse(emptyList(), null, false)

        val vm = viewModel(categoryArg = "5")

        assertEquals(5, api.requestedCategories.single())
        assertEquals(5, vm.state.value.filters.categoryId)
    }

    @Test
    fun `category correction updates the matching row in place`() {
        api.pages[null] = TransactionListResponse(listOf(txn("a"), txn("b")), nextCursor = null, hasMore = false)
        val correctedApi = object : PagingFakeApi() {
            override suspend fun correctCategory(
                id: String,
                body: com.spendwise.ui.api.CorrectCategoryRequest,
            ) = com.spendwise.ui.api.CategoryCorrectionResponse(id, body.categoryId)
        }
        correctedApi.pages[null] = api.pages[null]!!
        val vm = TransactionsViewModel(correctedApi, CategoriesRepository(correctedApi), SavedStateHandle())

        vm.correctCategory("a", 3)

        val corrected = vm.state.value.transactions.first { it.id == "a" }
        assertEquals(3, corrected.categoryId)
        assertEquals("user", corrected.assignedBy)
        assertNull(vm.state.value.transactions.first { it.id == "b" }.categoryId)
    }
}
