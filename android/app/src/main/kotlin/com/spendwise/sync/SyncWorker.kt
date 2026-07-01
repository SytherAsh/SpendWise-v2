package com.spendwise.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.storage.SpendWiseDatabase
import java.io.IOException

/**
 * WorkManager entry point for the ~15–30 min periodic sync (docs/requirements.md). Delegates
 * all retry/backoff-relevant business logic to `BatchSyncEngine`; only asks WorkManager for
 * its own built-in exponential-backoff retry when the whole request never got a response —
 * per-item failures within a successful response don't need a full worker retry since the
 * next periodic run already re-attempts whatever is still unsynced.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = SpendWiseDatabase.getInstance(applicationContext)
        val session = DeviceSessionStore(applicationContext)
        val engine = BatchSyncEngine(
            dao = database.queuedTransactionDao(),
            apiClient = HttpIngestApiClient(baseUrl = SyncConfig.DEFAULT_API_BASE_URL),
            sessionProvider = {
                val jwt = session.getUserJwt()
                val deviceKey = session.getDeviceApiKey()
                if (jwt != null && deviceKey != null) jwt to deviceKey else null
            },
        )
        return try {
            engine.syncOnce()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        }
    }
}
