package com.example.nextin.data.model

data class Business(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val type: BusinessType = BusinessType.OTHER,
    val cuisine: String? = null,
    val queueOpen: Boolean = false,
    val settings: BusinessSettings = BusinessSettings(),
    val tables: List<RestaurantTable> = emptyList()
)