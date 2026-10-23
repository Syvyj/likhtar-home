package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Пошук по нашому каталогу: портальні шоу TMDB-id не мають, тож інакше їх не знайти зовсім.
 */
class RealityMatchTest {

    private fun show(id: String, title: String) = RealityItem(
        id = id,
        title = title,
        genres = emptyList(),
        image = "",
    )

    private val rows = listOf(
        RealityRow("Кулінарія", listOf(show("teleportal:1", "МастерШеф"), show("teleportal:2", "Хто зверху?"))),
        RealityRow("Суто українське", listOf(show("teleportal:1", "МастерШеф"))),
    )

    @Test
    fun `збіг усередині назви теж знаходиться`() {
        assertEquals(listOf("Хто зверху?"), matches(rows, "зверху", 10).map { it.title })
    }

    @Test
    fun `одна позиція з двох рядів приходить один раз`() {
        assertEquals(1, matches(rows, "мастершеф", 10).size)
    }

    @Test
    fun `початок назви стоїть вище за збіг усередині`() {
        val rows = listOf(
            RealityRow("Усе", listOf(show("a", "Останній герой"), show("b", "Танці з зірками"))),
        )
        assertEquals(listOf("Танці з зірками", "Останній герой"), matches(rows, "тан", 10).map { it.title })
    }

    @Test
    fun `один символ не шукаємо`() {
        assertEquals(emptyList<RealityItem>(), matches(rows, "м", 10))
    }
}
