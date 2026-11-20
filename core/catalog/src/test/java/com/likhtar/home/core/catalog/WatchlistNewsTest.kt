package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C4: новини закладок у дзвіночку — з'явився на сервісі, вийшов трейлер. */
class WatchlistNewsTest {
    private val dune = CatalogItem(tmdbId = 693134, kind = TitleKind.MOVIE, title = "Дюна", year = "2024")
    private fun snap(providers: Set<String> = emptySet(), trailers: Map<String, String> = emptyMap()) = TitleSnapshot(providers, trailers)

    @Test fun `перша перевірка тайтлу — лише знімок, без новин`() =
        assertTrue(titleNews(dune, old = null, now = snap(setOf("Netflix"), mapOf("k1" to "Trailer")), date = "2026-11-19").isEmpty())

    @Test fun `з'явився на сервісі`() = assertEquals(
        listOf(TitleNews(dune, TitleNews.Kind.SERVICE, "Netflix", "2026-11-19")),
        titleNews(dune, snap(setOf("Max")), snap(setOf("Max", "Netflix")), "2026-11-19"),
    )

    @Test fun `новий трейлер — з його назвою`() = assertEquals(
        listOf(TitleNews(dune, TitleNews.Kind.TRAILER, "Official Trailer 2", "2026-11-19")),
        titleNews(dune, snap(trailers = mapOf("k1" to "Trailer")), snap(trailers = mapOf("k1" to "Trailer", "k2" to "Official Trailer 2")), "2026-11-19"),
    )

    @Test fun `сервіс, що зник і повернувся, — не новина (збій мережі не дає хибного «тепер на Netflix»)`() {
        val known = merged(snap(setOf("Netflix")), snap())
        assertEquals(setOf("Netflix"), known.providers)
        assertTrue(titleNews(dune, known, snap(setOf("Netflix")), "2026-11-20").isEmpty())
    }

    @Test fun `ключі новин різні для сервісу й трейлера`() {
        val a = TitleNews(dune, TitleNews.Kind.SERVICE, "Netflix", "d").key
        val b = TitleNews(dune, TitleNews.Kind.TRAILER, "Netflix", "d").key
        assertTrue(a != b)
    }

    @Test fun `знімок і новини переживають запис і читання`() {
        val snaps = mapOf("movie:693134" to snap(setOf("Netflix"), mapOf("k1" to "T")))
        assertEquals(snaps, decodeSnapshots(encodeSnapshots(snaps)))
        val news = listOf(TitleNews(dune, TitleNews.Kind.TRAILER, "Official Trailer", "2026-11-19"))
        assertEquals(news.map { it.key }, decodeNews(encodeNews(news)).map { it.key })
    }

    @Test fun `старі новини випадають за 14 днів`() {
        val old = TitleNews(dune, TitleNews.Kind.SERVICE, "Netflix", "2026-11-01")
        val fresh = TitleNews(dune, TitleNews.Kind.SERVICE, "Max", "2026-11-18")
        assertEquals(listOf(fresh), freshNews(listOf(old, fresh), today = "2026-11-19"))
    }
}
