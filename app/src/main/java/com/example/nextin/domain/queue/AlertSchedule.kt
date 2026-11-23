package com.example.nextin.domain.queue

import com.example.nextin.data.model.QueueEntry
import com.example.nextin.data.model.QueueStatus

enum class AlertKind { REMINDER, EXPIRED }

data class AlertTime(val kind: AlertKind, val atMillis: Long)

sealed interface AlertDecision {
    data class Show(val secondsLeft: Long) : AlertDecision
    data class Reschedule(val atMillis: Long) : AlertDecision
    data object Skip : AlertDecision
}

object AlertSchedule {
    const val EARLY_TOLERANCE_MILLIS = 5_000L

    fun key(entryId: String, calledAtMillis: Long?): String = "$entryId:$calledAtMillis"

    fun times(entry: QueueEntry, graceMinutes: Int): List<AlertTime> {
        if (entry.status != QueueStatus.CALLED || entry.confirmedArrival) return emptyList()
        val calledAt = entry.calledAtMillis ?: return emptyList()
        val graceMillis = graceMinutes * 60_000L
        if (graceMillis <= 0L) return emptyList()
        val deadline = calledAt + graceMillis
        return buildList {
            if (graceMillis > QueueAlerts.REMINDER_WINDOW_MILLIS) {
                add(AlertTime(AlertKind.REMINDER, deadline - QueueAlerts.REMINDER_WINDOW_MILLIS))
            }
            add(AlertTime(AlertKind.EXPIRED, deadline))
        }
    }

    fun decide(
        kind: AlertKind,
        entry: QueueEntry,
        graceMinutes: Int,
        expectedCalledAtMillis: Long,
        nowMillis: Long
    ): AlertDecision {
        if (entry.calledAtMillis != expectedCalledAtMillis) return AlertDecision.Skip
        if (kind == AlertKind.EXPIRED && entry.status == QueueStatus.EXPIRED) {
            return AlertDecision.Show(0L)
        }
        val time = times(entry, graceMinutes).firstOrNull { it.kind == kind }
            ?: return AlertDecision.Skip
        if (nowMillis < time.atMillis - EARLY_TOLERANCE_MILLIS) {
            return AlertDecision.Reschedule(time.atMillis)
        }
        val deadline = expectedCalledAtMillis + graceMinutes * 60_000L
        val remaining = deadline - nowMillis
        if (kind == AlertKind.REMINDER && remaining <= 0L) return AlertDecision.Skip
        return AlertDecision.Show(((remaining + 999) / 1000).coerceAtLeast(0L))
    }
}
