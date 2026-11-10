package com.example.nextin.data.repository

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessSettings
import com.example.nextin.domain.queue.GracePeriod
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueSnapshot
import com.example.nextin.data.model.QueueSource
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.data.model.SeatingMode
import com.example.nextin.domain.queue.QueuePolicy
import com.example.nextin.domain.validation.JoinQueueValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LocalQueueRepository(
    initialBusinesses: List<Business>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    initialEntries: List<QueueEntry> = emptyList(),
    private val persist: (QueueSnapshot) -> Unit = {}
) {
    private val mutableState = MutableStateFlow(
        QueueSnapshot(businesses = initialBusinesses.toList(), entries = initialEntries.toList())
    )

    val state: StateFlow<QueueSnapshot> = mutableState.asStateFlow()

    @Synchronized
    fun join(
        businessId: String,
        customerUid: String,
        name: String,
        partySize: Int
    ): QueueEntry {
        require(customerUid.isNotBlank()) { "Customer ID is required." }
        val snapshot = mutableState.value
        val business = requireBusiness(snapshot, businessId)
        val hasActiveEntry = snapshot.entries.any {
            it.customerUid == customerUid && isActive(it)
        }
        val error = JoinQueueValidator.validate(
            business, name, partySize, hasActiveEntry
        )
        if (error != null) throw IllegalArgumentException(error)

        val entry = QueueEntry(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            customerUid = customerUid,
            name = name.trim(),
            partySize = partySize,
            source = QueueSource.APP,
            status = QueueStatus.WAITING,
            joinedAtMillis = nowMillis()
        )
        publish(snapshot.copy(entries = snapshot.entries + entry))
        return entry
    }

    @Synchronized
    fun leave(entryId: String, customerUid: String) {
        val snapshot = mutableState.value
        val entry = snapshot.entries.firstOrNull { it.id == entryId }
            ?: throw IllegalArgumentException("Queue entry not found.")
        require(entry.customerUid == customerUid) {
            "You can only leave your own queue."
        }
        require(isActive(entry)) { "This queue entry is already completed." }

        publish(snapshot.copy(
            businesses = releaseTables(snapshot.businesses, entry.id),
            entries = snapshot.entries.map {
                if (it.id == entryId) it.copy(
                    status = QueueStatus.LEFT,
                    completedAtMillis = nowMillis()
                ) else it
            }
        ))
    }

    @Synchronized
    fun addWalkIn(businessId: String, name: String, partySize: Int): QueueEntry {
        val snapshot = mutableState.value
        val business = requireBusiness(snapshot, businessId)
        val error = JoinQueueValidator.validate(business, name, partySize)
        if (error != null) throw IllegalArgumentException(error)

        val entry = QueueEntry(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            customerUid = null,
            name = name.trim(),
            partySize = partySize,
            source = QueueSource.WALK_IN,
            joinedAtMillis = nowMillis()
        )
        publish(snapshot.copy(entries = snapshot.entries + entry))
        return entry
    }

    @Synchronized
    fun callParty(
        businessId: String,
        tableId: String? = null,
        entryId: String? = null
    ): QueueEntry {
        val snapshot = mutableState.value
        val business = requireBusiness(snapshot, businessId)
        val line = QueuePolicy.waiting(businessId, snapshot.entries)
        val party = if (entryId == null) {
            QueuePolicy.suggest(business, snapshot.entries, tableId)
                ?: throw IllegalArgumentException("No eligible waiting party for the open tables.")
        } else {
            line.firstOrNull { it.id == entryId }
                ?: throw IllegalArgumentException("That party is not waiting at this business.")
        }

        val table = if (business.type == BusinessType.RESTAURANT) {
            business.tables.filter {
                it.occupiedByEntryId == null && it.capacity >= party.partySize &&
                        (tableId == null || it.id == tableId)
            }.minByOrNull { it.capacity }
        } else null

        require(business.type != BusinessType.RESTAURANT || table != null) {
            "Choose an open table that fits this party."
        }

        val called = party.copy(
            status = QueueStatus.CALLED,
            calledAtMillis = nowMillis(),
            confirmedArrival = false,
            tableId = table?.id
        )
        val earlierIds = line.takeWhile { it.id != party.id }.map { it.id }.toSet()

        publish(snapshot.copy(
            businesses = snapshot.businesses.map { current ->
                if (current.id == businessId) current.copy(
                    tables = current.tables.map {
                        if (it.id == table?.id) it.copy(occupiedByEntryId = party.id)
                        else it
                    }
                ) else current
            },
            entries = snapshot.entries.map {
                when {
                    it.id == party.id -> called
                    business.type == BusinessType.RESTAURANT &&
                            business.settings.seatingMode == SeatingMode.TABLE_FIT &&
                            it.id in earlierIds -> it.copy(skipCount = it.skipCount + 1)
                    else -> it
                }
            }
        ))
        return called
    }

    @Synchronized
    fun seat(businessId: String, entryId: String) {
        val snapshot = mutableState.value
        val business = requireBusiness(snapshot, businessId)
        val entry = requireEntry(snapshot, businessId, entryId)
        require(entry.status in setOf(QueueStatus.CALLED, QueueStatus.EXPIRED)) {
            "Call the party before marking it seated."
        }
        require(business.type != BusinessType.RESTAURANT || business.tables.any {
            it.id == entry.tableId && it.occupiedByEntryId == entry.id
        }) { "This party has no reserved table." }

        publish(snapshot.copy(
            entries = snapshot.entries.map {
                if (it.id == entryId) it.copy(
                    status = QueueStatus.SEATED,
                    completedAtMillis = nowMillis()
                ) else it
            }
        ))
    }

    @Synchronized
    fun remove(businessId: String, entryId: String) {
        val snapshot = mutableState.value
        val entry = requireEntry(snapshot, businessId, entryId)
        require(isActive(entry)) { "This queue entry is already completed." }
        publish(snapshot.copy(
            businesses = releaseTables(snapshot.businesses, entryId),
            entries = snapshot.entries.map {
                if (it.id == entryId) it.copy(
                    status = QueueStatus.REMOVED,
                    completedAtMillis = nowMillis()
                ) else it
            }
        ))
    }

    @Synchronized
    fun releaseTable(businessId: String, tableId: String) {
        val snapshot = mutableState.value
        val business = requireBusiness(snapshot, businessId)
        val table = business.tables.firstOrNull { it.id == tableId }
            ?: throw IllegalArgumentException("Table not found.")
        require(snapshot.entries.none {
            it.id == table.occupiedByEntryId && isActive(it)
        }) { "Seat or remove the called party before reopening this table." }

        publish(snapshot.copy(
            businesses = snapshot.businesses.map {
                if (it.id == businessId) it.copy(
                    tables = it.tables.map { current ->
                        if (current.id == tableId) current.copy(occupiedByEntryId = null)
                        else current
                    }
                ) else it
            }
        ))
    }

    @Synchronized
    fun setQueueOpen(businessId: String, isOpen: Boolean) {
        val snapshot = mutableState.value
        requireBusiness(snapshot, businessId)
        publish(snapshot.copy(
            businesses = snapshot.businesses.map {
                if (it.id == businessId) it.copy(queueOpen = isOpen) else it
            }
        ))
    }

    @Synchronized
    fun updateSettings(businessId: String, settings: BusinessSettings) {
        val snapshot = mutableState.value
        val business = requireBusiness(snapshot, businessId)
        require(settings.averageServiceMinutes in 1..240) { "Average service must be 1–240 minutes." }
        require(settings.gracePeriodMinutes in 1..30) { "Grace period must be 1–30 minutes." }
        require(settings.skipLimit in 1..10) { "Skip limit must be 1–10." }
        require(settings.maxPartySize in 1..8) { "Maximum party size must be 1–8." }
        require(business.type != BusinessType.OTHER ||
                (settings.seatingMode == SeatingMode.FCFS && settings.maxPartySize == 1)) {
            "Service businesses use FCFS and individual customers."
        }
        require(business.type != BusinessType.RESTAURANT ||
                business.tables.any { it.capacity >= settings.maxPartySize }) {
            "The maximum party size must fit a table in this inventory."
        }
        publish(snapshot.copy(businesses = snapshot.businesses.map {
            if (it.id == businessId) it.copy(settings = settings) else it
        }))
    }

    @Synchronized
    fun expireCalls(atMillis: Long = nowMillis()) {
        val snapshot = mutableState.value
        val entries = snapshot.entries.map { entry ->
            val business = requireBusiness(snapshot, entry.businessId)
            if (GracePeriod.hasExpired(entry, business, atMillis)) {
                entry.copy(status = QueueStatus.EXPIRED)
            } else entry
        }
        if (entries != snapshot.entries) publish(snapshot.copy(entries = entries))
    }

    @Synchronized
    fun confirmArrival(entryId: String, customerUid: String) {
        val snapshot = mutableState.value
        val entry = snapshot.entries.firstOrNull { it.id == entryId }
            ?: throw IllegalArgumentException("Queue entry not found.")
        require(entry.customerUid == customerUid) { "You can only confirm your own arrival." }
        require(entry.status == QueueStatus.CALLED) { "This party is not awaiting confirmation. Please contact staff." }
        if (entry.confirmedArrival) return
        val business = requireBusiness(snapshot, entry.businessId)
        val now = nowMillis()
        if (GracePeriod.hasExpired(entry, business, now)) {
            expireCalls(now)
            throw IllegalArgumentException("The grace period ended. Please contact staff.")
        }
        publish(snapshot.copy(entries = snapshot.entries.map {
            if (it.id == entryId) it.copy(confirmedArrival = true) else it
        }))
    }

    @Synchronized
    fun keepExpired(businessId: String, entryId: String) {
        val snapshot = mutableState.value
        val entry = requireEntry(snapshot, businessId, entryId)
        require(entry.status == QueueStatus.EXPIRED) { "Only expired parties can be kept with a fresh grace period." }
        publish(snapshot.copy(entries = snapshot.entries.map {
            if (it.id == entryId) it.copy(status = QueueStatus.CALLED,
                calledAtMillis = nowMillis(), confirmedArrival = false) else it
        }))
    }

    private fun publish(next: QueueSnapshot) {
        persist(next)
        mutableState.value = next
    }

    private fun requireBusiness(snapshot: QueueSnapshot, id: String): Business {
        return snapshot.businesses.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Business not found.")
    }

    private fun requireEntry(
        snapshot: QueueSnapshot,
        businessId: String,
        entryId: String
    ): QueueEntry {
        requireBusiness(snapshot, businessId)
        return snapshot.entries.firstOrNull {
            it.id == entryId && it.businessId == businessId
        } ?: throw IllegalArgumentException("Entry not found for this business.")
    }

    private fun releaseTables(businesses: List<Business>, entryId: String): List<Business> {
        return businesses.map { business ->
            business.copy(tables = business.tables.map {
                if (it.occupiedByEntryId == entryId) it.copy(occupiedByEntryId = null)
                else it
            })
        }
    }

    private fun isActive(entry: QueueEntry): Boolean {
        return entry.status in setOf(
            QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED
        )
    }
}