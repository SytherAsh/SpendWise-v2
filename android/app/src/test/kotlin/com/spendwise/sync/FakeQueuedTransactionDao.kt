package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionDao
import com.spendwise.storage.QueuedTransactionEntity

/** Pure in-memory fake — lets `BatchSyncEngine` be tested without Room/Robolectric. */
class FakeQueuedTransactionDao : QueuedTransactionDao {

    private val rows = mutableMapOf<String, QueuedTransactionEntity>()

    fun seed(vararg entities: QueuedTransactionEntity) {
        entities.forEach { rows[it.transactionId] = it }
    }

    override suspend fun insert(entity: QueuedTransactionEntity) {
        if (!rows.containsKey(entity.transactionId)) rows[entity.transactionId] = entity
    }

    override suspend fun getUnsynced(): List<QueuedTransactionEntity> = rows.values.filter { !it.synced }

    override suspend fun markSynced(transactionId: String) {
        rows[transactionId]?.let { rows[transactionId] = it.copy(synced = true) }
    }

    override suspend fun delete(transactionId: String) {
        rows.remove(transactionId)
    }

    override suspend fun getAllTransactionIds(): List<String> = rows.keys.toList()
}
