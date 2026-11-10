package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelArchiveTest {
    private val start = 1_794_046_882_000L
    private val end = start + 30 * 60 * 1000

    @Test
    fun `flussonic mpegts — архів шматком із токеном`() {
        val a = ChannelArchive("flussonic", 5, "")
        assertEquals(
            "http://h/lb0/NTN/archive-1794046882-1800.ts?token=x",
            a.url("http://h/lb0/NTN/mpegts?token=x", start, end),
        )
    }

    @Test
    fun `flussonic hls`() {
        assertEquals(
            "http://h/ch/index-1794046882-1800.m3u8",
            ChannelArchive("flussonic", 3, "").url("http://h/ch/index.m3u8", start, end),
        )
    }

    @Test
    fun `шаблон catchup-source`() {
        assertEquals(
            "http://h/ch/mpegts?utc=1794046882&dur=1800",
            ChannelArchive("default", 3, "http://h/ch/mpegts?utc={utc}&dur={duration}").url("http://h/ch/mpegts", start, end),
        )
    }

    @Test
    fun `невідома схема — нічого`() {
        assertNull(ChannelArchive("flussonic", 3, "").url("http://h/ch/stream", start, end))
    }

    @Test
    fun `без днів архіву немає`() {
        assertNull(ChannelArchive.of("flussonic", 0, ""))
    }
}
