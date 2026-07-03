package com.spendwise.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** E9-S1-T5 — brief progress indicator over the historical inbox scan, then the dashboard. */
@Composable
fun BackfillScreen(
    onBackfillComplete: () -> Unit,
    viewModel: BackfillViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.runBackfill() }
    LaunchedEffect(state) {
        if (state is BackfillViewModel.UiState.Complete) onBackfillComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            is BackfillViewModel.UiState.Running, BackfillViewModel.UiState.Complete -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
                Text("Scanning your SMS inbox…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finding your past transactions. This happens on your phone — " +
                        "raw messages never leave the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is BackfillViewModel.UiState.Failed -> {
                Text("Backfill didn't finish", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { viewModel.runBackfill() }) { Text("Try again") }
            }
        }
    }
}
