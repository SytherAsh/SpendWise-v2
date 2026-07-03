package com.spendwise.ui.screens.budget

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * E9-S2-T3 — per-category budgets with progress bars. Tapping a category opens the edit
 * dialog; a suggestion (when available) pre-fills it (DoD).
 */
@Composable
fun BudgetScreen(viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<BudgetViewModel.CategoryBudget?>(null) }

    when {
        state.isLoading -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        state.error != null && state.rows.isEmpty() -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "This month's budgets",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.rows, key = { it.category.id }) { row ->
                BudgetRow(row, onClick = { editing = row })
                HorizontalDivider()
            }
        }
    }

    editing?.let { row ->
        BudgetEditDialog(
            row = row,
            isSaving = state.isSaving,
            onSave = { limit ->
                viewModel.saveBudget(row.category.id, limit)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun BudgetRow(row: BudgetViewModel.CategoryBudget, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val progress = row.progress
            if (progress != null) {
                Text(
                    "₹%,.0f / ₹%,.0f".format(progress.spent, progress.monthlyLimit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (row.suggestedLimit != null) "Suggested ₹%,.0f".format(row.suggestedLimit) else "No budget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        row.progress?.let { progress ->
            Spacer(Modifier.height(8.dp))
            val fraction = (progress.percentSpent / 100.0).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    progress.percentSpent >= 100 -> MaterialTheme.colorScheme.error
                    progress.percentSpent >= 80 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun BudgetEditDialog(
    row: BudgetViewModel.CategoryBudget,
    isSaving: Boolean,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Suggestion pre-fills the form (E9-S2-T3 DoD); an existing budget takes precedence.
    val initial = row.progress?.monthlyLimit ?: row.suggestedLimit
    var limitText by remember { mutableStateOf(initial?.let { "%.0f".format(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for ${row.category.name}") },
        text = {
            Column {
                if (row.suggestedLimit != null && row.progress == null) {
                    Text(
                        "Suggested from your recent spending: ₹%,.0f".format(row.suggestedLimit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit) },
                    prefix = { Text("₹ ") },
                    label = { Text("Monthly limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && (limitText.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = { limitText.toDoubleOrNull()?.let(onSave) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
