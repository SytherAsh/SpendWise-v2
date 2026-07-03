package com.spendwise.ui.screens.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * E9-S1-T3 — SMS + notification permission flow (docs/user_flows.md Onboarding steps 4-5).
 * SMS denial is a hard block with a deep link into Android settings ("SMS Permission
 * Denied" edge case); notification denial proceeds with a note (alerts become in-app only).
 */
@Composable
fun PermissionsScreen(onPermissionsResolved: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun smsGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    var smsState by remember { mutableStateOf(if (smsGranted()) SmsPermission.GRANTED else SmsPermission.NOT_ASKED) }
    var notificationsAsked by remember { mutableStateOf(Build.VERSION.SDK_INT < 33) }
    var notificationsDenied by remember { mutableStateOf(false) }

    // Re-check on resume: the user may have granted SMS from the settings deep link.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && smsGranted()) smsState = SmsPermission.GRANTED
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        smsState = if (results[Manifest.permission.READ_SMS] == true) SmsPermission.GRANTED else SmsPermission.DENIED
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAsked = true
        notificationsDenied = !granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            smsState == SmsPermission.DENIED -> {
                // Blocking screen — cannot be bypassed (E9-S1-T3 DoD).
                Text("SMS access is required", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Reading transaction SMS is SpendWise's core feature — the app cannot " +
                        "work without it. Grant SMS access in Android settings to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }) { Text("Open Android settings") }
            }

            smsState == SmsPermission.NOT_ASKED -> {
                Text("Let SpendWise read transaction SMS", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "SpendWise filters and parses bank/UPI SMS on your phone. " +
                        "Raw messages never leave the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grant SMS access") }
            }

            !notificationsAsked -> {
                Text("Turn on alert notifications?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Budget and spending alerts arrive as push notifications. If you skip " +
                        "this, alerts will only be visible inside the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsAsked = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow notifications") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        notificationsAsked = true
                        notificationsDenied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Not now") }
            }

            else -> {
                Text("You're all set", style = MaterialTheme.typography.titleLarge)
                if (notificationsDenied) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Notifications are off — alerts will appear in-app only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onPermissionsResolved, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
        }
    }
}

private enum class SmsPermission { NOT_ASKED, GRANTED, DENIED }
