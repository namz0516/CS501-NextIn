package com.example.nextin.domain.queue

import com.example.nextin.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AlertScheduleTest {
    private val calledAt = 1_000_000L
    private val called = QueueEntry(id = "e", businessId = "r", customerUid = "me",
        name = "Alex", partySize = 2, status = QueueStatus.CALLED, calledAtMillis = calledAt)

    @Test fun calledEntrySchedulesReminderAndExpiry() {
        assertEquals(
            listOf(
                AlertTime(AlertKind.REMINDER, calledAt + 4 * 60_000L),
                AlertTime(AlertKind.EXPIRED, calledAt + 5 * 60_000L)
            ),
            AlertSchedule.times(called, 5)
        )
    }

    @Test fun oneMinuteGraceSchedulesOnlyExpiry() {
        assertEquals(
            listOf(AlertTime(AlertKind.EXPIRED, calledAt + 60_000L)),
            AlertSchedule.times(called, 1)
        )
    }

    @Test fun confirmedWaitingAndFinishedEntriesScheduleNothing() {
        assertTrue(AlertSchedule.times(called.copy(confirmedArrival = true), 5).isEmpty())
        assertTrue(AlertSchedule.times(called.copy(status = QueueStatus.WAITING), 5).isEmpty())
        assertTrue(AlertSchedule.times(called.copy(status = QueueStatus.SEATED), 5).isEmpty())
        assertTrue(AlertSchedule.times(called.copy(calledAtMillis = null), 5).isEmpty())
    }

    @Test fun reminderShowsWithSecondsLeft() {
        val now = calledAt + 4 * 60_000L
        assertEquals(AlertDecision.Show(60L),
            AlertSchedule.decide(AlertKind.REMINDER, called, 5, calledAt, now))
    }

    @Test fun expiryShowsAtDeadlineOrWhenAlreadyExpired() {
        val deadline = calledAt + 5 * 60_000L
        assertEquals(AlertDecision.Show(0L),
            AlertSchedule.decide(AlertKind.EXPIRED, called, 5, calledAt, deadline))
        assertEquals(AlertDecision.Show(0L),
            AlertSchedule.decide(AlertKind.EXPIRED, called.copy(status = QueueStatus.EXPIRED), 5, calledAt, deadline))
    }

    @Test fun confirmedSeatedOrRemovedEntriesAreSkipped() {
        val deadline = calledAt + 5 * 60_000L
        listOf(
            called.copy(confirmedArrival = true),
            called.copy(status = QueueStatus.SEATED),
            called.copy(status = QueueStatus.REMOVED),
            called.copy(status = QueueStatus.LEFT)
        ).forEach { entry ->
            assertEquals(AlertDecision.Skip,
                AlertSchedule.decide(AlertKind.EXPIRED, entry, 5, calledAt, deadline))
            assertEquals(AlertDecision.Skip,
                AlertSchedule.decide(AlertKind.REMINDER, entry, 5, calledAt, deadline - 60_000L))
        }
    }

    @Test fun newCallAfterKeepSkipsTheOldAlarm() {
        val kept = called.copy(calledAtMillis = calledAt + 10 * 60_000L)
        assertEquals(AlertDecision.Skip,
            AlertSchedule.decide(AlertKind.EXPIRED, kept, 5, calledAt, calledAt + 5 * 60_000L))
    }

    @Test fun longerGracePeriodReschedules() {
        val now = calledAt + 5 * 60_000L
        assertEquals(AlertDecision.Reschedule(calledAt + 10 * 60_000L),
            AlertSchedule.decide(AlertKind.EXPIRED, called, 10, calledAt, now))
        assertEquals(AlertDecision.Reschedule(calledAt + 9 * 60_000L),
            AlertSchedule.decide(AlertKind.REMINDER, called, 10, calledAt, calledAt + 4 * 60_000L))
    }

    @Test fun alarmsSlightlyEarlyStillShow() {
        val now = calledAt + 5 * 60_000L - 3_000L
        assertEquals(AlertDecision.Show(3L),
            AlertSchedule.decide(AlertKind.EXPIRED, called, 5, calledAt, now))
    }

    @Test fun lateReminderAfterDeadlineIsSkipped() {
        assertEquals(AlertDecision.Skip,
            AlertSchedule.decide(AlertKind.REMINDER, called, 5, calledAt, calledAt + 6 * 60_000L))
    }
}
