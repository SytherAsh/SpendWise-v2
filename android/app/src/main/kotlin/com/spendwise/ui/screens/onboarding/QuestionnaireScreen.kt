package com.spendwise.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val PAYMENT_APPS = listOf("Paytm", "GPay", "PhonePe", "Other UPI app")
private val BANKS = listOf("SBI", "HDFC", "ICICI", "Axis", "Kotak", "Other")

/** E9-S1-T4 — apps/banks/spend questionnaire + optional bank-statement upload (steps 6-7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    onQuestionnaireDone: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is QuestionnaireViewModel.UiState.Done) onQuestionnaireDone()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadStatement(context, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        when (state) {
            QuestionnaireViewModel.UiState.Editing, QuestionnaireViewModel.UiState.Saving -> {
                val selectedApps = remember { mutableListOf<String>().toMutableStateList() }
                val selectedBanks = remember { mutableListOf<String>().toMutableStateList() }
                var estimate by remember { mutableStateOf("") }

                Text("Tell us about your spending", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                Text("Which payment apps do you use?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_APPS.forEach { app ->
                        FilterChip(
                            selected = app in selectedApps,
                            onClick = { if (app in selectedApps) selectedApps.remove(app) else selectedApps.add(app) },
                            label = { Text(app) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Which banks do you use?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BANKS.forEach { bank ->
                        FilterChip(
                            selected = bank in selectedBanks,
                            onClick = { if (bank in selectedBanks) selectedBanks.remove(bank) else selectedBanks.add(bank) },
                            label = { Text(bank) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Roughly how much do you spend per month?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = estimate,
                    onValueChange = { estimate = it.filter { c -> c.isDigit() } },
                    prefix = { Text("₹ ") },
                    label = { Text("Monthly estimate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))
                if (state is QuestionnaireViewModel.UiState.Saving) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Button(
                        onClick = {
                            viewModel.savePreferences(
                                selectedApps.toList(),
                                selectedBanks.toList(),
                                estimate.toDoubleOrNull(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue") }
                }
            }

            QuestionnaireViewModel.UiState.UploadOffer, QuestionnaireViewModel.UiState.Uploading -> {
                Text("Have a bank statement?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Upload a bank statement PDF to seed your history and get budget " +
                        "suggestions right away. It's parsed once and discarded — never stored. " +
                        "You can skip this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                if (state is QuestionnaireViewModel.UiState.Uploading) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Button(
                        onClick = { filePicker.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Upload statement PDF") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.finish() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Skip for now") }
                }
            }

            QuestionnaireViewModel.UiState.UploadSucceeded -> {
                Text("Statement uploaded", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your history has been seeded — budget suggestions will use it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { viewModel.finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }

            QuestionnaireViewModel.UiState.Done -> {}
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
