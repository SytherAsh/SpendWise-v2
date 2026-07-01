package com.spendwise.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QueuedTransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: QueuedTransactionEntity)

    @Query("SELECT * FROM queued_transactions WHERE synced = 0")
    suspend fun getUnsynced(): List<QueuedTransactionEntity>

    @Query("UPDATE queued_transactions SET synced = 1 WHERE transactionId = :transactionId")
    suspend fun markSynced(transactionId: String)

    @Query("DELETE FROM queued_transactions WHERE transactionId = :transactionId")
    suspend fun delete(transactionId: String)

    @Query("SELECT transactionId FROM queued_transactions")
    suspend fun getAllTransactionIds(): List<String>
}
