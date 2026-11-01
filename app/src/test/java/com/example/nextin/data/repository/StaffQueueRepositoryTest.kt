package com.example.nextin.data.repository

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessSettings
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueSource
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.data.model.RestaurantTable
import com.example.nextin.data.model.SeatingMode
import com.example.nextin.domain.queue.QueuePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffQueueRepositoryTest {
    private val restaurant = Business(
        id = "r", type = BusinessType.RESTAURANT, queueOpen = true,
        settings = BusinessSettings(seatingMode = SeatingMode.TABLE_FIT, maxPartySize = 6),
        tables = listOf(RestaurantTable("small", 2), RestaurantTable("large", 6))
    )
    private val studio = Business(
        id = "s", type = BusinessType.OTHER, queueOpen = true,
        settings = BusinessSettings(maxPartySize = 1)
    )

    private fun repository(): LocalQueueRepository {
        var time = 1_000L
        return LocalQueueRepository(listOf(restaurant, studio), nowMillis = { time++ })
    }

    @Test
    fun walkInsAndAppCustomersShareOneArrivalOrderedQueue() {
        val repo = repository()
        val app = repo.join("r", "customer", "Alex", 2)
        val walkIn = repo.addWalkIn("r", "  Sam  ", 2)
        assertEquals(QueueSource.WALK_IN, walkIn.source)
        assertNull(walkIn.customerUid)
        assertEquals("Sam", walkIn.name)
        assertEquals(listOf(app.id, walkIn.id), QueuePolicy.waiting("r", repo.state.value.entries).map { it.id })
    }

    @Test
    fun callReservesTableAndSeatingKeepsItBusyUntilReleased() {
        val repo = repository()
        val party = repo.addWalkIn("r", "Alex", 2)
        val called = repo.callParty("r")
        assertEquals(party.id, called.id)
        assertEquals(QueueStatus.CALLED, called.status)
        assertNotNull(called.calledAtMillis)
        assertEquals("small", called.tableId)
        assertThrows(IllegalArgumentException::class.java) { repo.releaseTable("r", "small") }

        repo.seat("r", party.id)
        assertEquals(QueueStatus.SEATED, repo.state.value.entries.single().status)
        assertNotNull(repo.state.value.entries.single().completedAtMillis)
        assertEquals(party.id, repo.state.value.businesses.first { it.id == "r" }.tables.first().occupiedByEntryId)
        repo.releaseTable("r", "small")
        assertNull(repo.state.value.businesses.first { it.id == "r" }.tables.first().occupiedByEntryId)
    }

    @Test
    fun onlySuccessfulTableFitCallIncrementsEarlierSkipCount() {
        val repo = repository()
        val large = repo.addWalkIn("r", "Large", 6)
        val small = repo.addWalkIn("r", "Small", 2)
        repeat(2) { QueuePolicy.suggest(restaurant, repo.state.value.entries, "small") }
        assertEquals(0, repo.state.value.entries.first { it.id == large.id }.skipCount)
        assertEquals(small.id, repo.callParty("r", tableId = "small").id)
        assertEquals(1, repo.state.value.entries.first { it.id == large.id }.skipCount)
        val afterCall = repo.state.value
        assertThrows(IllegalArgumentException::class.java) { repo.callParty("r", tableId = "small") }
        assertEquals(afterCall, repo.state.value)
    }

    @Test
    fun removalIsBusinessScopedAndReleasesReservationOnlyForRemovedParty() {
        val repo = repository()
        val party = repo.addWalkIn("r", "Alex", 2)
        val other = repo.addWalkIn("r", "Sam", 2)
        repo.callParty("r", entryId = party.id)
        assertThrows(IllegalArgumentException::class.java) { repo.remove("s", party.id) }
        repo.remove("r", party.id)
        assertEquals(QueueStatus.REMOVED, repo.state.value.entries.first { it.id == party.id }.status)
        assertEquals(QueueStatus.WAITING, repo.state.value.entries.first { it.id == other.id }.status)
        assertNull(repo.state.value.businesses.first { it.id == "r" }.tables.first().occupiedByEntryId)
    }

    @Test
    fun closingQueueBlocksNewJoinsButExistingPartyCanBeServed() {
        val repo = repository()
        val party = repo.addWalkIn("r", "Alex", 2)
        repo.setQueueOpen("r", false)
        assertThrows(IllegalArgumentException::class.java) { repo.addWalkIn("r", "Sam", 2) }
        assertThrows(IllegalArgumentException::class.java) { repo.join("r", "new", "Sam", 2) }
        assertEquals(party.id, repo.callParty("r").id)
        repo.seat("r", party.id)
        repo.setQueueOpen("r", true)
        assertTrue(repo.state.value.businesses.first { it.id == "r" }.queueOpen)
    }

    @Test
    fun invalidTransitionsAndWrongBusinessSeatingAreRejected() {
        val repo = repository()
        val party = repo.addWalkIn("r", "Alex", 2)
        assertThrows(IllegalArgumentException::class.java) { repo.seat("r", party.id) }
        repo.callParty("r", entryId = party.id)
        assertThrows(IllegalArgumentException::class.java) { repo.seat("s", party.id) }
        assertThrows(IllegalArgumentException::class.java) { repo.callParty("r", entryId = party.id) }
        repo.seat("r", party.id)
        assertThrows(IllegalArgumentException::class.java) { repo.remove("r", party.id) }
    }

    @Test
    fun staffOverrideCannotIgnoreCapacityAndCalledCustomerCanLeave() {
        val repo = repository()
        val large = repo.join("r", "customer", "Alex", 6)
        repo.addWalkIn("r", "Sam", 2)
        val before = repo.state.value
        assertThrows(IllegalArgumentException::class.java) {
            repo.callParty("r", tableId = "small", entryId = large.id)
        }
        assertEquals(before, repo.state.value)
        repo.callParty("r", tableId = "large", entryId = large.id)
        repo.leave(large.id, "customer")
        assertEquals(QueueStatus.LEFT, repo.state.value.entries.first { it.id == large.id }.status)
        assertNull(repo.state.value.businesses.first { it.id == "r" }.tables.first { it.id == "large" }.occupiedByEntryId)
    }

    @Test
    fun serviceBusinessCallsFirstArrivalWithoutTableReservation() {
        val repo = repository()
        val first = repo.addWalkIn("s", "Alex", 1)
        repo.addWalkIn("s", "Sam", 1)
        val called = repo.callParty("s")
        assertEquals(first.id, called.id)
        assertNull(called.tableId)
        repo.seat("s", first.id)
        assertEquals(QueueStatus.SEATED, repo.state.value.entries.first { it.id == first.id }.status)
    }
}