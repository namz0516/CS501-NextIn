package com.example.nextin.data.model

data class QueueSnapshot(
    val businesses: List<Business> = emptyList(),
    val entries: List<QueueEntry> = emptyList()
)