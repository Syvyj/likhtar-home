package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowScheduleTest {
    private val json = """
        {"id":42,"name":"Шоу","original_name":"Show","first_air_date":"2024-03-01","status":"Returning Series",
         "poster_path":"/p.jpg","backdrop_path":"/b.jpg",
         "last_episode_to_air":{"season_number":2,"episode_number":4,"name":"Four","air_date":"2026-11-14"},
         "next_episode_to_air":{"season_number":2,"episode_number":5,"name":"Five","air_date":"2026-11-21"},
         "seasons":[{"season_number":0,"episode_count":3},{"season_number":1,"episode_count":8},{"season_number":2,"episode_count":10}]}
    """.trimIndent()

    @Test fun `розклад із tv-відповіді`() {
        val s = parseShowSchedule(42, json)!!
        assertEquals("Returning Series", s.status)
        assertEquals(EpisodeRef(2, 4, "Four", "2026-11-14"), s.last)
        assertEquals(EpisodeRef(2, 5, "Five", "2026-11-21"), s.next)
        assertEquals(mapOf(1 to 8, 2 to 10), s.seasonSizes)
        assertEquals("Шоу", s.item.title)
        assertEquals(TitleKind.TV, s.item.kind)
    }

    @Test fun `немає next — null`() {
        val s = parseShowSchedule(42, json.replace(Regex("\"next_episode_to_air\":\\{[^}]*\\},"), "\"next_episode_to_air\":null,"))!!
        assertNull(s.next)
    }

    @Test fun `зламаний json — null`() = assertNull(parseShowSchedule(42, "{oops"))

    @Test fun `дати виходу фільму — лише країна, типи 3 і 4`() {
        val body = """{"results":[
          {"iso_3166_1":"US","release_dates":[{"type":4,"release_date":"2026-11-01T00:00:00.000Z"}]},
          {"iso_3166_1":"UA","release_dates":[
            {"type":1,"release_date":"2026-10-01T00:00:00.000Z"},
            {"type":3,"release_date":"2026-11-19T00:00:00.000Z"},
            {"type":4,"release_date":"2026-12-05T00:00:00.000Z"}]}]}"""
        assertEquals(listOf(3 to "2026-11-19", 4 to "2026-12-05"), parseReleaseDates(body, "UA"))
        assertEquals(emptyList<Pair<Int, String>>(), parseReleaseDates(body, "PL"))
    }
}
