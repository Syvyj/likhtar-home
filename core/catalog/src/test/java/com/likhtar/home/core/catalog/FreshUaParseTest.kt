package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Межа розбору `/api/fresh-ua`: у ряд не може потрапити позиція, яку нічим відкрити або
 * нічим упорядкувати. Обидва випадки трапляються в живій відповіді, тож перевіряємо їх,
 * а не вигадані.
 */
class FreshUaParseTest {

    private val body = """
        {"count":4,"results":[
          {"tmdb_id":1236045,"kind":"movie","title":"Тварини","year":2026,
           "online_date":"2026-10-09","audio":true,"subs":false,
           "services":["netflix"],"poster":"/p.jpg","backdrop":"/b.jpg",
           "genres":["Трилер","Кримінал"]},
          {"tmdb_id":314271,"kind":"tv","title":"Басс із машини","year":2026,
           "online_date":"2026-10-06","audio":true,"subs":false,
           "services":[],"poster":"","backdrop":"","genres":[]},
          {"tmdb_id":0,"kind":"movie","title":"Без TMDB","year":2026,
           "online_date":"2026-09-01"},
          {"tmdb_id":777,"kind":"movie","title":"Без дати","year":2026,
           "online_date":""}
        ]}
    """.trimIndent()

    @Test
    fun `позиції без id або без дати в ряд не потрапляють`() {
        val items = parseFreshUa(body)
        assertEquals(listOf(1236045, 314271), items.map { it.tmdbId })
    }

    @Test
    fun `дата появи українською стає датою свіжості картки`() {
        // Саме `availabilityDate`: за нею вже сортуються ряди Кінозалу, і окремого поля
        // під «коли з'явилось українською» заводити не треба.
        val first = parseFreshUa(body).first()
        assertEquals("2026-10-09", first.availabilityDate)
        assertEquals(TitleKind.MOVIE, first.kind)
        assertEquals("/p.jpg", first.posterPath)
        assertEquals(listOf("Трилер", "Кримінал"), first.genres)
    }

    @Test
    fun `порожні рядки не перетворюються на шлях до картинки`() {
        // Сервер віддає "" замість null, коли метаданих ще немає. Порожній рядок,
        // підставлений у URL, дав би запит по адресі виду .../w342 і биту картку.
        val tv = parseFreshUa(body)[1]
        assertTrue(tv.posterPath == null && tv.backdropPath == null)
    }
}
