package com.example.nextin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.security.MessageDigest

@Database(
    entities = [BusinessEntity::class, TableEntity::class, QueueEntryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class NextInDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile private var instance: NextInDatabase? = null
        private val customerCaches = mutableMapOf<String, NextInDatabase>()

        // A fresh cache namespace avoids mixing legacy demo data or different users.
        @Synchronized
        fun forCustomer(context: Context, uid: String): NextInDatabase {
            require(uid.isNotBlank())
            val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return customerCaches.getOrPut(key) {
                Room.databaseBuilder(context.applicationContext, NextInDatabase::class.java,
                    "nextin-cloud-" + key + ".db").build()
            }
        }


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