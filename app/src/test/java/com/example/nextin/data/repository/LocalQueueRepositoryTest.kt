package com.example.nextin.data.repository

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessSettings
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.QueueStatus
import com.example.nextin.data.model.RestaurantTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQueueRepositoryTest {

    private val restaurant = Business(
        id = "restaurant",
        type = BusinessType.RESTAURANT,
        queueOpen = true,
        settings = BusinessSettings(maxPartySize = 4),
        tables = listOf(
            RestaurantTable(id = "table-1", capacity = 4)
        )
    )

    private val studio = Business(
        id = "studio",
        type = BusinessType.OTHER,
        queueOpen = true,
        settings = BusinessSettings(maxPartySize = 1)
    )

    private fun newRepository(): LocalQueueRepository {
        return LocalQueueRepository(
            initialBusinesses = listOf(restaurant, studio)
        )
    }

    @Test
    fun joinCreatesWaitingEntry() {
        val repository = newRepository()

        val entry = repository.join(
            businessId = "restaurant",
            customerUid = "customer-1",
            name = "  Alex  ",
            partySize = 2
        )

        assertEquals("Alex", entry.name)
        assertEquals(QueueStatus.WAITING, entry.status)
        assertTrue(entry.id.isNotBlank())
        assertTrue(entry.joinedAtMillis > 0)
        assertEquals(listOf(entry), repository.state.value.entries)
    }

    @Test
    fun customerCannotJoinTwoBusinessesAtOnce() {
        val repository = newRepository()

        repository.join("restaurant", "customer-1", "Alex", 2)

        assertThrows(IllegalArgumentException::class.java) {
            repository.join("studio", "customer-1", "Alex", 1)
        }

        assertEquals(1, repository.state.value.entries.size)
    }

    @Test
    fun leavingAllowsCustomerToJoinAnotherBusiness() {
        val repository = newRepository()

        val first = repository.join(
            "restaurant", "customer-1", "Alex", 2
        )

        repository.leave(first.id, "customer-1")

        val second = repository.join(
            "studio", "customer-1", "Alex", 1
        )

        val entries = repository.state.value.entries

        assertEquals(
            QueueStatus.LEFT,
            entries.first { it.id == first.id }.status
        )

        assertEquals(QueueStatus.WAITING, second.status)
        assertEquals(2, entries.size)
    }

    @Test
    fun customerCannotLeaveAnotherCustomersEntry() {
        val repository = newRepository()

        val entry = repository.join(
            "restaurant", "customer-1", "Alex", 2
        )

        assertThrows(IllegalArgumentException::class.java) {
            repository.leave(entry.id, "customer-2")
        }

        assertEquals(
            QueueStatus.WAITING,
            repository.state.value.entries.single().status
        )
    }

    @Test
    fun closedBusinessDoesNotCreateEntry() {
        val repository = LocalQueueRepository(
            initialBusinesses = listOf(
                restaurant.copy(queueOpen = false)
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            repository.join(
                "restaurant", "customer-1", "Alex", 2
            )
        }

        assertTrue(repository.state.value.entries.isEmpty())
    }

    @Test
    fun invalidInputDoesNotCreateEntry() {
        val repository = newRepository()

        assertThrows(IllegalArgumentException::class.java) {
            repository.join(
                "restaurant", "customer-1", "   ", 2
            )
        }

        assertTrue(repository.state.value.entries.isEmpty())
    }
}