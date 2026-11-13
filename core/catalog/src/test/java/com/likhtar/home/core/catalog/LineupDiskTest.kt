package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.RecentChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class LineupDiskTest {
    @Test
    fun `lineup survives the disk without the portal account`() {
        val l = TvLineup(
            urls = listOf("http://a/1.m3u8", "stalker://p/1-2/video.m3u8"), names = listOf("A", "B"), now = listOf("x", "y"),
            index = 1, guide = "content://g", offset = 10, portal = "{secret}",
            channels = listOf(RecentChannel("http://a/1.m3u8", "A", "a.png", ""), RecentChannel("stalker://p/1-2/video.m3u8", "B")),
        )
        assertEquals(l.copy(now = listOf("", ""), portal = ""), decodeLineup(encodeLineup(l)))
    }
}
