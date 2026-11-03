package com.example.nextin.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QueueDao {
    @Query("SELECT * FROM businesses ORDER BY sortOrder, id")
    fun businesses(): List<BusinessEntity>

    @Query("SELECT * FROM restaurant_tables ORDER BY businessId, sortOrder, id")
    fun tables(): List<TableEntity>

    @Query("SELECT * FROM queue_entries ORDER BY sortOrder, id")
    fun entries(): List<QueueEntryEntity>

    @Query("DELETE FROM queue_entries") fun clearEntries()
    @Query("DELETE FROM restaurant_tables") fun clearTables()
    @Query("DELETE FROM businesses") fun clearBusinesses()

    @Insert fun insertBusinesses(values: List<BusinessEntity>)
    @Insert fun insertTables(values: List<TableEntity>)
    @Insert fun insertEntries(values: List<QueueEntryEntity>)
}