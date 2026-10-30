package com.example.nextin.data.model

data class BusinessSettings(
    val seatingMode: SeatingMode = SeatingMode.FCFS,
    val maxPartySize: Int = 8,
    val skipLimit: Int = 3,
    val averageServiceMinutes: Int = 20,
    val gracePeriodMinutes: Int = 5
)