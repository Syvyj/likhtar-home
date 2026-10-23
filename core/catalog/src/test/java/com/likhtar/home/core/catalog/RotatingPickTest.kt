package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Шапка сторінки: стала протягом години, різна між годинами, лише з кадром. */
class RotatingPickTest {
    private val items = (1..12).map { CatalogItem(tmdbId = it, kind = TitleKind.MOVIE, title = "t$it", year = "", backdropPath = "/b$it.jpg") } +
        CatalogItem(tmdbId = 99, kind = TitleKind.MOVIE, title = "без кадру", year = "")
    private val hour = 3_600_000L

    @Test
    fun `у межах години — той самий кадр`() {
        val base = 1_000L * hour
        assertEquals(rotatingPick("netflix", items, base), rotatingPick("netflix", items, base + hour - 1))
    }

    @Test
    fun `за добу шапка міняється і не бере позицію без кадру`() {
        val picks = (0 until 24).map { rotatingPick("netflix", items, (1_000L + it) * hour)!! }
        assertTrue(picks.map { it.id }.toSet().size > 3)
        assertTrue(picks.none { it.backdropPath == null })
    }

    @Test
    fun `без кадрів — нічого`() {
        assertNull(rotatingPick("x", listOf(CatalogItem(tmdbId = 1, kind = TitleKind.MOVIE, title = "t", year = ""))))
    }
}
