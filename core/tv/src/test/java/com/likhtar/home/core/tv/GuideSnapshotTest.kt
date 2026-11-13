package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Стара програма, яку показуємо, доки розбирається XMLTV: пересунута на «зараз» і збережена на диск. */
class GuideSnapshotTest {
    private val schedule = ChannelSchedule(
        live = NowOnAir(title = "Новини", image = null, startMillis = 0, endMillis = 100),
        upcoming = listOf(EpgProgramme("Кіно", 100, 200, "опис"), EpgProgramme("Спорт", 200, 300)),
    )

    @Test
    fun `передача змінилась — поточною стає наступна`() {
        val later = schedule.rolledTo(150)!!
        assertEquals("Кіно", later.live?.title)
        assertEquals(listOf("Спорт"), later.upcoming.map { it.title })
    }

    @Test
    fun `та сама хвилина — нічого не міняється, усе скінчилось — null`() {
        assertEquals("Новини", schedule.rolledTo(50)?.live?.title)
        assertNull(schedule.rolledTo(300))
    }

    @Test
    fun `кінець невідомий — іде, доки не почнеться наступна`() {
        val open = ChannelSchedule(live = NowOnAir(title = "Ефір", image = null, startMillis = 0, endMillis = 0), upcoming = listOf(EpgProgramme("Далі", 100, 0)))
        assertEquals("Ефір", open.rolledTo(50)?.live?.title)
        assertEquals("Далі", open.rolledTo(150)?.live?.title)
    }

    @Test
    fun `знімок — туди й назад, чужа адреса і сміття — порожньо`() {
        val text = encodeGuideSnapshot("http://epg/a.xml", mapOf("http://h/1" to schedule))
        val back = decodeGuideSnapshot(text, "http://epg/a.xml", now = 150)
        assertEquals("Кіно", back["http://h/1"]?.live?.title)
        assertEquals("опис", back["http://h/1"]?.live?.description)
        assertTrue(decodeGuideSnapshot(text, "http://epg/b.xml", 150).isEmpty())
        assertTrue(decodeGuideSnapshot("{", "http://epg/a.xml", 150).isEmpty())
        assertTrue(decodeGuideSnapshot(text, "http://epg/a.xml", 999).isEmpty())
    }
}
