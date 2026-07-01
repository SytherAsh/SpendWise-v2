package com.spendwise.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room instrumented test (docs/testing.md E2-S4 requirement) run via Robolectric against a
 * real in-memory SQLite database — this CI environment (.github/workflows/ci.yml `android`
 * job) only runs `./gradlew test` (JVM unit tests), not `connectedAndroidTest` against a real
 * device/emulator, so Robolectric is the practical stand-in that still exercises real Room
 * behavior end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
class QueuedTransactionDaoTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var dao: QueuedTransactionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SpendWiseDatabase::class.java,
        ).build()
        dao = database.queuedTransactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(id: String) = QueuedTransactionEntity(
        transactionId = id,
        transactionDate = "2026-06-28T10:15:00Z",
        debit = 500.0,
        credit = 0.0,
        amount = -500.0,
        drCrIndicator = "DR",
        recipientName = null,
        upiId = null,
        bank = "SBIN",
        transactionMode = "UPI",
        note = null,
        source = "sms",
    )

    @Test
    fun `insert appears in unsynced query`() = runTest {
        dao.insert(entity("txn-1"))

        val unsynced = dao.getUnsynced()

        assertEquals(1, unsynced.size)
        assertEquals("txn-1", unsynced.first().transactionId)
        assertTrue(!unsynced.first().synced)
    }

    @Test
    fun `mark-synced excludes the row from the unsynced query`() = runTest {
        dao.insert(entity("txn-1"))

        dao.markSynced("txn-1")
        val unsynced = dao.getUnsynced()

        assertTrue(unsynced.isEmpty())
    }

    @Test
    fun `entity fields map 1-to-1 to the ingest payload's transaction object fields`() = runTest {
        val original = entity("txn-1").copy(
            recipientName = "Swiggy",
            upiId = "swiggy@okicici",
            note = "lunch",
        )
        dao.insert(original)

        val stored = dao.getUnsynced().first()

        assertEquals(original, stored)
    }
}
