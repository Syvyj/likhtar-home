package com.likhtar.home.feature.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Варіанти взято з бази: 177 рядків роздач пишуть студію чотирма способами. */
class StudioMatchTest {
    @Test fun `написання студії не ховає доріжку`() {
        listOf("DniproFilm", "Dniprofilm", "Dniprofilm [Режисерська версія]", "DniproFlm")
            .forEach { assertTrue(it, matchesStudio(it, "DniproFilm")) }
    }

    @Test fun `чужа студія не проходить`() {
        assertFalse(matchesStudio("НеЗупиняйПродакшн", "DniproFilm"))
        assertFalse(matchesStudio("DniproFilm", ""))
    }
}

/** Пам'ять озвучки: те, чим вмикали востаннє, має стояти першим. */
class VoiceRankTest {
    @Test fun `недавня озвучка випереджає решту`() {
        val recent = listOf("DniproFilm", "Дубляж")
        assertEquals(0, voiceRank("🇺🇦 1080p · Dniprofilm · Торент", recent))
        assertEquals(1, voiceRank("🇺🇦 2160p · Дубляж · Торент", recent))
        assertEquals(2, voiceRank("🇺🇦 1080p · Джерело", recent))
    }

    @Test fun `без памʼяті порядок не змінюється`() {
        assertEquals(0, voiceRank("будь-що", emptyList()))
    }
}
