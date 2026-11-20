package com.likhtar.home.feature.cinema

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseChipsTest {
    // Підпис справжнього пункта «Таємниці бункера».
    @Test
    fun `підпис варіанта розкладається на плашки без службових слів`() {
        assertEquals(
            listOf("2160p", "DV", "WEB-DL", "HEVC", "92 ГБ", "Джерело"),
            releaseChips("🇺🇦 2160p · Dolby Vision · WEB-DL · HEVC · Дубляж · 92 ГБ · Джерело · сезон"),
        )
        assertEquals(4.0, needMbps("2160p · DV"), 0.0)
        assertEquals(1.5, needMbps("1080p · WEB-DL"), 0.0)
    }
}
