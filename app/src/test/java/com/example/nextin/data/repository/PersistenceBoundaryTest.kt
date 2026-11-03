package com.example.nextin.data.repository

import com.example.nextin.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PersistenceBoundaryTest {
    private val business = Business(id = "r", type = BusinessType.RESTAURANT, queueOpen = true,
        settings = BusinessSettings(maxPartySize = 4), tables = listOf(RestaurantTable("t", 4)))

    @Test fun failedSaveDoesNotPublishJoin() {
        val repo = LocalQueueRepository(listOf(business), persist = { throw IllegalStateException("Disk unavailable") })
        assertThrows(IllegalStateException::class.java) { repo.join("r", "customer", "Alex", 2) }
        assertTrue(repo.state.value.entries.isEmpty())
    }
    @Test fun failedCallSaveDoesNotReserveTableOrChangeEntry() {
        var fail = false
        val repo = LocalQueueRepository(listOf(business), persist = { if (fail) throw IllegalStateException("Disk unavailable") })
        repo.join("r", "customer", "Alex", 2)
        val before = repo.state.value
        fail = true
        assertThrows(IllegalStateException::class.java) { repo.callParty("r") }
        assertEquals(before, repo.state.value)
        assertNull(repo.state.value.businesses.single().tables.single().occupiedByEntryId)
    }
    @Test fun savedEntriesAndTableReservationsCanBeRestoredTogether() {
        val entry = QueueEntry(id = "entry", businessId = "r", customerUid = "customer", name = "Alex",
            status = QueueStatus.CALLED, joinedAtMillis = 100, calledAtMillis = 200, tableId = "t")
        val occupied = business.copy(tables = listOf(RestaurantTable("t", 4, "entry")))
        val repo = LocalQueueRepository(listOf(occupied), initialEntries = listOf(entry))
        assertEquals(QueueSnapshot(listOf(occupied), listOf(entry)), repo.state.value)
    }
}