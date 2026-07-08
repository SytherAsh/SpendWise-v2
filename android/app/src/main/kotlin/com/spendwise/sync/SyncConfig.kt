package com.spendwise.sync

/**
 * Placeholder for the backend base URL until an env-specific build config wiring lands
 * (see `android/local.properties.example` `API_BASE_URL` — deployment epic's concern, not
 * Epic 2's parser/sync pipeline).
 */
object SyncConfig {
    // Local acceptance testing on a physical device over USB: `adb reverse tcp:8080 tcp:8080`
    // tunnels the device's 127.0.0.1:8080 to the host's backend. (Emulator value was
    // http://10.0.2.2:8080 — the emulator-only host-loopback alias.) Still a placeholder
    // pending real env-specific build-config wiring — do not ship as-is.
    const val DEFAULT_API_BASE_URL = "http://127.0.0.1:8080/api/v1"
}
