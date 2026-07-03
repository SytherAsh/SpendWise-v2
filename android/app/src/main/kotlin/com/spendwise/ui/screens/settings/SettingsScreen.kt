package com.spendwise.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.ui.screens.onboarding.PrivacyPolicy
import java.io.File
import java.time.LocalDate

/** E9-S2-T6 — preferences, export, privacy policy (DPDP: reachable any time), logout. */
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var lastFormat by remember { mutableStateOf(SettingsViewModel.ExportFormat.PDF) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.exportReady.collect { file -> shareFile(context, file, lastFormat.mimeType) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Text("Alert channels", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Push notifications", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.pushEnabled,
                enabled = !state.isLoading,
                onCheckedChange = { viewModel.setAlertChannels(it, state.emailEnabled) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Email", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.emailEnabled,
                enabled = !state.isLoading,
                onCheckedChange = { viewModel.setAlertChannels(state.pushEnabled, it) },
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Text("Export report", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Download your transactions as a PDF report or raw CSV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showExportDialog = true }, enabled = !state.isExporting) {
            Text(if (state.isExporting) "Exporting…" else "Export…")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // docs/security.md: privacy policy accessible from settings at any time.
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PrivacyPolicy.URL)))
        }) { Text("Privacy policy") }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { viewModel.logout() }, modifier = Modifier.fillMaxWidth()) {
            Text("Log out", color = MaterialTheme.colorScheme.error)
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onExport = { format, from, to ->
                lastFormat = format
                showExportDialog = false
                viewModel.export(context, format, from, to)
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

/**
 * Date-range picker (defaults to the current month so far) + format choice, or the "full
 * financial year" shortcut (docs/user_flows.md "Exporting a Report").
 */
@Composable
private fun ExportDialog(
    onExport: (SettingsViewModel.ExportFormat, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var from by remember { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var to by remember { mutableStateOf(today.toString()) }
    var format by remember { mutableStateOf(SettingsViewModel.ExportFormat.PDF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export report") },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = format == SettingsViewModel.ExportFormat.PDF,
                        onClick = { format = SettingsViewModel.ExportFormat.PDF },
                        label = { Text("PDF") },
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = format == SettingsViewModel.ExportFormat.CSV,
                        onClick = { format = SettingsViewModel.ExportFormat.CSV },
                        label = { Text("CSV") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("From (YYYY-MM-DD)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To (YYYY-MM-DD)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    // Indian financial year containing today (April-March).
                    val fyStart = if (today.monthValue >= 4) today.year else today.year - 1
                    from = LocalDate.of(fyStart, 4, 1).toString()
                    to = LocalDate.of(fyStart + 1, 3, 31).toString()
                }) { Text("Use full financial year") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isIsoDate(from) && isIsoDate(to),
                onClick = { onExport(format, from, to) },
            ) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun isIsoDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share report"))
}
