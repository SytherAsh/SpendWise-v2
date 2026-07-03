package com.spendwise.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.ui.api.TransactionResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault())

/** E9-S2-T2 — paginated, filterable transaction list; taps open the detail view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onTransactionClick: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Infinite scroll: request the next page when the last visible item nears the end.
    val shouldLoadMore by remember {
        derivedStateOf { listState.isNearEnd() && state.hasMore && !state.isLoadingMore && !state.isLoading }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
        // Category filter chips ("All" + the fixed category list).
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.filters.categoryId == null,
                    onClick = { viewModel.setFilters(state.filters.copy(categoryId = null)) },
                    label = { Text("All") },
                )
            }
            items(state.categories) { category ->
                FilterChip(
                    selected = state.filters.categoryId == category.id,
                    onClick = { viewModel.setFilters(state.filters.copy(categoryId = category.id)) },
                    label = { Text(category.name) },
                )
            }
        }

        when {
            state.isLoading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.error != null && state.transactions.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }

            state.transactions.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Transactions appear here once SMS sync catches them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.transactions, key = { it.id }) { txn ->
                    TransactionRow(
                        txn = txn,
                        categoryName = state.categories.firstOrNull { it.id == txn.categoryId }?.name,
                        onClick = { onTransactionClick(txn.id) },
                    )
                    HorizontalDivider()
                }
                if (state.isLoadingMore) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(txn: TransactionResponse, categoryName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                txn.recipientName ?: txn.upiId ?: txn.note ?: "Transaction",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                listOfNotNull(formatDate(txn.transactionDate), categoryName ?: "Uncategorized")
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatAmount(txn.amount),
            style = MaterialTheme.typography.bodyLarge,
            color = if (txn.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun LazyListState.isNearEnd(): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= layoutInfo.totalItemsCount - 5
}

internal fun formatDate(iso: String): String =
    runCatching { DATE_FORMAT.format(Instant.parse(iso)) }.getOrDefault(iso)

internal fun formatAmount(amount: Double): String {
    val abs = if (amount < 0) -amount else amount
    val sign = if (amount < 0) "-" else "+"
    return "$sign₹%,.2f".format(abs)
}
