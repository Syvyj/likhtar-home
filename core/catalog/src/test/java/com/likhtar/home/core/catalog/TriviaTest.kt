package com.likhtar.home.core.catalog

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriviaTest {
    private val json = """[
        {"id":1,"kind":"movie","title":"Титанік","year":"1997","backdrop":"/b.jpg","poster":"/p.jpg",
         "q":[["Джек — Ді Капріо?",true,"Так."],["Режисер — Спілберг?",false,"Кемерон."]]},
        {"id":2,"kind":"tv","title":"Друзі","year":"1994","backdrop":null,"poster":null,"q":[["Росс — палеонтолог?",true,"Так."]]}
    ]"""

    // 30 тайтлів по два твердження — як справжній файл, лише менший.
    private val many = (1..30).flatMap { t ->
        val item = CatalogItem(tmdbId = t, kind = TitleKind.MOVIE, title = "t$t", year = "2000")
        listOf(TriviaStatement("$t:0", item, "a$t", true, ""), TriviaStatement("$t:1", item, "b$t", false, ""))
    }

    @Test fun `розбір файлу — тайтл, правда й пояснення`() {
        val all = parseTrivia(json)
        assertEquals(3, all.size)
        val first = all[0]
        assertEquals("1:0", first.id)
        assertEquals(TitleKind.MOVIE, first.item.kind)
        assertEquals("Титанік", first.item.title)
        assertEquals("/b.jpg", first.item.backdropPath)
        assertTrue(first.truth)
        assertFalse(all[1].truth)
        assertEquals("Кемерон.", all[1].why)
        assertEquals(TitleKind.TV, all[2].item.kind)
    }

    @Test fun `раунд дня — десять, по одному на тайтл, однаковий цілий день`() {
        val a = dailyRound(many, "2026-11-17")
        assertEquals(10, a.size)
        assertEquals(10, a.map { it.item.tmdbId }.toSet().size)
        assertEquals(a, dailyRound(many, "2026-11-17"))
        assertNotEquals(a, dailyRound(many, "2026-11-18"))
    }

    @Test fun `випадкове — не з бачених і не з раунду дня, а всі бачені — коло спочатку`() {
        val round = dailyRound(many, "2026-11-17").map { it.id }.toSet()
        val seen = many.map { it.id }.filter { it != "5:1" && it != "6:0" }.toSet() - round
        repeat(20) {
            val pick = randomStatement(many, seen, round, Random(it))
            assertTrue(pick.id !in seen && pick.id !in round)
        }
        val all = many.map { it.id }.toSet()
        assertTrue(randomStatement(many, all, emptySet(), Random(1)).id in all)
    }

    @Test fun `серія днів — учора +1, сьогодні без змін, пропуск — з одиниці`() {
        assertEquals(4, nextStreak(lastDate = "2026-11-16", streak = 3, today = "2026-11-17"))
        assertEquals(3, nextStreak(lastDate = "2026-11-17", streak = 3, today = "2026-11-17"))
        assertEquals(1, nextStreak(lastDate = "2026-11-14", streak = 3, today = "2026-11-17"))
        assertEquals(1, nextStreak(lastDate = "", streak = 0, today = "2026-11-17"))
        assertEquals(2, nextStreak(lastDate = "2026-11-30", streak = 1, today = "2026-12-01"))
    }
}
