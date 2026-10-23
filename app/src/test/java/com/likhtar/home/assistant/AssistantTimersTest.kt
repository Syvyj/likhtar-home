package com.likhtar.home.assistant

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantTimersTest {
    private val now = ZonedDateTime.of(2026, 10, 20, 20, 15, 0, 0, ZoneId.of("Europe/Warsaw"))

    @Test
    fun `час сьогодні, вже минулий — завтра, через хвилини`() {
        assertEquals(45 * 60_000L, AssistantTimers.delayUntil("21:00", now))
        assertEquals((24 * 60 - 15) * 60_000L, AssistantTimers.delayUntil("20:00", now))
        assertEquals(30 * 60_000L, AssistantTimers.delayUntil("+30", now))
        assertEquals((60 + 45) * 60_000L, AssistantTimers.delayUntil("22", now))
        assertNull(AssistantTimers.delayUntil("ввечері", now))
        assertNull(AssistantTimers.delayUntil("25:00", now))
    }
}
