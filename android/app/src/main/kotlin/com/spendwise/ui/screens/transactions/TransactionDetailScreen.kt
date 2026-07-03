package com.spendwise.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** E9-S2-T2 — transaction detail with the "Change Category" flow (10-category picker). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.transaction == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text(state.error ?: "Transaction not found", color = MaterialTheme.colorScheme.error) }

            else -> {
                val txn = state.transaction!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        formatAmount(txn.amount),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (txn.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        txn.recipientName ?: txn.upiId ?: "Unknown recipient",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(24.dp))

                    DetailRow("Date", formatDate(txn.transactionDate))
                    DetailRow("Category", state.categories.firstOrNull { it.id == txn.categoryId }?.name ?: "Uncategorized")
                    txn.upiId?.let { DetailRow("UPI ID", it) }
                    txn.bank?.let { DetailRow("Bank", it) }
                    txn.transactionMode?.let { DetailRow("Mode", it) }
                    txn.source?.let { DetailRow("Source", it) }
                    txn.note?.let { DetailRow("Note", it) }

                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { showCategoryPicker = true },
                        enabled = !state.isSavingCategory && state.categories.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.isSavingCategory) "Saving…" else "Change Category") }

                    state.error?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (showCategoryPicker) {
                    AlertDialog(
                        onDismissRequest = { showCategoryPicker = false },
                        title = { Text("Choose a category") },
                        text = {
                            Column {
                                state.categories.forEach { category ->
                                    TextButton(
                                        onClick = {
                                            showCategoryPicker = false
                                            viewModel.correctCategory(category.id)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(Modifier.fillMaxWidth()) { Text(category.name) }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showCategoryPicker = false }) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
