package com.likhtar.home.core.designsystem

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Пояс змінився посеред життя процесу — години мають піти за ним, а не лишитись старими. */
class ClockTimeTest {
    private val saved = TimeZone.getDefault()

    @After
    fun restore() = TimeZone.setDefault(saved)

    @Test
    fun followsTimeZoneChange() {
        // 21.10.2026 17:00 UTC: Київ (EEST) — 20:00, Варшава (CEST) — 19:00.
        val news = 1_792_602_000_000L
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        assertEquals("20:00", clockTime(news))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
        assertEquals("19:00", clockTime(news))
    }
}
