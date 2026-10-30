package com.example.nextin.data.sample

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.BusinessSettings
import com.example.nextin.data.model.BusinessType
import com.example.nextin.data.model.RestaurantTable
import com.example.nextin.data.model.SeatingMode

object SampleBusinessData {

    val businesses: List<Business> = listOf(
        Business(
            id = "olive-ember",
            name = "Olive & Ember",
            address = "100 Sample Street, Boston",
            description = "A neighborhood Mediterranean restaurant.",
            type = BusinessType.RESTAURANT,
            cuisine = "Mediterranean",
            queueOpen = true,
            settings = BusinessSettings(
                seatingMode = SeatingMode.TABLE_FIT,
                maxPartySize = 8,
                skipLimit = 3,
                averageServiceMinutes = 30,
                gracePeriodMinutes = 5
            ),
            tables = listOf(
                RestaurantTable(id = "olive-t1", capacity = 2),
                RestaurantTable(id = "olive-t2", capacity = 4),
                RestaurantTable(id = "olive-t3", capacity = 6),
                RestaurantTable(id = "olive-t4", capacity = 8)
            )
        ),
        Business(
            id = "bloom-studio",
            name = "Bloom Studio",
            address = "200 Sample Street, Boston",
            description = "A walk-in hair studio.",
            type = BusinessType.OTHER,
            queueOpen = true,
            settings = BusinessSettings(
                seatingMode = SeatingMode.FCFS,
                maxPartySize = 1,
                averageServiceMinutes = 15,
                gracePeriodMinutes = 5
            )
        ),
        Business(
            id = "miso-house",
            name = "Miso House",
            address = "300 Sample Street, Boston",
            description = "Japanese comfort food and ramen.",
            type = BusinessType.RESTAURANT,
            cuisine = "Japanese",
            queueOpen = false,
            settings = BusinessSettings(
                seatingMode = SeatingMode.FCFS,
                maxPartySize = 4,
                averageServiceMinutes = 25,
                gracePeriodMinutes = 5
            ),
            tables = listOf(
                RestaurantTable(id = "miso-t1", capacity = 2),
                RestaurantTable(id = "miso-t2", capacity = 4)
            )
        )
    )
}