package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventsTest {
    private val show = CatalogItem(tmdbId = 1, kind = TitleKind.TV, title = "Б", year = "2024")
    private val movie = CatalogItem(tmdbId = 2, kind = TitleKind.MOVIE, title = "А", year = "2026")
    private fun tracked(state: ShowState = ShowState.WATCHING) = TrackedShow(show, state, 1, 3)
    private fun episode(n: Int, date: String) = Episode(number = n, title = "", airDate = date)

    @Test fun `тиждень з понеділка`() {
        assertEquals("2026-11-16", weekDays("2026-11-22").first()) // неділя
        assertEquals(7, weekDays("2026-11-22").size)
        assertEquals("2026-11-22", weekDays("2026-11-22").last())
    }

    @Test fun `понеділок - свій тиждень`() = assertEquals("2026-11-23", weekDays("2026-11-23").first())

    @Test fun `вікно -7 +30 включно`() = assertEquals("2026-11-10" to "2026-12-17", window("2026-11-17"))

    @Test fun `серії поза вікном відкинуто, на краю - лишаються`() {
        val eps = listOf(episode(1, "2026-11-09"), episode(2, "2026-11-10"), episode(3, "2026-12-17"), episode(4, "2026-12-18"), episode(5, ""))
        val events = episodeEvents(tracked(), eps, season = 1, from = "2026-11-10", to = "2026-12-17")
        assertEquals(listOf(2, 3), events.map { it.episode })
        assertTrue(events.all { it.kind == EventKind.EPISODE && it.season == 1 })
    }

    @Test fun `кинутий серіал - жодної події`() =
        assertEquals(emptyList<CalendarEvent>(), episodeEvents(tracked(ShowState.DROPPED), listOf(episode(2, "2026-11-12")), 1, "2026-11-10", "2026-12-17"))

    @Test fun `фільм - тип 4 онлайн, тип 3 у кіно, інші й поза вікном ігнор`() {
        val events = movieEvents(movie, listOf(3 to "2026-11-19", 4 to "2026-12-05", 4 to "2027-01-01", 1 to "2026-11-20"), "2026-11-10", "2026-12-17")
        assertEquals(listOf(EventKind.CINEMA to "2026-11-19", EventKind.ONLINE to "2026-12-05"), events.map { it.kind to it.date })
    }

    @Test fun `byDay - дні за датою, у дні серії перед фільмами`() {
        val e = listOf(
            CalendarEvent("2026-11-20", movie, EventKind.ONLINE),
            CalendarEvent("2026-11-18", show, EventKind.EPISODE, 1, 2),
            CalendarEvent("2026-11-20", show, EventKind.EPISODE, 1, 3),
        )
        val days = byDay(e)
        assertEquals(listOf("2026-11-18", "2026-11-20"), days.map { it.first })
        assertEquals(listOf(EventKind.EPISODE, EventKind.ONLINE), days[1].second.map { it.kind })
    }
}
