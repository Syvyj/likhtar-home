package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Відповідь `/api/ua` тримає три стани, і сплутати «немає» з «не знаємо» — це намалювати
 * мітку там, де підстав немає. Тест ловить саме цю межу.
 */
class UaAudioParseTest {

    private val body = """
        {"asked":3,"results":{
          "120":{"ua":"yes","basis":"source","services":["sweet.tv","megogo"],
                 "other_services":["itunes","max"],"voices":["Дубляж","1+1"]},
          "550":{"ua":"no"}
        }}
    """.trimIndent()

    @Test
    fun `тайтл з доріжкою читається разом із сервісами й озвучками`() {
        val found = parseUaAudio(body).getValue(120)
        assertTrue(found.ua)
        assertEquals(listOf("sweet.tv", "megogo"), found.services)
        assertEquals(listOf("Дубляж", "1+1"), found.voices)
    }

    @Test
    fun `сервіси наявності лежать окремо від сервісів зі звуком`() {
        assertEquals(listOf("itunes", "max"), parseUaAudio(body).getValue(120).otherServices)
    }

    @Test
    fun `явне ні — це не мітка`() {
        assertEquals(false, parseUaAudio(body).getValue(550).ua)
    }

    @Test
    fun `про кого сервер промовчав — того в мапі немає`() {
        assertNull(parseUaAudio(body)[27205])
    }

    @Test
    fun `порожня відповідь не падає`() {
        assertTrue(parseUaAudio("""{"asked":2,"results":{}}""").isEmpty())
    }
}

/**
 * Підпис озвучки мусить казати те, що ми справді знаємо: «мова не вказана» — це не «без
 * української», а здогад мусить бути видним, бо джерела в ньому від сусідньої картки.
 */
class VoiceLabelTest {

    @Test
    fun `невідома мова підписана нейтрально`() {
        val group = VoiceGroup("Дубляж", 1080, "", 1, listOf("ref"), lang = "unknown")
        assertTrue(group.label.contains("мова не вказана"))
        assertTrue("не вигадуємо відсутності української", !group.label.contains("без"))
    }

    @Test
    fun `українська окремого підпису не має`() {
        val group = VoiceGroup("Дубляж", 1080, "", 1, listOf("ref"), lang = "ukr")
        assertEquals("Дубляж · 1080p", group.label)
    }

    @Test
    fun `здогад видно в підписі`() {
        val group = VoiceGroup("Дубляж", 0, "", 1, listOf("ref"), guess = true)
        assertTrue(group.label.contains("здогад"))
    }
}
