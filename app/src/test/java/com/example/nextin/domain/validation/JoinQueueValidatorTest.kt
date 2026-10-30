package com.example.nextin.domain.validation

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessSettings
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.RestaurantTable
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JoinQueueValidatorTest {

    private val restaurant = Business(
        id = "test-restaurant",
        name = "Test Restaurant",
        type = BusinessType.RESTAURANT,
        queueOpen = true,
        settings = BusinessSettings(
            maxPartySize = 4
        ),
        tables = listOf(
            RestaurantTable(
                id = "table-1",
                capacity = 4
            )
        )
    )

    @Test
    fun validRestaurantPartyIsAccepted() {
        val error = JoinQueueValidator.validate(
            business = restaurant,
            name = "Alex",
            partySize = 2
        )

        assertNull(error)
    }

    @Test
    fun maximumAllowedPartyIsAccepted() {
        val error = JoinQueueValidator.validate(
            business = restaurant,
            name = "Alex",
            partySize = 4
        )

        assertNull(error)
    }

    @Test
    fun blankNamesAreRejected() {
        listOf("", "   ").forEach { name ->
            val error = JoinQueueValidator.validate(
                business = restaurant,
                name = name,
                partySize = 2
            )

            assertNotNull(error)
        }
    }

    @Test
    fun zeroAndNegativePartySizesAreRejected() {
        listOf(0, -1).forEach { size ->
            val error = JoinQueueValidator.validate(
                business = restaurant,
                name = "Alex",
                partySize = size
            )

            assertNotNull(error)
        }
    }

    @Test
    fun partyAboveMaximumIsRejected() {
        val error = JoinQueueValidator.validate(
            business = restaurant,
            name = "Alex",
            partySize = 5
        )

        assertNotNull(error)
    }

    @Test
    fun partyWithoutAFittingTableIsRejected() {
        val business = restaurant.copy(
            settings = restaurant.settings.copy(
                maxPartySize = 8
            )
        )

        val error = JoinQueueValidator.validate(
            business = business,
            name = "Alex",
            partySize = 6
        )

        assertNotNull(error)
    }

    @Test
    fun closedQueueIsRejected() {
        val error = JoinQueueValidator.validate(
            business = restaurant.copy(queueOpen = false),
            name = "Alex",
            partySize = 2
        )

        assertNotNull(error)
    }

    @Test
    fun existingActiveQueueIsRejected() {
        val error = JoinQueueValidator.validate(
            business = restaurant,
            name = "Alex",
            partySize = 2,
            hasActiveEntry = true
        )

        assertNotNull(error)
    }

    @Test
    fun serviceBusinessAcceptsOnlyOnePerson() {
        val studio = Business(
            id = "test-studio",
            type = BusinessType.OTHER,
            queueOpen = true,
            settings = BusinessSettings(
                maxPartySize = 1
            )
        )

        assertNull(
            JoinQueueValidator.validate(
                business = studio,
                name = "Alex",
                partySize = 1
            )
        )

        assertNotNull(
            JoinQueueValidator.validate(
                business = studio,
                name = "Alex",
                partySize = 2
            )
        )
    }
}