package com.example.nextin.domain.queue

import com.example.nextin.data.model.Business
import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus

object GracePeriod {
    fun remainingMillis(entry: QueueEntry, business: Business, nowMillis: Long): Long? {
        val calledAt = entry.calledAtMillis ?: return null
        val deadline = calledAt + business.settings.gracePeriodMinutes * 60_000L
        return (deadline - nowMillis).coerceAtLeast(0L)
    }

    fun hasExpired(entry: QueueEntry, business: Business, nowMillis: Long): Boolean {
        return entry.status == QueueStatus.CALLED && !entry.confirmedArrival &&
                remainingMillis(entry, business, nowMillis) == 0L
    }
}