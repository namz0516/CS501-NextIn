package com.example.nextin.data.model

data class RestaurantTable(
    val id: String = "",
    val capacity: Int = 2,
    val occupiedByEntryId: String? = null
)