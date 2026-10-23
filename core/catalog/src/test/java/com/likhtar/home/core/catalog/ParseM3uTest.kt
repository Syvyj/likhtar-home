package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseM3uTest {
    @Test
    fun episodesFromSeasonPlaylist() {
        val body = """
            #EXTM3U
            #EXTINF:3600,S01E03 · Завтрашній день · 🇺🇦 2160p · Торент
            http://192.168.1.20:8090/stream/a.mkv?link=abc&index=3&play
            #EXTINF:-1,S01E04 · 🇺🇦 2160p · Торент
            http://192.168.1.20:8090/stream/a.mkv?link=abc&index=4&play
        """.trimIndent()
        val got = parseM3u(body)
        assertEquals(listOf(3, 4), got.map { it.episode })
        assertEquals("http://192.168.1.20:8090/stream/a.mkv?link=abc&index=4&play", got[1].url)
    }
}
