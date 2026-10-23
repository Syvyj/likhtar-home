package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronologyMatchTest {

    @Test
    fun `рік у межах допуску приймається`() {
        val entry = ChronologyEntry("Alien", 1979)

        assertTrue(ChronologyMatch.accepts(entry, movie(1, "Чужий", "1979")))
        assertTrue(ChronologyMatch.accepts(entry, movie(1, "Чужий", "1981")))
    }

    @Test
    fun `фільм іншого десятиліття відкидається`() {
        val entry = ChronologyEntry("Alien", 1979)

        assertFalse(ChronologyMatch.accepts(entry, movie(2, "Alien", "1990")))
    }

    @Test
    fun `позиція без року приймає будь-що`() {
        assertTrue(ChronologyMatch.accepts(ChronologyEntry("Iron Man", 0), movie(3, "Залізна людина", "")))
    }

    @Test
    fun `позиція з роком не приймає фільм без дати`() {
        assertFalse(ChronologyMatch.accepts(ChronologyEntry("Iron Man", 2008), movie(3, "Залізна людина", "")))
    }

    @Test
    fun `порядок хронології зберігається, ненайдене й повтори зникають`() {
        val entries = listOf(
            ChronologyEntry("Captain America: The First Avenger", 2011),
            ChronologyEntry("Немає такого фільму", 1999),
            ChronologyEntry("Iron Man", 2008),
            ChronologyEntry("Iron Man (той самий)", 2008),
            ChronologyEntry("The Avengers", 2012),
        )
        val found = listOf(
            movie(1771, "Перший мститель", "2011"),
            null,
            movie(1726, "Залізна людина", "2008"),
            movie(1726, "Залізна людина", "2008"),
            movie(24428, "Мстителі", "2012"),
        )

        val result = ChronologyMatch.assemble(entries, found)

        assertEquals(listOf(1771, 1726, 24428), result.map { it.tmdbId })
    }

    @Test
    fun `помилковий збіг за назвою не потрапляє в список`() {
        val entries = listOf(ChronologyEntry("Thor", 2011))
        val found = listOf(movie(999, "Тор інший", "1974"))

        assertTrue(ChronologyMatch.assemble(entries, found).isEmpty())
    }

    private fun movie(id: Int, title: String, year: String) =
        CatalogItem(tmdbId = id, kind = TitleKind.MOVIE, title = title, year = year)
}
