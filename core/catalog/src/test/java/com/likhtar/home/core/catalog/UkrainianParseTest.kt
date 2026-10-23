package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * У відповіді `/api/ukrainian` є позиції без справжнього `tmdb_id`, і підставити замість
 * нього `catalog_id` означало б піти в TMDB з чужим номером — тобто показати не той фільм.
 * Тест тримає саме цю межу.
 */
class UkrainianParseTest {

    private val body = """
        {"results":[
          {"catalog_id":1493123,"tmdb_id":1493123,"kind":"movie","title":"Кіллхаус",
           "original_title":"Кіллхаус","year":2026,"genres":["Бойовик","Драма"],"sources":3},
          {"catalog_id":900001,"kind":"tv","title":"Караоке на Майдані",
           "original_title":"Караоке на Майдані","year":2024,"sources":1},
          {"catalog_id":0,"kind":"movie","title":"Без id","year":2024}
        ]}
    """.trimIndent()

    @Test
    fun `позиція зі справжнім id читається повністю`() {
        val found = parseUkrainian(body).first()
        assertEquals(1493123, found.tmdbId)
        assertEquals(TitleKind.MOVIE, found.kind)
        assertEquals("2026", found.year)
        assertEquals(listOf("Бойовик", "Драма"), found.genres)
        assertEquals(3, found.sources)
    }

    @Test
    fun `без tmdb_id залишається порожньо, а не catalog_id`() {
        val found = parseUkrainian(body)[1]
        assertNull(found.tmdbId)
        assertEquals(900001, found.catalogId)
        assertEquals(TitleKind.TV, found.kind)
    }

    @Test
    fun `позиція без catalog_id відкидається`() {
        assertTrue(parseUkrainian(body).none { it.title == "Без id" })
    }

    @Test
    fun `порожня відповідь не падає`() {
        assertTrue(parseUkrainian("""{"results":[]}""").isEmpty())
    }
}
