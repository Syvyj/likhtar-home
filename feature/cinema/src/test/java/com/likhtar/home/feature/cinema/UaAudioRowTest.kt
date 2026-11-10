package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Правило ряду: у ньому лишається те, що можна ввімкнути.
 *
 * Тест тримає рівно ті три межі, на яких раніше ламалась видача: озвучка не є ключем
 * сортування, непідтверджене не заходить із вимкненим налаштуванням, і навіть з
 * увімкненим воно не розбавляє ряд, якому вистачає власних позицій.
 */
class UaAudioRowTest {

    private fun item(id: Int, date: String) = CatalogItem(
        tmdbId = id,
        kind = TitleKind.MOVIE,
        title = "Фільм $id",
        year = date.take(4),
        releaseDate = date,
    )

    private fun titles(items: List<CatalogItem>) = items.map { it.tmdbId }

    private fun row(
        items: List<CatalogItem>,
        confirmed: Set<Int>,
        fill: Boolean,
    ) = uaAudioRow(
        items = items,
        confirmedUaIds = confirmed,
        freshness = { it.releaseDate },
        fillWithUnconfirmed = fill,
    )

    @Test
    fun `ряд іде за датою, а не за озвучкою`() {
        // Саме той випадок, на який був баг: перевірена позиція 2019 року
        // стояла перед учорашнім релізом, бо озвучка була ПЕРШИМ ключем сортування.
        val items = listOf(item(1, "2019-01-01"), item(2, "2026-09-17"), item(3, "2026-08-01"))
        assertEquals(listOf(2, 3, 1), titles(row(items, setOf(1, 2, 3), fill = false)))
    }

    @Test
    fun `без озвучки в ряд не потрапляє`() {
        val items = listOf(item(1, "2026-09-17"), item(2, "2026-09-16"))
        assertEquals(listOf(2), titles(row(items, confirmed = setOf(2), fill = false)))
    }

    @Test
    fun `увімкнене налаштування лишає каталог як є — без відсіву й пересортування`() {
        val items = listOf(item(1, "2026-09-10"), item(2, "2026-09-17"), item(3, "2026-09-20"))
        assertEquals(listOf(1, 2, 3), titles(row(items, confirmed = setOf(2), fill = true)))
    }

    @Test
    fun `повний ряд не обрізається`() {
        // Шість і більше підтверджених більше не ховають решту — «Фантастика» з
        // семи карток за сто років кіно.
        val confirmed = (1..7).map { item(it, "2026-09-0$it") }
        val rest = listOf(item(99, "2026-09-30"))
        val ids = confirmed.map { it.tmdbId }.toSet()
        assertEquals(8, row(confirmed + rest, ids, fill = true).size)
    }
}
