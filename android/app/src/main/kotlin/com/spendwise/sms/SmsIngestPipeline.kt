package com.spendwise.sms

import android.content.Context
import com.spendwise.parser.DedupChecker
import com.spendwise.parser.FinancialKeywordDetector
import com.spendwise.parser.TransactionParserRouter
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.storage.QueuedTransactionDao
import com.spendwise.storage.QueuedTransactionEntity
import com.spendwise.storage.SpendWiseDatabase
import com.spendwise.storage.UserSessionProvider
import java.time.Instant

/**
 * Orchestrates the on-device pipeline shared by real-time capture (`SmsReceiver`) and the
 * first-launch inbox backfill (`SmsInboxBackfill`): financial filter → sender-routed parser →
 * on-device dedup → Room queue (docs/architecture.md SMS Ingestion Flow).
 */
class SmsIngestPipeline(
    private val queuedTransactionDao: QueuedTransactionDao,
    private val sessionStore: UserSessionProvider,
) {

    /** Returns `true` if a new row was enqueued, `false` if the SMS was skipped (non-financial or a duplicate). */
    suspend fun process(sender: String, body: String, receivedAt: Instant): Boolean {
        if (!FinancialKeywordDetector.isFinancial(body, sender)) return false

        val userId = sessionStore.getUserId() ?: return false
        val candidate = TransactionParserRouter.parse(body, sender, userId, receivedAt)

        val existingIds = queuedTransactionDao.getAllTransactionIds().toSet()
        val resolved = DedupChecker.check(candidate, existingIds) ?: return false

        val transactionId = resolved.transactionId ?: return false
        val amount = resolved.amount ?: return false
        val date = resolved.transactionDate ?: return false
        val drCr = resolved.drCrIndicator ?: return false

        queuedTransactionDao.insert(
            QueuedTransactionEntity(
                transactionId = transactionId,
                transactionDate = date.toString(),
                debit = resolved.debit ?: 0.0,
                credit = resolved.credit ?: 0.0,
                amount = amount,
                drCrIndicator = drCr,
                recipientName = resolved.recipientName,
                upiId = resolved.upiId,
                bank = resolved.bank,
                transactionMode = resolved.transactionMode,
                note = resolved.note,
                source = resolved.source,
            ),
        )
        return true
    }

    companion object {
        fun create(context: Context): SmsIngestPipeline {
            val database = SpendWiseDatabase.getInstance(context)
            return SmsIngestPipeline(database.queuedTransactionDao(), DeviceSessionStore(context))
        }
    }
}
