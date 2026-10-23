package com.likhtar.home.core.catalog

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAllSectionsTest {
    private val today = LocalDate.of(2026, 10, 14)
    private fun item(id: Int, note: String = "", kind: TitleKind = TitleKind.MOVIE) =
        CatalogItem(id, kind, "t$id", year = "2026", note = note)

    private val names = mapOf(
        OpenAllPart.THIS_WEEK to "Цього тижня", OpenAllPart.LAST_WEEK to "Минулого тижня",
        OpenAllPart.EARLIER to "Раніше", OpenAllPart.MONTH_END to "До кінця місяця",
        OpenAllPart.LATER to "Пізніше", OpenAllPart.MOVIES to "Фільми", OpenAllPart.SERIES to "Серіали",
    )

    private fun row(vararg items: CatalogItem) =
        CatalogRow(id = "r", title = "Ряд", source = "server", path = "", params = emptyMap(), items = items.toList())

    @Test
    fun newSplitsByWeek() {
        val parts = openAllSections(row(item(1, "13.10"), item(2, "Netflix · 05.10"), item(3, "20.09")), today, names::getValue)
        assertEquals(listOf("Цього тижня", "Минулого тижня", "Раніше"), parts.map { it.title })
        assertEquals(RowLayout.FEATURED, parts.first().layout)
    }

    @Test
    fun expiringSplitsAhead() {
        val parts = openAllSections(row(item(1, "до 16.10"), item(2, "до 28.10"), item(3, "до 05.11")), today, names::getValue)
        assertEquals(listOf("Цього тижня", "До кінця місяця", "Пізніше"), parts.map { it.title })
    }

    @Test
    fun undatedSplitsByKind() {
        val parts = openAllSections(row(item(1), item(2, kind = TitleKind.TV)), today, names::getValue)
        assertEquals(listOf("Фільми", "Серіали"), parts.map { it.title })
    }
}
