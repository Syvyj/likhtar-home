package com.likhtar.home.core.tv

import com.likhtar.home.core.data.TvPortal
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Відповіді порталу Stalker (формат — як віддає справжній портал). */
class StalkerPortalTest {
    @Test
    fun `канали — номер в адресі, група, логотип і архів`() {
        val data = JSONArray("""[
            {"id":"221","cmd":"ffrt http:///ch/40803","name":"UA:Перший HD","tv_genre_id":"7","xmltv_id":"ua-pershy","logo":"221.png","tv_archive_duration":"120","enable_tv_archive":1},
            {"id":"222","cmd":"ffrt http:///ch/41176","name":"UA:Перший","tv_genre_id":"7","xmltv_id":"","logo":"","tv_archive_duration":"0","enable_tv_archive":0}
        ]""")
        val list = parsePortalChannels(data, mapOf("7" to "Україна"), "http://p/stalker_portal")
        assertEquals("stalker://p/221-40803/video.m3u8", list[0].url)
        assertEquals("40803", portalLink(list[0].url))
        assertEquals("Україна", list[0].category)
        assertEquals("http://p/stalker_portal/misc/logos/240/221.png", list[0].logo)
        assertNotNull(list[0].archive)
        assertEquals(5, list[0].archive?.days)
        assertNull(list[1].archive)
        assertEquals("221", portalId(list[0].url))
        assertNull(portalId("http://h/ch/index.m3u8"))
    }

    @Test
    fun `коротка програма — з позначок часу, без «⋗»`() {
        val a = JSONArray("""[{"name":"⋗ Суспільне Новини","start_timestamp":"1794564900","stop_timestamp":"1794567600","descr":""},{"name":"x","start_timestamp":"0","stop_timestamp":"0"}]""")
        val p = parseShortEpg(a)
        assertEquals(1, p.size)
        assertEquals("Суспільне Новини", p[0].title)
        assertEquals(1794564900_000L, p[0].startMillis)
    }

    @Test
    fun `адреса порталу без c і index`() {
        assertEquals("http://portal.example.com/stalker_portal", TvPortal.base("http://portal.example.com/stalker_portal/c/"))
        assertEquals("http://portal.example.com/stalker_portal", TvPortal.base("http://portal.example.com/stalker_portal/c/index.html"))
    }
}
