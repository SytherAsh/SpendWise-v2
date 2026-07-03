package com.spendwise.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * E9-S1-T2 — the single mandatory DPDP consent gate (ADR-005). Renders [ConsentText]'s
 * constants directly so what the user sees is exactly the [ConsentText.FULL_TEXT] snapshot
 * the ViewModel persists.
 */
@Composable
fun ConsentScreen(
    onConsented: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is ConsentViewModel.UiState.Accepted) onConsented()
    }

    if (state is ConsentViewModel.UiState.Declined) {
        // ADR-005: declining blocks the flow entirely — no bypass, only reconsidering.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SpendWise can't work without consent", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "Reading transaction SMS, storing your data, and improving categorization " +
                    "are all core to the app. Without consent there is nothing SpendWise can do.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { viewModel.backToConsent() }) { Text("Review consent again") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(ConsentText.HEADING, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(ConsentText.INTRO, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        ConsentText.PURPOSES.forEach { purpose ->
            Row(Modifier.padding(vertical = 8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(purpose, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PrivacyPolicy.URL)))
        }) { Text("Read the privacy policy") }

        Spacer(Modifier.height(24.dp))

        if (state is ConsentViewModel.UiState.Submitting) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = { viewModel.accept(appVersion(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("I consent to all three") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.decline() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Decline") }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun appVersion(context: android.content.Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()
