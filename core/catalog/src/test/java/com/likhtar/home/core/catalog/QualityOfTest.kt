package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Підписи взяті з живої відповіді агрегатора (заміряно 03.09.2026) — окремого поля
 * якості він не віддає, тому це єдине джерело на шляху кнопки «Дивитись».
 */
class QualityOfTest {

    @Test
    fun `бере роздільність із справжніх підписів`() {
        assertEquals(2160, qualityOf("🇺🇦 2160p · DV+HDR10+ · Remux · Дубляж · 84 ГБ · Торент"))
        assertEquals(1080, qualityOf("🇺🇦 1080p · Postmodern · Джерело"))
        assertEquals(720, qualityOf("🇺🇦 720p · Серія 11 · Джерело"))
    }

    @Test
    fun `не вигадує там, де числа немає або воно не про роздільність`() {
        // Підпис без роздільності — сервер тоді виведе якість із `ref`, і це нормально.
        assertEquals(0, qualityOf("🇺🇦 Серія 1 · Джерело 2"))
        assertEquals(0, qualityOf(""))
        // «5.6 ГБ» і рік не мають ставати роздільністю, а «12000p» — це не наш формат.
        assertEquals(0, qualityOf("Дюна 2021 · 5.6 ГБ · Торент"))
        assertEquals(0, qualityOf("12000p"))
    }
}

/**
 * Мова доріжки за підписом сервера.
 *
 * Правило тримає не косметику: на ньому стоїть рішення «вмикати саме чи спершу спитати».
 * Знімуть ворота «тільки українське» — і без цієї перевірки «Дивитись» тихо заграє чужою.
 */
class UkrainianAudioTest {
    @Test
    fun `прапорець сервера означає українську доріжку`() {
        assertTrue(StreamOption(label = "🇺🇦 2160p · HDR · Торент", url = "http://h/1").ukrainianAudio)
    }

    @Test
    fun `підпис без прапорця українською не вважається`() {
        assertFalse(StreamOption(label = "1080p · WEB-DL · Торент", url = "http://h/1").ukrainianAudio)
    }

    @Test
    fun `поле мови важить більше за прапорець у підписі`() {
        val option = StreamOption(label = "🇺🇦 1080p · Торент", url = "http://h/1", lang = "other")
        assertFalse(option.ukrainianAudio)
    }

    @Test
    fun `невідома мова українською не вважається`() {
        val option = StreamOption(label = "1080p · Торент", url = "http://h/1", lang = "unknown")
        assertFalse(option.ukrainianAudio)
    }

    @Test
    fun `підтверджене ukr вмикається без питання`() {
        assertTrue(StreamOption(label = "1080p · Торент", url = "http://h/1", lang = "ukr").ukrainianAudio)
    }

    @Test
    fun `clean label drops flag, shortens DV and empty separators`() {
        assertEquals("2160p · DV · Remux", cleanStreamLabel("$UA_FLAG 2160p · Dolby Vision ·  · Remux"))
        assertEquals("DV+HDR10+", cleanStreamLabel("DV+HDR10+"))
    }

    @Test
    fun `own TorrServer takes torrent links and the season playlist`() {
        val hash = "a".repeat(40)
        val link = "http://192.168.1.20:8090/stream/video?link=$hash&index=2&play"
        assertEquals("http://10.0.0.5:8090/stream/video?link=$hash&index=2&play", retargetTorrent(link, "http://10.0.0.5:8090"))
        assertEquals(link, retargetTorrent(link, null))
        assertEquals("https://cdn/x.m3u8", retargetTorrent("https://cdn/x.m3u8", "http://10.0.0.5:8090"))
        assertEquals(
            "http://s/api/playlist.m3u?serial=1&ts=http%3A%2F%2F10.0.0.5%3A8090",
            retargetTorrent("http://s/api/playlist.m3u?serial=1", "http://10.0.0.5:8090"),
        )
    }
}
