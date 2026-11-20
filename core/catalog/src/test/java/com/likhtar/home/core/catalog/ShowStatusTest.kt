package com.likhtar.home.core.catalog

import com.likhtar.home.core.catalog.ShowState.DONE
import com.likhtar.home.core.catalog.ShowState.DROPPED
import com.likhtar.home.core.catalog.ShowState.NEW
import com.likhtar.home.core.catalog.ShowState.NONE
import com.likhtar.home.core.catalog.ShowState.WAITING_EPISODE
import com.likhtar.home.core.catalog.ShowState.WAITING_SEASON
import com.likhtar.home.core.catalog.ShowState.WATCHING
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowStatusTest {
    private val item = CatalogItem(tmdbId = 7, kind = TitleKind.TV, title = "x", year = "2024")
    private fun ep(s: Int, e: Int, date: String = "2026-11-14") = EpisodeRef(s, e, "", date)

    private fun track(
        reached: Pair<Int, Int>? = null,
        mark: TitleMark? = null,
        last: EpisodeRef? = ep(2, 4),
        next: EpisodeRef? = null,
        status: String = "Returning Series",
    ) = trackShow(ShowSchedule(item, status, last, next, mapOf(1 to 8, 2 to 10, 3 to 8)), reached, mark, "2026-11-17")

    @Test fun `кинутий`() = assertEquals(DROPPED, track(reached = 2 to 2, mark = TitleMark("hide")).state)

    @Test fun `play - лише закладка, нічого не дивився, є next`() {
        val t = track(next = ep(2, 5, "2026-11-21"))
        assertEquals(WAITING_EPISODE, t.state)
        assertEquals("2026-11-21", t.date)
    }

    @Test fun `play - лише закладка, без next`() = assertEquals(NONE, track().state)

    @Test fun `вийшла одна наступна`() {
        val t = track(reached = 2 to 4)
        assertEquals(NEW, t.state)
        assertEquals(2 to 4, t.season to t.episode)
    }

    @Test fun `попереду кілька вийшлих, давно`() {
        val t = track(reached = 2 to 2, last = ep(2, 4, "2026-08-01"))
        assertEquals(WATCHING, t.state)
        assertEquals(1 to 10, t.watched to t.total)
        assertEquals(2 to 2, t.season to t.episode)
    }

    @Test fun `усе переглянуто, next є`() {
        val t = track(reached = 2 to 5, next = ep(2, 5, "2026-11-21"))
        assertEquals(WAITING_EPISODE, t.state)
        assertEquals("2026-11-21", t.date)
    }

    @Test fun `усе переглянуто, серіал триває`() =
        assertEquals(WAITING_SEASON, track(reached = 3 to 1, last = ep(2, 10)).state)

    @Test fun `усе переглянуто, серіал завершено`() =
        assertEquals(DONE, track(reached = 3 to 1, last = ep(2, 10), status = "Ended").state)

    @Test fun `позначка до S2E10, вийшов S3E1 - знову нова`() {
        val t = track(mark = TitleMark("watched", "upto", season = 2, episode = 10), last = ep(3, 1))
        assertEquals(NEW, t.state)
        assertEquals(3 to 1, t.season to t.episode)
    }

    @Test fun `прогрес і позначка - береться далі`() =
        assertEquals(NEW, track(reached = 1 to 3, mark = TitleMark("watched", "upto", season = 2, episode = 4), last = ep(2, 5)).state)

    @Test fun `позначка весь серіал - переглянуто`() =
        assertEquals(DONE, track(mark = TitleMark("watched", "all"), last = ep(2, 4)).state)

    @Test fun `сезон вийшов цілком нещодавно - нова, з наступної до перегляду`() {
        val t = track(reached = 1 to 1, last = ep(1, 8, "2026-11-14"))
        assertEquals(NEW, t.state)
        assertEquals(1 to 1, t.season to t.episode)
    }

    @Test fun `чекаю серію - несе останню вийшлу для «Переглянуто до»`() {
        val t = track(reached = 2 to 5, next = ep(2, 5, "2026-11-21"))
        assertEquals(2 to 4, t.lastSeason to t.lastEpisode)
    }

    @Test fun `нова несе дату виходу останньої серії`() =
        assertEquals("2026-11-14", track(reached = 2 to 4).date)
}
