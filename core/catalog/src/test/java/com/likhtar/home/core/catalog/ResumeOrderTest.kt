package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeOrderTest {
    private fun ours(title: String, at: Long) =
        ContinueItem(1, TitleKind.MOVIE, title, title, "2020", 0, 0, "", 0, updatedAt = at)

    private fun mg(title: String, at: Long, show: Boolean = false) =
        ServiceResume(title, null, "megogo://video/1", "pkg", updatedAt = at, show = show)

    @Test
    fun oneTimelineShowsAside() {
        val cards = resumeCards(
            watching = listOf(ours("Зникла", 300), ours("Матерія", 100)),
            services = listOf(mg("Дім дракона", 200), mg("Royal Cooking", 250, show = true), mg("Старе", 0), mg("Ще старше", 0)),
            subtitle = { it.year },
        )
        assertEquals(listOf("Зникла", "Дім дракона", "Матерія", "Старе", "Ще старше"), cards.map { it.title })
        val reality = showResumeCards(emptyList(), services = listOf(mg("Дім дракона", 200), mg("Royal Cooking", 250, show = true)), subtitle = { it.year })
        assertEquals(listOf("Royal Cooking"), reality.map { it.title })
    }

    @Test
    fun episodeLabelTakesTextsFromCaller() {
        val next = ContinueItem(1, TitleKind.TV, "Шоу", "Show", "2026", 2, 7, "Назва", 0, nextAirDate = "2026-10-08", newEpisode = true)
        assertEquals("NEW · S02E07 · on ${airDate("2026-10-08")}", next.episodeLabel("NEW") { "on $it" })
        assertEquals("S02E07 · Назва", next.copy(nextAirDate = "", newEpisode = false).episodeLabel("NEW") { it })
        assertEquals("8 Oct", airDate("2026-10-08", java.util.Locale.ENGLISH))
        assertEquals(null, airDate(""))
    }
}
