package com.example.nextin.domain.queue

import com.example.nextin.data.model.*
import com.example.nextin.data.repository.LocalQueueRepository
import org.junit.Assert.*
import org.junit.Test

class ServerClockTest {
    private var device = 10_000L
    private val clock = ServerClock { device }

    @Test fun unsyncedClockUsesDeviceTime() {
        assertEquals(0L, clock.offsetMillis)
        assertEquals(10_000L, clock.now())
    }

    @Test fun syncUsesRoundTripMidpoint() {
        clock.sync(serverMillis = 50_000L, requestStartedAt = 10_000L, responseReceivedAt = 10_400L)
        assertEquals(39_800L, clock.offsetMillis)
        device = 20_000L
        assertEquals(59_800L, clock.now())
    }

    @Test fun deviceClockBehindServerIsCorrected() {
        clock.sync(serverMillis = 5_000L, requestStartedAt = 10_000L, responseReceivedAt = 10_000L)
        assertEquals(-5_000L, clock.offsetMillis)
        assertEquals(5_000L, clock.now())
    }

    @Test fun invalidRoundTripIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            clock.sync(serverMillis = 1L, requestStartedAt = 10L, responseReceivedAt = 9L)
        }
        assertEquals(0L, clock.offsetMillis)
    }

    @Test fun gracePeriodFollowsServerTimeWhenDeviceClockIsWrong() {
        val business = Business(id = "r", type = BusinessType.RESTAURANT, queueOpen = true,
            settings = BusinessSettings(maxPartySize = 4, gracePeriodMinutes = 2),
            tables = listOf(RestaurantTable("t", 4)))
        clock.sync(serverMillis = 1_000_000L, requestStartedAt = 10_000L, responseReceivedAt = 10_000L)
        val repo = LocalQueueRepository(listOf(business), nowMillis = clock::now)
        val entry = repo.join("r", "customer", "Alex", 2)
        val called = repo.callParty("r")
        assertEquals(1_000_000L, called.calledAtMillis)

        device += 60_000L
        assertEquals(60_000L, GracePeriod.remainingMillis(called, business, clock.now()))

        device += 60_000L
        repo.expireCalls(clock.now())
        assertEquals(QueueStatus.EXPIRED, repo.state.value.entries.single { it.id == entry.id }.status)
    }
}
