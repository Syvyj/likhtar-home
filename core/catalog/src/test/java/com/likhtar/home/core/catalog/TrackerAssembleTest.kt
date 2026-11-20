package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerAssembleTest {
    private fun schedule(id: Int, last: EpisodeRef?, next: EpisodeRef? = null, status: String = "Returning Series") =
        ShowSchedule(CatalogItem(tmdbId = id, kind = TitleKind.TV, title = "s$id", year = "2024"), status, last, next, mapOf(1 to 10, 2 to 10))

    private fun ep(s: Int, e: Int, d: String = "2026-11-14") = EpisodeRef(s, e, "", d)

    @Test fun `сортування за станом, кинутий — останній`() {
        val schedules = listOf(
            schedule(1, ep(1, 10), status = "Ended"),           // DONE
            schedule(2, ep(1, 5, "2026-08-01")),                // WATCHING (reached 1/2, давній хвіст)
            schedule(3, ep(1, 4), next = ep(1, 5, "2026-11-25")), // WAITING_EPISODE пізніше
            schedule(4, ep(1, 3)),                              // DROPPED
            schedule(5, ep(1, 3)),                              // NEW (reached 1/3)
            schedule(6, ep(1, 4), next = ep(1, 5, "2026-11-19")), // WAITING_EPISODE раніше
            schedule(7, ep(1, 10)),                             // WAITING_SEASON
        )
        val reached = mapOf(1 to (2 to 1), 2 to (1 to 2), 3 to (1 to 5), 4 to (1 to 2), 5 to (1 to 3), 6 to (1 to 5), 7 to (2 to 1))
        val marks = mapOf(4 to TitleMark("hide"))
        val ids = TrackerRepository.assemble(schedules, reached, marks, "2026-11-17").map { it.item.tmdbId }
        assertEquals(listOf(5, 2, 6, 3, 7, 1, 4), ids)
    }

    @Test fun `серіал без розкладу (TMDB не відповів) — відсутній, решта є`() {
        val shows = TrackerRepository.assemble(listOf(schedule(1, ep(1, 3))), mapOf(1 to (1 to 3), 9 to (1 to 1)), emptyMap(), "2026-11-17")
        assertEquals(listOf(1), shows.map { it.item.tmdbId })
    }

    @Test fun `порожньо — порожньо`() =
        assertEquals(emptyList<TrackedShow>(), TrackerRepository.assemble(emptyList(), emptyMap(), emptyMap(), "2026-11-17"))

    @Test fun `кеш свіжий лише до зміни позначок і двох годин`() {
        assertEquals(true, TrackerRepository.isFresh(at = 1_000, now = 2_000, marksChangedAt = 500))
        assertEquals(false, TrackerRepository.isFresh(at = 1_000, now = 2_000, marksChangedAt = 1_500))
        assertEquals(false, TrackerRepository.isFresh(at = 0, now = 2 * 60 * 60 * 1000L + 1, marksChangedAt = 0))
    }

    @Test fun `збій TMDB не кешується`() {
        assertEquals(true, TrackerRepository.cacheable(requested = 3, got = 3))
        assertEquals(true, TrackerRepository.cacheable(requested = 0, got = 0))
        assertEquals(false, TrackerRepository.cacheable(requested = 3, got = 2))
    }

    @Test fun `серед нових — найсвіжіша перша`() {
        val shows = TrackerRepository.assemble(
            listOf(schedule(1, ep(1, 3, "2026-11-10")), schedule(2, ep(1, 3, "2026-11-15"))),
            mapOf(1 to (1 to 3), 2 to (1 to 3)), emptyMap(), "2026-11-17",
        )
        assertEquals(listOf(2, 1), shows.map { it.item.tmdbId })
    }

    @Test fun `прем'єри без кинутих`() {
        val e = listOf(CalendarEvent("2026-11-20", CatalogItem(tmdbId = 1, kind = TitleKind.TV, title = "a", year = ""), EventKind.EPISODE),
            CalendarEvent("2026-11-20", CatalogItem(tmdbId = 2, kind = TitleKind.TV, title = "b", year = ""), EventKind.EPISODE))
        assertEquals(listOf(1), TrackerRepository.withoutDropped(e, setOf(2)).map { it.item.tmdbId })
    }

    @Test fun `кинуті, яких сервер уже не віддає, теж у трекері — без повторів`() {
        fun tv(id: Int) = CatalogItem(tmdbId = id, kind = TitleKind.TV, title = "", year = "")
        val ids = TrackerRepository.trackedItems(watching = listOf(tv(1)), saved = listOf(tv(1), tv(2)), dropped = listOf(tv(3), tv(2))).map { it.tmdbId }
        assertEquals(listOf(1, 2, 3), ids)
    }
}
