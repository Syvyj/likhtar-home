package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionRowsParseTest {

    private val body = """
        {"section":"reality","rows":[
          {"title":"Кулінарія","items":[
            {"catalog_id":-1183,"kind":"tv","title":"ChefMar","year":0,
             "ext_key":"megogo:23085416","poster":"https://s8.vcdn.biz/a.jpg","backdrop":"",
             "genres":["Кулінарія"],"date":"2026-09-18","audio":"uk","open":"service"},
            {"catalog_id":1083433,"tmdb_id":1083433,"kind":"movie","title":"Смак",
             "year":2025,"poster":"/p.jpg","backdrop":"/b.jpg","genres":[],
             "date":"2026-09-01","audio":"ru","open":"tmdb"},
            {"catalog_id":0,"kind":"tv","title":"Без id","date":"2026-09-01"},
            {"catalog_id":-7,"kind":"tv","title":"Без дати","date":""}
          ]},
          {"title":"Порожній","items":[]}
        ]}
    """.trimIndent()

    @Test
    fun `ряд приїжджає з тим, чого немає в самій картці`() {
        val rows = parseRows(body)
        // Порожній ряд — не ряд: малювати заголовок над нічим немає сенсу.
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("Кулінарія", row.title)

        // Позиція без id або без дати в ряд не заходить: відкривати нічим, сортувати нема за чим.
        assertEquals(2, row.items.size)

        val service = row.items.first()
        assertEquals(-1183, service.tmdbId)
        assertEquals("service", row.open[-1183])
        assertEquals("megogo:23085416", row.extKeys[-1183])
        // Повна адреса постера лишається як є — її не можна клеїти до image.tmdb.org.
        assertTrue(service.posterUrlIsAbsolute())
        assertEquals("2026-09-18", service.availabilityDate)

        // Мова доріжки — мітка на картці, тож RU має приїхати окремим переліком.
        assertTrue(row.russian.contains(1083433))
        assertTrue(!row.russian.contains(-1183))
        assertEquals("tmdb", row.open[1083433])
        assertNull(row.extKeys[1083433])
    }

    @Test
    fun `той самий тайтл двічі в ряд не заходить`() {
        val twice = """
            {"rows":[{"title":"Нове","items":[
              {"catalog_id":306762,"tmdb_id":306762,"kind":"tv","title":"А","date":"2026-10-01"},
              {"catalog_id":306762,"tmdb_id":306762,"kind":"tv","title":"А","date":"2026-10-02"}
            ]}]}
        """.trimIndent()
        val items = parseRows(twice).single().items
        assertEquals(listOf("tv-306762"), items.map { it.id })
    }

    private fun CatalogItem.posterUrlIsAbsolute(): Boolean =
        posterPath?.startsWith("http") == true
}

class PortalShowIdTest {
    @org.junit.Test
    fun portalCardsOpenTheShowPage() {
        org.junit.Assert.assertEquals("serials/stb/likari", portalShowId("portal", "teleportal:stb/likari", TitleKind.TV))
        org.junit.Assert.assertEquals("plus1/100807", portalShowId("portal", "1plus1:100807", TitleKind.TV))
        org.junit.Assert.assertEquals("", portalShowId("tmdb", "", TitleKind.MOVIE))
    }

    @Test
    fun genresCarryBackdropPool() {
        val genres = parseGenres(
            """{"genres":[{"key":"drama","title":"Драма","color":"#2F6FB0","backdrop":"/a.jpg","backdrops":["/a.jpg","","/b.jpg"]},
               {"key":"comedy","title":"Комедія","color":"#1FA8A0","backdrop":"/c.jpg"}]}""",
        )
        assertEquals(listOf("/a.jpg", "/b.jpg"), genres[0].backdrops)
        assertTrue(genres[1].backdrops.isEmpty())
    }
}
