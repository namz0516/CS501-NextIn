package com.example.nextin.data.cloud

import com.example.nextin.data.model.*
import com.example.nextin.data.sample.SampleBusinessData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

// One mapping keeps customer, staff, and public queue metadata consistent.
object CloudQueueCodec {
    val activeStatuses = setOf(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED)
    private val publicFields = setOf(
        "businessId", "partySize", "source", "status", "joinedAt", "skipCount",
        "calledAt", "confirmedArrival", "tableId", "completedAt"
    )

    fun isActive(entry: QueueEntry) = entry.status in activeStatuses

    fun privateData(entry: QueueEntry, previous: DocumentSnapshot? = null): Map<String, Any?> {
        // Preserve original server timestamp precision when a time did not change.
        fun time(value: Long?, field: String): Any? {
            if (value == null) return null
            val old = previous?.getTimestamp(field)
            return if (old?.toDate()?.time == value) old else FieldValue.serverTimestamp()
        }
        return mapOf(
            "businessId" to entry.businessId, "customerUid" to entry.customerUid,
            "name" to entry.name, "partySize" to entry.partySize,
            "source" to entry.source.name, "status" to entry.status.name,
            "joinedAt" to time(entry.joinedAtMillis, "joinedAt"),
            "skipCount" to entry.skipCount, "calledAt" to time(entry.calledAtMillis, "calledAt"),
            "confirmedArrival" to entry.confirmedArrival, "tableId" to entry.tableId,
            "completedAt" to time(entry.completedAtMillis, "completedAt")
        )
    }

    // Names and customer UIDs are never placed in the public queue.
    fun publicData(privateData: Map<String, Any?>) = privateData.filterKeys { it in publicFields }

    fun entry(document: DocumentSnapshot): QueueEntry =
        entry(document.id, requireNotNull(document.data), false)

    fun entry(id: String, data: Map<String, Any?>, isPublic: Boolean): QueueEntry {
        fun string(key: String) = requireNotNull(data[key] as? String) { "Missing " + key }
        fun number(key: String) = requireNotNull(data[key] as? Number) { "Missing " + key }
        fun time(key: String) = (data[key] as? Timestamp)?.toDate()?.time
        return QueueEntry(
            id = id, businessId = string("businessId"),
            customerUid = if (isPublic) null else data["customerUid"] as? String,
            name = if (isPublic) "Waiting party" else string("name"),
            partySize = number("partySize").toInt(),
            source = QueueSource.valueOf(string("source")),
            status = QueueStatus.valueOf(string("status")),
            joinedAtMillis = requireNotNull(time("joinedAt")),
            skipCount = number("skipCount").toInt(), calledAtMillis = time("calledAt"),
            confirmedArrival = data["confirmedArrival"] == true,
            tableId = data["tableId"] as? String, completedAtMillis = time("completedAt")
        )
    }

    fun publicEntries(document: DocumentSnapshot): Map<String, Map<String, Any?>> {
        if (!document.exists()) return emptyMap()
        val entries = requireNotNull(document.get("entries") as? Map<*, *>)
        return entries.entries.associate { (id, value) ->
            requireNotNull(id as? String) to requireNotNull(value as? Map<*, *>)
                .entries.associate { requireNotNull(it.key as? String) to it.value }
        }
    }

    fun business(document: DocumentSnapshot, tables: List<RestaurantTable>? = null): Business {
        val base = SampleBusinessData.businesses.firstOrNull { it.id == document.id }
            ?: throw IllegalArgumentException("Unknown business: " + document.id)
        val configured = document.getLong("schemaVersion") == 1L
        return base.copy(
            // Unconfigured businesses cannot accept cloud joins yet.
            queueOpen = configured && document.getBoolean("queueOpen") == true,
            type = document.getString("type")?.let(BusinessType::valueOf) ?: base.type,
            settings = base.settings.copy(
                maxPartySize = document.getLong("maxPartySize")?.toInt() ?: base.settings.maxPartySize,
                gracePeriodMinutes = document.getLong("gracePeriodMinutes")?.toInt() ?: base.settings.gracePeriodMinutes,
                skipLimit = document.getLong("skipLimit")?.toInt() ?: base.settings.skipLimit,
                averageServiceMinutes = document.getLong("averageServiceMinutes")?.toInt()
                    ?: base.settings.averageServiceMinutes,
                seatingMode = document.getString("seatingMode")?.let(SeatingMode::valueOf)
                    ?: base.settings.seatingMode
            ),
            tables = tables ?: base.tables.map { it.copy(occupiedByEntryId = null) }
        )
    }

    fun table(document: DocumentSnapshot) = RestaurantTable(
        id = document.id,
        capacity = requireNotNull(document.getLong("capacity")).toInt(),
        occupiedByEntryId = document.getString("occupiedByEntryId")
    )
}
