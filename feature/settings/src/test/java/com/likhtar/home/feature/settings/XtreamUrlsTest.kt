package com.likhtar.home.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamUrlsTest {
    @Test
    fun urls() {
        val want = "http://tv.example:8080/get.php?username=u&password=p%26q&type=m3u_plus&output=m3u8" to
            "http://tv.example:8080/xmltv.php?username=u&password=p%26q"
        assertEquals(want, xtreamUrls("http://tv.example:8080", "u", "p&q"))
        assertEquals(want, xtreamUrls("http://tv.example:8080/", "u", "p&q"))
        assertEquals(want, xtreamUrls("http://tv.example:8080/player_api.php?username=x", "u", "p&q"))
        assertNull(xtreamUrls("tv.example", "u", "p"))
        assertNull(xtreamUrls("http://tv.example", "", "p"))
    }
}
