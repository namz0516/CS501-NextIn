package com.example.nextin.domain.queue

import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueSnapshot
import com.example.nextin.data.model.QueueStatus

sealed interface QueueAlert {
    val entryId: String

    data class Called(
        override val entryId: String,
        val businessName: String,
        val graceMinutes: Int
    ) : QueueAlert

    data class Reminder(
        override val entryId: String,
        val businessName: String,
        val secondsLeft: Long,
        val key: String
    ) : QueueAlert

    data class Expired(
        override val entryId: String,
        val businessName: String
    ) : QueueAlert

    data class Removed(
        override val entryId: String,
        val businessName: String
    ) : QueueAlert

    data class Clear(
        override val entryId: String
    ) : QueueAlert
}

object QueueAlerts {
    const val REMINDER_WINDOW_MILLIS = 60_000L

    private val ACTIVE = setOf(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.EXPIRED)
    private val ALERTED = setOf(QueueStatus.CALLED, QueueStatus.EXPIRED)

    fun detect(
        previous: QueueSnapshot,
        current: QueueSnapshot,
        customerUid: String
    ): List<QueueAlert> {
        if (customerUid.isBlank()) return emptyList()
        val before = previous.entries.associateBy { it.id }

        return current.entries
            .filter { it.customerUid == customerUid }
            .mapNotNull { entry ->
                val old = before[entry.id] ?: return@mapNotNull null
                val name = businessName(current, entry)
                when {
                    entry.status == QueueStatus.CALLED && !entry.confirmedArrival &&
                            (old.status != QueueStatus.CALLED || old.calledAtMillis != entry.calledAtMillis) -> {
                        val minutes = current.businesses
                            .firstOrNull { it.id == entry.businessId }
                            ?.settings?.gracePeriodMinutes ?: 0
                        QueueAlert.Called(entry.id, name, minutes)
                    }

                    entry.status == QueueStatus.EXPIRED && old.status != QueueStatus.EXPIRED ->
                        QueueAlert.Expired(entry.id, name)

                    entry.status == QueueStatus.REMOVED && old.status in ACTIVE ->
                        QueueAlert.Removed(entry.id, name)

                    old.status in ALERTED && (
                            entry.status !in ACTIVE ||
                                    (entry.confirmedArrival && !old.confirmedArrival)) ->
                        QueueAlert.Clear(entry.id)

                    else -> null
                }
            }
    }

    fun reminder(
        snapshot: QueueSnapshot,
        customerUid: String,
        nowMillis: Long,
        alreadySent: Set<String>
    ): QueueAlert.Reminder? {
        if (customerUid.isBlank()) return null
        val entry = snapshot.entries.firstOrNull {
            it.customerUid == customerUid &&
                    it.status == QueueStatus.CALLED && !it.confirmedArrival
        } ?: return null
        val business = snapshot.businesses.firstOrNull { it.id == entry.businessId } ?: return null
        if (business.settings.gracePeriodMinutes * 60_000L <= REMINDER_WINDOW_MILLIS) return null

        val remaining = GracePeriod.remainingMillis(entry, business, nowMillis) ?: return null
        if (remaining <= 0L || remaining > REMINDER_WINDOW_MILLIS) return null

        val key = "${entry.id}:${entry.calledAtMillis}"
        if (key in alreadySent) return null
        return QueueAlert.Reminder(
            entryId = entry.id,
            businessName = business.name,
            secondsLeft = (remaining + 999) / 1000,
            key = key
        )
    }

    private fun businessName(snapshot: QueueSnapshot, entry: QueueEntry): String {
        return snapshot.businesses.firstOrNull { it.id == entry.businessId }?.name ?: "the business"
    }
}
