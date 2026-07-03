package com.spendwise.ui.screens.emis

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
import com.spendwise.ui.api.EmiResponse

/** E9-S2-T4 — EMI/subscription management (docs/user_flows.md "EMI / Subscriptions"). */
@Composable
fun EmisScreen(viewModel: EmisViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<EmiResponse?>(null) }

    when {
        state.isLoading -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        state.error != null && state.emis.isEmpty() -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }

        state.emis.isEmpty() -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No tracked subscriptions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Confirmed recurring payments and EMIs show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Subscriptions & EMIs",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.emis, key = { it.id }) { emi ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = emi }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(emi.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                emi.dueDay?.let { "Due day $it" },
                                if (emi.detectedFromSms) "Auto-detected" else "Manual",
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("₹%,.0f".format(emi.amount), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
        }
    }

    editing?.let { emi ->
        EmiEditDialog(
            emi = emi,
            isMutating = state.isMutating,
            onSave = { label, amount, dueDay ->
                viewModel.update(emi.id, label, amount, dueDay)
                editing = null
            },
            onDeactivate = {
                viewModel.deactivate(emi.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EmiEditDialog(
    emi: EmiResponse,
    isMutating: Boolean,
    onSave: (String, Double, Int?) -> Unit,
    onDeactivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(emi.label) }
    var amount by remember { mutableStateOf("%.0f".format(emi.amount)) }
    var dueDay by remember { mutableStateOf(emi.dueDay?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit subscription") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    prefix = { Text("₹ ") },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { v -> dueDay = v.filter(Char::isDigit).take(2) },
                    label = { Text("Due day of month (1-31, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDeactivate, enabled = !isMutating) {
                    Text("No longer subscribed", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isMutating && label.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = {
                    onSave(label.trim(), amount.toDouble(), dueDay.toIntOrNull()?.takeIf { it in 1..31 })
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
