package com.spendwise.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QueuedTransactionEntity::class], version = 1, exportSchema = false)
abstract class SpendWiseDatabase : RoomDatabase() {

    abstract fun queuedTransactionDao(): QueuedTransactionDao

    companion object {
        @Volatile
        private var instance: SpendWiseDatabase? = null

        fun getInstance(context: Context): SpendWiseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpendWiseDatabase::class.java,
                    "spendwise.db",
                ).build().also { instance = it }
            }
    }
}
