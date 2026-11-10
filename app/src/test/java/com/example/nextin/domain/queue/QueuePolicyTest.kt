package com.example.nextin.domain.queue

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessSettings
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.data.model.RestaurantTable
import com.example.nextin.data.model.SeatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuePolicyTest {
    private val business = Business(
        id = "r", type = BusinessType.RESTAURANT, queueOpen = true,
        settings = BusinessSettings(
            seatingMode = SeatingMode.TABLE_FIT, skipLimit = 2,
            maxPartySize = 8, averageServiceMinutes = 20
        ),
        tables = listOf(
            RestaurantTable("t2", 2), RestaurantTable("t4", 4),
            RestaurantTable("t6", 6), RestaurantTable("t8", 8)
        )
    )

    private fun party(id: String, size: Int, time: Long, skips: Int = 0) =
        QueueEntry(id = id, businessId = "r", partySize = size,
            joinedAtMillis = time, skipCount = skips)

    @Test
    fun waitingUsesTimestampThenIdAndExcludesOtherEntries() {
        val entries = listOf(
            party("b", 2, 1), party("a", 2, 1),
            party("called", 2, 0).copy(status = QueueStatus.CALLED),
            party("other", 2, 0).copy(businessId = "another")
        )
        assertEquals(listOf("a", "b"), QueuePolicy.waiting("r", entries).map { it.id })
    }

    @Test
    fun strictFcfsDoesNotBypassFirstPartyForSmallTable() {
        val strict = business.copy(settings = business.settings.copy(seatingMode = SeatingMode.FCFS))
        assertNull(QueuePolicy.suggest(strict, listOf(party("large", 6, 1), party("small", 2, 2)), "t2"))
    }

    @Test
    fun strictFcfsSelectsOldestWhenTableFits() {
        val strict = business.copy(settings = business.settings.copy(seatingMode = SeatingMode.FCFS))
        assertEquals("large", QueuePolicy.suggest(strict, listOf(party("large", 6, 1), party("small", 2, 2)), "t8")?.id)
    }

    @Test
    fun tableFitSelectsEarliestPartyThatFits() {
        val line = listOf(party("large", 6, 1), party("small", 2, 2), party("later", 2, 3))
        assertEquals("small", QueuePolicy.suggest(business, line, "t2")?.id)
    }

    @Test
    fun fittingPartyAtSkipLimitGetsPriority() {
        val line = listOf(party("older", 2, 1), party("priority", 6, 2, skips = 2))
        assertEquals("priority", QueuePolicy.suggest(business, line, "t6")?.id)
    }

    @Test
    fun priorityNeverOverridesTableCapacity() {
        val line = listOf(party("older", 2, 1), party("priority", 6, 2, skips = 2))
        assertEquals("older", QueuePolicy.suggest(business, line, "t2")?.id)
    }

    @Test
    fun occupiedTablesAreUnavailable() {
        val full = business.copy(tables = business.tables.map { it.copy(occupiedByEntryId = "busy") })
        assertNull(QueuePolicy.suggest(full, listOf(party("a", 2, 1))))
    }

    @Test
    fun restaurantEstimateCountsCompetingPartiesAndRoundsUp() {
        val line = listOf(party("a", 2, 1), party("b", 4, 2), party("mine", 4, 3))
        assertEquals(14, QueuePolicy.estimatedWaitMinutes(business, line, 4, "mine"))
    }

    @Test
    fun serviceEstimateUsesCustomersAhead() {
        val service = business.copy(type = BusinessType.OTHER,
            settings = business.settings.copy(averageServiceMinutes = 12))
        val line = listOf(party("a", 1, 1), party("b", 1, 2), party("mine", 1, 3))
        assertEquals(24, QueuePolicy.estimatedWaitMinutes(service, line, 1, "mine"))
    }

    @Test
    fun unsupportedPartyOrMissingEntryHasNoEstimate() {
        val smallTables = business.copy(tables = listOf(RestaurantTable("small", 2)))
        assertNull(QueuePolicy.estimatedWaitMinutes(smallTables, emptyList(), 6))
        assertNull(QueuePolicy.estimatedWaitMinutes(business, emptyList(), 0))
        assertNull(QueuePolicy.estimatedWaitMinutes(business, emptyList(), 2, "missing"))
    }
}