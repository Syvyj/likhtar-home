package com.likhtar.home.feature.search

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ContinueItem
import com.likhtar.home.core.catalog.ShowState
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TrackedShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroPickTest {
    private fun item(id: Int) = CatalogItem(tmdbId = id, kind = TitleKind.TV, title = "t$id", year = "2026")
    private val newShow = TrackedShow(item(1), ShowState.NEW, 1, 3)
    private val waiting = TrackedShow(item(2), ShowState.WAITING_SEASON)
    private val cont = ContinueItem(5, TitleKind.TV, "c", "c", "2026", 1, 2, "", 40)
    private val advice = (10..14).map { item(it) to "Бо вам сподобалась «X»" }

    @Test fun `нова серія — герой`() {
        val h = pickHero(listOf(waiting, newShow), listOf(cont), advice, "2026-11-17")
        assertEquals(HeroPick.NewEpisode(newShow), h)
    }

    @Test fun `немає нової — продовжити`() =
        assertEquals(HeroPick.Continue(cont), pickHero(listOf(waiting), listOf(cont), advice, "2026-11-17"))

    @Test fun `нічого — порада, та сама весь день`() {
        val a = pickHero(emptyList(), emptyList(), advice, "2026-11-17")
        val b = pickHero(emptyList(), emptyList(), advice, "2026-11-17")
        assertTrue(a is HeroPick.Advice)
        assertEquals(a, b)
        val other = (1..10).map { pickHero(emptyList(), emptyList(), advice, "2026-11-${17 + it}") }.toSet()
        assertTrue(other.size > 1)
    }

    @Test fun `зовсім порожньо — null`() = assertNull(pickHero(emptyList(), emptyList(), emptyList(), "2026-11-17"))

    @Test fun `з кількох нових — найсвіжіша`() {
        val older = TrackedShow(item(7), ShowState.NEW, 1, 2, date = "2026-11-10")
        val newer = TrackedShow(item(8), ShowState.NEW, 1, 2, date = "2026-11-15")
        assertEquals(HeroPick.NewEpisode(newer), pickHero(listOf(older, newer), emptyList(), advice, "2026-11-17"))
    }

    @Test fun `«Подивились?» — лише якщо виходили із застосунку після запуску`() {
        assertTrue(shouldAskWatched(startedAt = 100, stoppedAt = 200))
        assertTrue(!shouldAskWatched(startedAt = 100, stoppedAt = 50))
        assertTrue(!shouldAskWatched(startedAt = 0, stoppedAt = 200))
    }
}
