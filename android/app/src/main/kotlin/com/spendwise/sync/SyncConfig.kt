package com.spendwise.sync

/**
 * Placeholder for the backend base URL until an env-specific build config wiring lands
 * (see `android/local.properties.example` `API_BASE_URL` — deployment epic's concern, not
 * Epic 2's parser/sync pipeline).
 */
object SyncConfig {
    const val DEFAULT_API_BASE_URL = "http://10.0.2.2:8080/api/v1"
}
