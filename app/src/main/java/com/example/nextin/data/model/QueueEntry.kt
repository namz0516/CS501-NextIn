package com.example.nextin.data.model

data class QueueEntry(
    val id: String = "",
    val businessId: String = "",
    val customerUid: String? = null,
    val name: String = "",
    val partySize: Int = 1,
    val source: QueueSource = QueueSource.APP,
    val status: QueueStatus = QueueStatus.WAITING,
    val joinedAtMillis: Long = 0L,
    val skipCount: Int = 0,
    val calledAtMillis: Long? = null,
    val confirmedArrival: Boolean = false,
    val tableId: String? = null,
    val completedAtMillis: Long? = null
)