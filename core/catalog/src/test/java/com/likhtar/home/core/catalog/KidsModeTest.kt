package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Дитячий режим: що прибирати з рядів і порад (Nottilo «Modalità famiglia»). */
class KidsModeTest {
    @Test fun `жахи й трилери — ні`() {
        assertFalse(kidsSafe(setOf(27)))
        assertFalse(kidsSafe(setOf(18, 53)))
    }

    @Test fun `комедія, анімація, пригоди — так`() = assertTrue(kidsSafe(setOf(35, 16, 12)))

    @Test fun `без жанрів — так (не відомо — не ховаємо)`() = assertTrue(kidsSafe(emptySet()))

    @Test fun `запит порад — з kids=1 лише в дитячому режимі`() {
        assertEquals("&kids=1", advisorKidsParam(kidsMode = true))
        assertEquals("", advisorKidsParam(kidsMode = false))
    }

    @Test fun `рейтинг — Україна, далі США, Британія, будь-який`() {
        assertEquals("12", pickCertification(mapOf("US" to "R", "UA" to "12")))
        assertEquals("R", pickCertification(mapOf("DE" to "16", "US" to "R")))
        assertEquals("16", pickCertification(mapOf("DE" to "16", "US" to " ")))
        assertEquals(null, pickCertification(emptyMap()))
    }

    @Test fun `до PG-13 і 12+ — так, вище — ні`() {
        listOf("G", "PG", "PG-13", "TV-PG", "TV-14", "12", "12+", "6", "U", "12A").forEach { assertTrue(it, kidsAllowed(it, emptySet())) }
        listOf("R", "NC-17", "TV-MA", "16", "16+", "18", "15").forEach { assertFalse(it, kidsAllowed(it, emptySet())) }
    }

    @Test fun `серіал-жахи з рейтингом TV-MA — ні, хоч жанри драма й детектив`() = assertFalse(kidsAllowed("TV-MA", setOf(18, 9648)))

    @Test fun `без рейтингу вирішують жанри`() {
        assertTrue(kidsAllowed(null, setOf(35)))
        assertFalse(kidsAllowed(null, setOf(27)))
    }

    @Test fun `картка франшизи чи всесвіту — у дитячому режимі ні (її id не тайтл, рейтинг не перевірити)`() {
        val franchise = CatalogItem(tmdbId = 2344, kind = TitleKind.MOVIE, title = "Матриця", year = "", opens = "franchise:2344")
        assertFalse(kidsCard(franchise, certification = "PG", genreIds = emptySet()))
        assertTrue(kidsCard(franchise.copy(opens = ""), certification = "PG", genreIds = emptySet()))
    }
}
