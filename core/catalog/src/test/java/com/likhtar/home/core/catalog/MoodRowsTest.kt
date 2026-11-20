package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C3: ряди за настроєм у Кінозалі (Nottilo «Gemme nascoste», «Sotto i 100 minuti», «Storie vere», ряд дня). */
class MoodRowsTest {
    @Test fun `три постійні й ряд дня`() {
        val rows = moodCollections(epochDay = 20_000)
        assertEquals(listOf(WatchCollection.HIDDEN_GEMS, WatchCollection.UNDER_100, WatchCollection.TRUE_STORIES), rows.take(3))
        assertTrue(rows[3].daily)
        assertEquals(4, rows.size)
    }

    @Test fun `ряд дня — завтра інший, протягом дня той самий`() {
        assertNotEquals(moodCollections(20_000)[3], moodCollections(20_001)[3])
        assertEquals(moodCollections(20_000)[3], moodCollections(20_000)[3])
    }

    @Test fun `за тиждень — сім різних тем`() =
        assertEquals(7, (0L until 7L).map { moodCollections(20_000 + it)[3] }.toSet().size)

    @Test fun `кожна тема дня — за ключовим словом TMDB`() =
        WatchCollection.entries.filter { it.daily }.forEach { assertTrue(it.key, "with_keywords" in it.params) }

    @Test fun `запити рядів`() {
        assertEquals("100", WatchCollection.UNDER_100.params["with_runtime.lte"])
        assertEquals("9672", WatchCollection.TRUE_STORIES.params["with_keywords"])
        assertEquals("7.4", WatchCollection.HIDDEN_GEMS.params["vote_average.gte"])
        // Документалки, концерти, ТБ-фільми й мультфільми з малою кількістю голосів мали найвищі оцінки.
        assertTrue("99" in WatchCollection.HIDDEN_GEMS.params["without_genres"].orEmpty())
        assertEquals("6.5", WatchCollection.UNDER_100.params["vote_average.gte"])
        assertTrue("vote_count.lte" in WatchCollection.HIDDEN_GEMS.params) // «мало голосів» — інакше це просто «Топи»
    }

    @Test fun `ключі унікальні — «Відкрити всі» знаходить ряд за ключем`() =
        assertEquals(WatchCollection.entries.size, WatchCollection.entries.map { it.key }.toSet().size)
}
