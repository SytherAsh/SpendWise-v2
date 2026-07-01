package com.spendwise.sms

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.spendwise.parser.samples.SampleSms
import com.spendwise.storage.UserSessionProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** A fake `content://sms` provider backing a fixed, in-memory set of inbox rows for the test. */
class FakeSmsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE))
        ROWS.forEach { (address, body, date) -> cursor.addRow(arrayOf<Any>(address, body, date)) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        val ROWS = listOf(
            Triple("VM-SBIINB", SampleSms.SBI_DEBIT, 1_751_100_000_000L),
            Triple("VM-HDFCBK", SampleSms.OTP, 1_751_100_100_000L),
            Triple("AX-PYTMSMS", SampleSms.PAYTM_DEBIT, 1_751_100_200_000L),
            Triple("AD-SHOPZY", SampleSms.PROMOTIONAL, 1_751_100_300_000L),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
class SmsInboxBackfillTest {

    private val session = object : UserSessionProvider {
        override fun getUserId(): String = "test-user-1"
    }

    @Test
    fun `backfill enqueues only the financial messages with no duplicates, then syncs exactly once`() = runTest {
        Robolectric.buildContentProvider(FakeSmsProvider::class.java).create("sms")

        val dao = FakeQueuedTransactionDao()
        val pipeline = SmsIngestPipeline(dao, session)
        var syncCallCount = 0
        val backfill = SmsInboxBackfill(pipeline, syncTrigger = { syncCallCount++ })

        val context = ApplicationProvider.getApplicationContext<Context>()
        backfill.run(context)

        // Only the SBI and Paytm rows are financial; OTP and promotional are skipped.
        assertEquals(2, dao.insertedRows.size)
        assertEquals(1, syncCallCount)
    }
}
