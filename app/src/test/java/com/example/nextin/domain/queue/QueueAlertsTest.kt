package com.example.nextin.domain.queue

import com.example.nextin.data.model.*
import org.junit.Assert.*
import org.junit.Test

class QueueAlertsTest {
    private val business = Business(id = "r", name = "Olive", type = BusinessType.RESTAURANT,
        queueOpen = true, settings = BusinessSettings(gracePeriodMinutes = 5),
        tables = listOf(RestaurantTable("t", 4)))
    private val waiting = QueueEntry(id = "e", businessId = "r", customerUid = "me",
        name = "Alex", partySize = 2, status = QueueStatus.WAITING)

    private fun snapshot(vararg entries: QueueEntry, settings: BusinessSettings = business.settings) =
        QueueSnapshot(listOf(business.copy(settings = settings)), entries.toList())

    private fun called(at: Long = 0L, confirmed: Boolean = false) =
        waiting.copy(status = QueueStatus.CALLED, calledAtMillis = at, confirmedArrival = confirmed)

    @Test fun callingTheCustomerRaisesCalledAlert() {
        val alerts = QueueAlerts.detect(snapshot(waiting), snapshot(called()), "me")
        assertEquals(listOf(QueueAlert.Called("e", "Olive", 5)), alerts)
    }

    @Test fun otherCustomersAndWalkInsAreIgnored() {
        val other = waiting.copy(id = "o", customerUid = "someone")
        val walkIn = waiting.copy(id = "w", customerUid = null, source = QueueSource.WALK_IN)
        val before = snapshot(other, walkIn)
        val after = snapshot(other.copy(status = QueueStatus.CALLED), walkIn.copy(status = QueueStatus.CALLED))
        assertTrue(QueueAlerts.detect(before, after, "me").isEmpty())
    }

    @Test fun blankCustomerIdNeverAlerts() {
        assertTrue(QueueAlerts.detect(snapshot(waiting), snapshot(called()), "").isEmpty())
    }

    @Test fun newEntriesAndUnchangedEntriesDoNotAlert() {
        assertTrue(QueueAlerts.detect(snapshot(), snapshot(called()), "me").isEmpty())
        assertTrue(QueueAlerts.detect(snapshot(called()), snapshot(called()), "me").isEmpty())
    }

    @Test fun keepingAnExpiredPartyAlertsAgain() {
        val expired = called(at = 0L).copy(status = QueueStatus.EXPIRED)
        val alerts = QueueAlerts.detect(snapshot(expired), snapshot(called(at = 400_000L)), "me")
        assertEquals(listOf(QueueAlert.Called("e", "Olive", 5)), alerts)
    }

    @Test fun expiryRaisesExpiredAlert() {
        val expired = called().copy(status = QueueStatus.EXPIRED)
        assertEquals(listOf(QueueAlert.Expired("e", "Olive")),
            QueueAlerts.detect(snapshot(called()), snapshot(expired), "me"))
    }

    @Test fun removalRaisesRemovedAlert() {
        val removed = waiting.copy(status = QueueStatus.REMOVED)
        assertEquals(listOf(QueueAlert.Removed("e", "Olive")),
            QueueAlerts.detect(snapshot(waiting), snapshot(removed), "me"))
    }

    @Test fun confirmingOrBeingSeatedClearsTheAlert() {
        assertEquals(listOf(QueueAlert.Clear("e")),
            QueueAlerts.detect(snapshot(called()), snapshot(called(confirmed = true)), "me"))
        assertEquals(listOf(QueueAlert.Clear("e")),
            QueueAlerts.detect(snapshot(called()), snapshot(called().copy(status = QueueStatus.SEATED)), "me"))
        assertEquals(listOf(QueueAlert.Clear("e")),
            QueueAlerts.detect(snapshot(called()), snapshot(called().copy(status = QueueStatus.LEFT)), "me"))
    }

    @Test fun leavingWhileWaitingDoesNotAlert() {
        val left = waiting.copy(status = QueueStatus.LEFT)
        assertTrue(QueueAlerts.detect(snapshot(waiting), snapshot(left), "me").isEmpty())
    }

    @Test fun reminderFiresOnlyInsideTheLastMinute() {
        val state = snapshot(called(at = 0L))
        assertNull(QueueAlerts.reminder(state, "me", 239_999L, emptySet()))
        val reminder = QueueAlerts.reminder(state, "me", 240_000L, emptySet())
        assertEquals(QueueAlert.Reminder("e", "Olive", 60L, "e:0"), reminder)
        assertEquals(1L, QueueAlerts.reminder(state, "me", 299_500L, emptySet())?.secondsLeft)
        assertNull(QueueAlerts.reminder(state, "me", 300_000L, emptySet()))
    }

    @Test fun reminderIsSentOncePerCall() {
        val state = snapshot(called(at = 0L))
        assertNull(QueueAlerts.reminder(state, "me", 250_000L, setOf("e:0")))
        val recalled = snapshot(called(at = 500_000L))
        assertEquals("e:500000", QueueAlerts.reminder(recalled, "me", 750_000L, setOf("e:0"))?.key)
    }

    @Test fun noReminderAfterConfirmationOrForShortGracePeriods() {
        assertNull(QueueAlerts.reminder(snapshot(called(confirmed = true)), "me", 250_000L, emptySet()))
        val short = snapshot(called(), settings = BusinessSettings(gracePeriodMinutes = 1))
        assertNull(QueueAlerts.reminder(short, "me", 30_000L, emptySet()))
    }
}
