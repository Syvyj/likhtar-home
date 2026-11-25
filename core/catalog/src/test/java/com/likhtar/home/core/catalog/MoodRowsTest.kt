package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C3: ряди за настроєм у Кінозалі (Nottilo «Gemme nascoste», «Sotto i 100 minuti», «Storie vere», ряд дня). */
class MoodRowsTest {
    @Test fun `два ряди на відкриття — ряд дня й одна постійна навмання`() {
        val constant = setOf(WatchCollection.HIDDEN_GEMS, WatchCollection.UNDER_100, WatchCollection.TRUE_STORIES)
        val picks = (0 until 50).map { moodCollections(20_000, kotlin.random.Random(it)) }
        picks.forEach { rows ->
            assertEquals(2, rows.size)
            assertTrue(rows[0].daily)
            assertTrue(rows[1] in constant)
        }
        assertEquals(constant, picks.map { it[1] }.toSet()) // різні відкриття — різні теми
    }

    @Test fun `ряд дня — завтра інший, протягом дня той самий`() {
        assertNotEquals(moodCollections(20_000)[0], moodCollections(20_001)[0])
        assertEquals(moodCollections(20_000)[0], moodCollections(20_000)[0])
    }

    @Test fun `за тиждень — сім різних тем`() =
        assertEquals(7, (0L until 7L).map { moodCollections(20_000 + it)[0] }.toSet().size)

    private fun row(key: String, brand: String? = null) =
        SectionRow(key, emptyList(), emptySet(), emptySet(), emptyMap(), emptyMap(), openBrand = brand, key = key)

    @Test fun `ряди за настроєм — між рядами стрімінгів`() {
        val rows = listOf(row("Нові фільми", "films"), row("Ніч дурнів"), row("netflix", "netflix"), row("apple", "apple"),
            row("hbo", "hbo"), row("amazon", "amazon"))
        val out = withMoods(rows, listOf(row("day"), row("mood")), setOf("netflix", "apple", "hbo", "amazon"), fallbackAt = 6)
        assertEquals(listOf("Нові фільми", "Ніч дурнів", "netflix", "day", "apple", "hbo", "mood", "amazon"), out.map { it.key })
    }

    @Test fun `без стрімінгів — як раніше, після шести`() {
        val rows = (1..8).map { row("r$it") }
        val out = withMoods(rows, listOf(row("day"), row("mood")), setOf("netflix"), fallbackAt = 6)
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5", "r6", "day", "mood", "r7", "r8"), out.map { it.key })
    }

    @Test fun `стрімінг один — обидва після нього`() {
        val out = withMoods(listOf(row("a"), row("n", "netflix"), row("b")), listOf(row("day"), row("mood")), setOf("netflix"), 6)
        assertEquals(listOf("a", "n", "day", "mood", "b"), out.map { it.key })
    }

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
