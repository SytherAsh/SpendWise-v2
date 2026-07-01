package com.spendwise.sms

import android.content.Context
import android.provider.Telephony
import com.spendwise.sync.SyncScheduler
import java.time.Instant

/**
 * One-time bulk read of the existing SMS inbox at the end of onboarding
 * (docs/architecture.md "First-Launch SMS Inbox Backfill"), distinct from the ongoing
 * foreground service which only captures new incoming messages. Feeds the same filter →
 * parser → dedup → Room queue pipeline as real-time capture, then triggers exactly one
 * immediate sync on completion. `syncTrigger` is injected so tests can assert it fired
 * exactly once without depending on WorkManager.
 */
class SmsInboxBackfill(
    private val pipeline: SmsIngestPipeline,
    private val syncTrigger: () -> Unit,
) {

    suspend fun run(context: Context) {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            null,
        ) ?: return

        cursor.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            if (addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) return

            while (it.moveToNext()) {
                val sender = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val receivedAt = Instant.ofEpochMilli(it.getLong(dateIndex))
                pipeline.process(sender, body, receivedAt)
            }
        }

        syncTrigger()
    }

    companion object {
        fun create(context: Context): SmsInboxBackfill {
            val appContext = context.applicationContext
            return SmsInboxBackfill(
                pipeline = SmsIngestPipeline.create(appContext),
                syncTrigger = { SyncScheduler.triggerImmediateSync(appContext) },
            )
        }
    }
}
