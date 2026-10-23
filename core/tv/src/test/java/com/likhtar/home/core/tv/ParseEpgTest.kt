package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Форма відповіді взята з живої ручки (`/api/epg`, заміряно 04.09.2026). */
class ParseEpgTest {

    private fun payload(stale: String) = """
        {"source":"megogo", $stale
         "results":{"5866605":{
           "live":{"title":"«Ваш шеф», епізод 5","start":1788485400,"end":1788486000,
                   "image":"https://example/one.jpg"},
           "ends_in":95,
           "upcoming":[{"title":"Три в одному","start":1788486000,"end":1788489600}]}}}
    """.trimIndent()

    @Test
    fun `час приходить у секундах`() {
        val live = parseEpg(payload("")).getValue("5866605").live!!
        // 1788485400 с — це 1788485400000 мс. Помилка на 1000× тиха: сітка просто порожня.
        assertEquals(1_788_485_400_000L, live.startMillis)
        assertEquals(1_788_486_000_000L, live.endMillis)
    }

    @Test
    fun `позначка кешу гасне, коли приїхав свіжий розпис`() {
        val stale = parseEpg(payload(""""stale":["5866605"],""")).getValue("5866605")
        assertTrue("сервер сказав, що це з кешу", stale.live!!.stale)

        // Та сама відповідь без `stale` — позначка мусить ЗНИКНУТИ. Позначка, яка не
        // гасне, гірша за відсутню: вона привчає її ігнорувати.
        val fresh = parseEpg(payload("")).getValue("5866605")
        assertFalse(fresh.live!!.stale)
    }

    @Test
    fun `розпис живе без «зараз»`() {
        val body = """
            {"results":{"1":{"upcoming":[{"title":"Далі","start":1788486000,"end":1788489600}]}}}
        """.trimIndent()
        val schedule = parseEpg(body).getValue("1")
        assertNull("поточної передачі сервер не знає", schedule.live)
        assertEquals("а розпис усе одно є", 1, schedule.upcoming.size)
    }

    @Test
    fun `порожня й поламана відповідь не падають`() {
        assertTrue(parseEpg("""{"results":{}}""").isEmpty())
        assertTrue(parseEpg("{}").isEmpty())
    }
}
