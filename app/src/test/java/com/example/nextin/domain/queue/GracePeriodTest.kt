package com.example.nextin.domain.queue

import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import org.junit.Assert.*
import org.junit.Test

class GracePeriodTest {
    private val business = Business(id = "r", type = BusinessType.RESTAURANT, queueOpen = true,
        settings = BusinessSettings(maxPartySize = 4, gracePeriodMinutes = 1),
        tables = listOf(RestaurantTable("t", 4)))
    private var now = 1_000L
    private fun repository() = LocalQueueRepository(listOf(business), nowMillis = { now })

    @Test fun remainingUsesAbsoluteCallTimeAndClampsAtZero() {
        val entry = QueueEntry(status = QueueStatus.CALLED, calledAtMillis = 1_000)
        assertEquals(60_000L, GracePeriod.remainingMillis(entry, business, 1_000))
        assertEquals(30_000L, GracePeriod.remainingMillis(entry, business, 31_000))
        assertEquals(0L, GracePeriod.remainingMillis(entry, business, 90_000))
    }
    @Test fun uncalledEntryHasNoDeadline() {
        val entry = QueueEntry()
        assertNull(GracePeriod.remainingMillis(entry, business, now))
        assertFalse(GracePeriod.hasExpired(entry, business, now))
    }
    @Test fun confirmedPartyDoesNotExpire() {
        val repo = repository(); val entry = repo.join("r", "customer", "Alex", 2)
        repo.callParty("r"); now += 20_000; repo.confirmArrival(entry.id, "customer")
        now += 100_000; repo.expireCalls()
        assertTrue(repo.state.value.entries.single().confirmedArrival)
        assertEquals(QueueStatus.CALLED, repo.state.value.entries.single().status)
    }
    @Test fun confirmationAtDeadlineIsRejectedAndMarksExpired() {
        val repo = repository(); val entry = repo.join("r", "customer", "Alex", 2)
        repo.callParty("r"); now += 60_000
        assertThrows(IllegalArgumentException::class.java) { repo.confirmArrival(entry.id, "customer") }
        assertEquals(QueueStatus.EXPIRED, repo.state.value.entries.single().status)
    }
    @Test fun anotherCustomerCannotConfirmArrival() {
        val repo = repository(); val entry = repo.join("r", "customer", "Alex", 2)
        repo.callParty("r")
        assertThrows(IllegalArgumentException::class.java) { repo.confirmArrival(entry.id, "other") }
        assertFalse(repo.state.value.entries.single().confirmedArrival)
    }
    @Test fun expiryRetainsEntryAndTableReservation() {
        val repo = repository(); val entry = repo.join("r", "customer", "Alex", 2)
        repo.callParty("r"); now += 60_000; repo.expireCalls()
        assertEquals(QueueStatus.EXPIRED, repo.state.value.entries.single().status)
        assertEquals(entry.id, repo.state.value.businesses.single().tables.single().occupiedByEntryId)
    }
    @Test fun keepingExpiredPartyStartsFreshGracePeriod() {
        val repo = repository(); val entry = repo.join("r", "customer", "Alex", 2)
        repo.callParty("r"); now += 60_000; repo.expireCalls(); repo.keepExpired("r", entry.id)
        val kept = repo.state.value.entries.single()
        assertEquals(QueueStatus.CALLED, kept.status)
        assertEquals(now, kept.calledAtMillis)
        assertEquals(60_000L, GracePeriod.remainingMillis(kept, business, now))
    }
    @Test fun invalidSettingsAreRejectedAndValidModeCanChange() {
        val repo = repository()
        assertThrows(IllegalArgumentException::class.java) { repo.updateSettings("r", business.settings.copy(gracePeriodMinutes = 0)) }
        assertThrows(IllegalArgumentException::class.java) { repo.updateSettings("r", business.settings.copy(averageServiceMinutes = 0)) }
        assertThrows(IllegalArgumentException::class.java) { repo.updateSettings("r", business.settings.copy(maxPartySize = 8)) }
        val changed = business.settings.copy(seatingMode = SeatingMode.TABLE_FIT, skipLimit = 2)
        repo.updateSettings("r", changed)
        assertEquals(changed, repo.state.value.businesses.single().settings)
    }
}