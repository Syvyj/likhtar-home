package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ряди «Бо сподобалось «X»»: список вподобаного на приставці й картки ряду. */
class LikedRowsTest {
    private fun movie(id: Int) = CatalogItem(tmdbId = id, kind = TitleKind.MOVIE, title = "M$id", year = "2020")
    private fun show(id: Int) = CatalogItem(tmdbId = id, kind = TitleKind.TV, title = "S$id", year = "2020")

    @Test fun `like — на початок, повторний like не дублює`() {
        val once = updateLiked(emptyList(), movie(1), liked = true)
        val twice = updateLiked(updateLiked(once, movie(2), liked = true), movie(1), liked = true)
        assertEquals(listOf(1, 2), twice.map { it.tmdbId })
    }

    @Test fun `зняли like — геть зі списку`() =
        assertEquals(listOf(2), updateLiked(listOf(movie(1), movie(2)), movie(1), liked = false).map { it.tmdbId })

    @Test fun `фільм і серіал з одним id — різні`() =
        assertEquals(2, updateLiked(listOf(movie(7)), show(7), liked = true).size)

    @Test fun `список не росте без меж`() {
        var list = emptyList<CatalogItem>()
        repeat(40) { list = updateLiked(list, movie(it), liked = true) }
        assertEquals(LIKED_MAX, list.size)
        assertEquals(39, list.first().tmdbId)
    }

    @Test fun `ряд без самого тайтлу й без іншого вподобаного`() {
        val recs = listOf(movie(1), movie(2), movie(3), movie(4), movie(5), movie(6), movie(2))
        val items = likedRowItems(movie(1), recs, liked = listOf(movie(1), movie(3)))
        assertEquals(listOf(2, 4, 5, 6), items.map { it.tmdbId })
    }

    @Test fun `мало схожого — ряду немає`() =
        assertTrue(likedRowItems(movie(1), listOf(movie(2), movie(3)), liked = emptyList()).isEmpty())
}
