package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Черга Just+: на якій серії вийшли і які позначити переглянутими. */
class ReachedEpisodeTest {
    private val queue = (1..6).map { QueueEntry(episode = it, name = "S01E0$it", url = "https://a.example/e$it") }
    private val variants = queue.map { e -> listOf("1080p · Джерело 2" to e.url, "1080p · Джерело · Студія" to "https://b.example/e${e.episode}") }

    @Test
    fun `почали з першої, вийшли на п'ятій — 1-4 переглянуті`() {
        assertEquals(QueueReach(listOf(1, 2, 3, 4), 5), reachedEpisode(queue, variants, 1, "https://a.example/e5"))
    }

    @Test
    fun `адреса іншої озвучки теж знаходить серію`() {
        assertEquals(QueueReach(listOf(2), 3), reachedEpisode(queue, variants, 2, "https://b.example/e3"))
    }

    @Test
    fun `та сама серія чи невідома адреса — нічого`() {
        assertNull(reachedEpisode(queue, variants, 1, "https://a.example/e1"))
        assertNull(reachedEpisode(queue, variants, 1, "https://elsewhere/x"))
        assertNull(reachedEpisode(queue, variants, 1, null))
    }
}
