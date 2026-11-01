package com.example.nextin.data.repository

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueSnapshot
import com.example.nextin.data.model.QueueSource
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.domain.validation.JoinQueueValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LocalQueueRepository(
    initialBusinesses: List<Business>
) {
    private val mutableState = MutableStateFlow(
        QueueSnapshot(
            businesses = initialBusinesses.toList()
        )
    )

    val state: StateFlow<QueueSnapshot> = mutableState.asStateFlow()

    @Synchronized
    fun join(
        businessId: String,
        customerUid: String,
        name: String,
        partySize: Int
    ): QueueEntry {
        require(customerUid.isNotBlank()) {
            "Customer ID is required."
        }

        val snapshot = mutableState.value

        val business = snapshot.businesses.firstOrNull {
            it.id == businessId
        } ?: throw IllegalArgumentException("Business not found.")

        val hasActiveEntry = snapshot.entries.any {
            it.customerUid == customerUid && isActive(it)
        }

        val error = JoinQueueValidator.validate(
            business = business,
            name = name,
            partySize = partySize,
            hasActiveEntry = hasActiveEntry
        )

        if (error != null) {
            throw IllegalArgumentException(error)
        }

        val entry = QueueEntry(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            customerUid = customerUid,
            name = name.trim(),
            partySize = partySize,
            source = QueueSource.APP,
            status = QueueStatus.WAITING,
            joinedAtMillis = System.currentTimeMillis()
        )

        mutableState.value = snapshot.copy(
            entries = snapshot.entries + entry
        )

        return entry
    }

    @Synchronized
    fun leave(
        entryId: String,
        customerUid: String
    ) {
        val snapshot = mutableState.value

        val entry = snapshot.entries.firstOrNull {
            it.id == entryId
        } ?: throw IllegalArgumentException("Queue entry not found.")

        require(entry.customerUid == customerUid) {
            "You can only leave your own queue."
        }

        require(isActive(entry)) {
            "This queue entry is already completed."
        }

        val updatedEntries = snapshot.entries.map {
            if (it.id == entryId) {
                it.copy(
                    status = QueueStatus.LEFT,
                    completedAtMillis = System.currentTimeMillis()
                )
            } else {
                it
            }
        }

        val updatedBusinesses = snapshot.businesses.map { business ->
            if (business.id == entry.businessId) {
                business.copy(
                    tables = business.tables.map { table ->
                        if (table.occupiedByEntryId == entryId) {
                            table.copy(occupiedByEntryId = null)
                        } else {
                            table
                        }
                    }
                )
            } else {
                business
            }
        }

        mutableState.value = snapshot.copy(
            businesses = updatedBusinesses,
            entries = updatedEntries
        )
    }

    private fun isActive(entry: QueueEntry): Boolean {
        return entry.status in setOf(
            QueueStatus.WAITING,
            QueueStatus.CALLED,
            QueueStatus.EXPIRED
        )
    }
}