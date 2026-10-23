package com.likhtar.home.core.catalog

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Що лаунчер віддає зовнішньому плеєру: ключі extras у кожного плеєра свої. */
class PlayerPlanTest {

    private val show = CatalogItem(tmdbId = 1399, kind = TitleKind.TV, title = "Шьоґун", year = "2024", posterPath = "/p.jpg")
    private val queue = listOf(
        QueueEntry(1, "S01E01 · Анджін · 1080p", "http://s/1.mkv"),
        QueueEntry(2, "S01E02 · Слуги · 1080p", "http://s/2.mkv"),
    )
    private val request = PlayRequest(
        uri = "http://s/season.m3u", mime = "audio/x-mpegurl", packageName = "com.brouken.player",
        resumeMs = 452_272, title = PlayingTitle(show, season = 1, episode = 2),
        single = "http://s/2.mkv", queue = queue,
        variants = listOf("UA" to "http://s/2-ua.mkv", "EN" to "http://s/2-en.mkv"),
    )

    @Test
    fun `плеєр без m3u — серія й черга video_list`() {
        val plan = request.playerPlan(takesM3u = false, companion = mapOf("likhtar.profile" to "p1"))
        assertTrue(plan.asQueue)
        assertEquals("http://s/2.mkv", plan.data)
        assertEquals("video/*", plan.mime)
        assertEquals(PlayerPlan.UriList(listOf("http://s/1.mkv", "http://s/2.mkv")), plan.extras["video_list"])
        assertArrayEquals(arrayOf("1", "2"), plan.extras["video_list.episode"] as Array<*>)
        // Варіанти поточної серії — на її індексі в черзі, а не загальним списком.
        assertArrayEquals(arrayOf("UA", "EN"), plan.extras["video_list.quality_levels.1"] as Array<*>)
        assertNull(plan.extras["quality_levels"])
        assertEquals("p1", plan.extras["likhtar.profile"])
        assertEquals("tv", plan.extras["likhtar.kind"])
    }

    @Test
    fun `плеєр з m3u — плейлист сезону з його mime`() {
        val plan = request.playerPlan(takesM3u = true, companion = emptyMap())
        assertFalse(plan.asQueue)
        assertEquals("http://s/season.m3u", plan.data)
        assertEquals("audio/x-mpegurl", plan.mime)
        assertNull(plan.extras["video_list"])
        assertArrayEquals(arrayOf("UA", "EN"), plan.extras["quality_levels"] as Array<*>)
    }

    @Test
    fun `позиція, назва й результат — ключі всіх плеєрів`() {
        val plan = request.playerPlan(takesM3u = true, companion = emptyMap())
        assertEquals(452_272, plan.extras["position"])
        assertEquals(452_272L, plan.extras["extra_position"])
        assertEquals(452_272, plan.extras["startfrom"])
        assertEquals("Шьоґун · S1E2", plan.extras["title"])
        assertEquals("Шьоґун · S1E2", plan.extras["forcename"])
        assertEquals("1399", plan.extras["id"])
        assertEquals(true, plan.extras["return_result"])
    }

    @Test
    fun `випуск порталу — назва шоу й випуску, потік як відео`() {
        val portal = PlayRequest(
            uri = "http://p/ep.m3u8", mime = "application/x-mpegurl", packageName = "com.brouken.player",
            resumeMs = 0, title = null,
            episode = PlayingEpisode(extKey = "teleportal:stb/x", episode = "e1", season = "s1", title = "Супермама", episodeTitle = "Випуск 12"),
        )
        val plan = portal.playerPlan(takesM3u = false, companion = emptyMap())
        assertEquals("video/*", plan.mime)
        assertEquals("Супермама · Випуск 12", plan.extras["title"])
        assertNull(plan.extras["position"])
    }
}
