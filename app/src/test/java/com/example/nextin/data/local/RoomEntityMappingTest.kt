package com.example.nextin.data.local

import com.example.nextin.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomEntityMappingTest {
    @Test fun businessSettingsAndOccupiedTablesRoundTrip() {
        val table = RestaurantTable("t", 6, "entry")
        val business = Business(id = "r", name = "Example", address = "Sample address", description = "Sample",
            latitude = 42.1, longitude = -71.1, type = BusinessType.RESTAURANT, cuisine = "Sample cuisine",
            queueOpen = true, settings = BusinessSettings(SeatingMode.TABLE_FIT, 6, 2, 35, 7), tables = listOf(table))
        val restoredTable = table.toEntity(business.id).toModel()
        assertEquals(business, business.toEntity().toModel(listOf(restoredTable)))
    }
    @Test fun entryLifecycleFieldsAndNullValuesRoundTrip() {
        val entry = QueueEntry(id = "entry", businessId = "r", customerUid = "customer", name = "Alex",
            partySize = 3, source = QueueSource.APP, status = QueueStatus.CALLED, joinedAtMillis = 100,
            skipCount = 2, calledAtMillis = 200, confirmedArrival = true, tableId = "t", completedAtMillis = 300)
        assertEquals(entry, entry.toEntity().toModel())
        val walkIn = entry.copy(customerUid = null, source = QueueSource.WALK_IN, calledAtMillis = null,
            confirmedArrival = false, tableId = null, completedAtMillis = null)
        assertEquals(walkIn, walkIn.toEntity().toModel())
    }
}