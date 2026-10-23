package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceContinueParseTest {

    /** Помилка сервера — не порожній ряд: «сесія протухла» має долітати як помилка. */
    @Test
    fun `помилка не перетворюється на порожній список`() {
        val body = """{"error":"megogo: сторінка без ознак входу","results":[]}"""
        assertTrue(parseServiceContinue(body).isEmpty())
    }

    @Test
    fun `позиція сервісу стає карткою з діплінком`() {
        val body = """{"count":2,"results":[
            {"ext_id":"10246315","title":"Голі й налякані","poster":"https://s8.vcdn.biz/a.jpg",
             "deeplink":"megogo://video/10246315","catalog_id":-1087,"kind":"tv"},
            {"ext_id":"1","title":"Без діплінка","poster":"","deeplink":""}
        ]}"""
        val items = parseServiceContinue(body)
        // Позиція без діплінка в ряд не потрапляє: відкривати її нічим.
        assertEquals(1, items.size)
        assertEquals("Голі й налякані", items[0].title)
        assertEquals("megogo://video/10246315", items[0].deeplink)
        assertEquals("com.megogo.application", items[0].packageName)
        assertEquals("https://s8.vcdn.biz/a.jpg", items[0].imageUrl)
    }
}
