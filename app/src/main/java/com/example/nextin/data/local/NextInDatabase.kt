package com.example.nextin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BusinessEntity::class, TableEntity::class, QueueEntryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class NextInDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile private var instance: NextInDatabase? = null

        fun getInstance(context: Context): NextInDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NextInDatabase::class.java,
                    "nextin-local.db"
                ).build().also { instance = it }
            }
        }
    }
}