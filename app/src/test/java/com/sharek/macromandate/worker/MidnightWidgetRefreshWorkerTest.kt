package com.sharek.macromandate.worker

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MidnightWidgetRefreshWorkerTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun usesLocalMidnightIncludingTheRolloverBuffer() {
        val now = Instant.parse("2026-11-21T23:00:02Z")
        val refresh = MidnightWidgetRefreshWorker.nextRefresh(now, berlin)
        assertEquals(Instant.parse("2026-11-21T23:00:05Z"), refresh.toInstant())
    }

    @Test
    fun followsLocalDaysAcrossBothDaylightSavingTransitions() {
        val spring = Instant.parse("2026-03-28T23:00:05Z")
        val autumn = Instant.parse("2026-10-24T22:00:05Z")

        assertEquals(
            Duration.ofHours(23),
            Duration.between(spring, MidnightWidgetRefreshWorker.nextRefresh(spring, berlin).toInstant())
        )
        assertEquals(
            Duration.ofHours(25),
            Duration.between(autumn, MidnightWidgetRefreshWorker.nextRefresh(autumn, berlin).toInstant())
        )
    }
}
