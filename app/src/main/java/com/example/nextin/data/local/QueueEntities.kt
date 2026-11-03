package com.example.nextin.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nextin.data.model.*

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey val id: String,
    val sortOrder: Int,
    val name: String,
    val address: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val cuisine: String?,
    val queueOpen: Boolean,
    val seatingMode: String,
    val maxPartySize: Int,
    val skipLimit: Int,
    val averageServiceMinutes: Int,
    val gracePeriodMinutes: Int
)

@Entity(
    tableName = "restaurant_tables",
    primaryKeys = ["businessId", "id"],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class,
        parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("businessId")]
)
data class TableEntity(
    val businessId: String, val id: String, val sortOrder: Int,
    val capacity: Int, val occupiedByEntryId: String?
)

@Entity(
    tableName = "queue_entries",
    foreignKeys = [ForeignKey(entity = BusinessEntity::class,
        parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("businessId"), Index("customerUid")]
)
data class QueueEntryEntity(
    @PrimaryKey val id: String,
    val sortOrder: Int,
    val businessId: String,
    val customerUid: String?,
    val name: String,
    val partySize: Int,
    val source: String,
    val status: String,
    val joinedAtMillis: Long,
    val skipCount: Int,
    val calledAtMillis: Long?,
    val confirmedArrival: Boolean,
    val tableId: String?,
    val completedAtMillis: Long?
)

fun Business.toEntity(order: Int = 0) = BusinessEntity(
    id, order, name, address, description, latitude, longitude, type.name, cuisine,
    queueOpen, settings.seatingMode.name, settings.maxPartySize, settings.skipLimit,
    settings.averageServiceMinutes, settings.gracePeriodMinutes
)

fun BusinessEntity.toModel(tables: List<RestaurantTable>) = Business(
    id = id, name = name, address = address, description = description,
    latitude = latitude, longitude = longitude, type = BusinessType.valueOf(type),
    cuisine = cuisine, queueOpen = queueOpen,
    settings = BusinessSettings(SeatingMode.valueOf(seatingMode), maxPartySize,
        skipLimit, averageServiceMinutes, gracePeriodMinutes),
    tables = tables
)

fun RestaurantTable.toEntity(businessId: String, order: Int = 0) =
    TableEntity(businessId, id, order, capacity, occupiedByEntryId)

fun TableEntity.toModel() = RestaurantTable(id, capacity, occupiedByEntryId)

fun QueueEntry.toEntity(order: Int = 0) = QueueEntryEntity(
    id, order, businessId, customerUid, name, partySize, source.name, status.name,
    joinedAtMillis, skipCount, calledAtMillis, confirmedArrival, tableId, completedAtMillis
)

fun QueueEntryEntity.toModel() = QueueEntry(
    id = id, businessId = businessId, customerUid = customerUid, name = name,
    partySize = partySize, source = QueueSource.valueOf(source), status = QueueStatus.valueOf(status),
    joinedAtMillis = joinedAtMillis, skipCount = skipCount, calledAtMillis = calledAtMillis,
    confirmedArrival = confirmedArrival, tableId = tableId, completedAtMillis = completedAtMillis
)