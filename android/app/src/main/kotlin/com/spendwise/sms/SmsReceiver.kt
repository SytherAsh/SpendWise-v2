package com.spendwise.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Real-time SMS capture (docs/architecture.md SMS Ingestion Flow). Delegates all parsing/
 * dedup/storage work to `SmsIngestPipeline` — this class only extracts sender/body/timestamp
 * from the incoming broadcast and keeps the receiver alive across the async pipeline call via
 * `goAsync()` (a `BroadcastReceiver.onReceive()` normally returns before a coroutine launched
 * from it would finish, and the OS is free to recycle the receiver once `onReceive` returns).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pipeline = SmsIngestPipeline.create(appContext)
                messages.forEach { message ->
                    val sender = message.originatingAddress ?: return@forEach
                    val body = message.messageBody ?: return@forEach
                    val receivedAt = Instant.ofEpochMilli(message.timestampMillis)
                    pipeline.process(sender, body, receivedAt)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
