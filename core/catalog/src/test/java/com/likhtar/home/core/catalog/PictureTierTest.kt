package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureTierTest {
    private fun vg(
        voice: String, quality: Int, hdr: String, sources: Int = 1,
        audio: List<Track> = emptyList(), names: Map<String, String> = emptyMap(),
        rank: Map<String, Int> = emptyMap(), from: List<String> = emptyList(),
    ) = VoiceGroup(voice, quality, hdr, sources, listOf("r"), audio = audio, names = names, rank = rank, from = from)

    @Test fun `групи й порядок`() {
        val a = vg("Дубляж", 2160, "DV+HDR10+"); val b = vg("Енеїда", 2160, ""); val c = vg("Line", 1080, "")
        val d = vg("Postmodern", 2160, "Dolby Vision"); val e = vg("Стара", 0, ""); val f = vg("HDR", 2160, "HDR10")
        assertEquals(
            listOf(
                PictureTier.DV_4K to listOf(a, d), PictureTier.HDR_4K to listOf(f),
                PictureTier.UHD to listOf(b), PictureTier.FHD to listOf(c), PictureTier.LOWER to listOf(e),
            ),
            byPicture(listOf(a, b, c, d, e, f)),
        )
    }

    @Test fun `DV у 1080p — не група 4K`() {
        assertEquals(PictureTier.FHD, vg("Д", 1080, "DV").pictureTier())
    }

    @Test fun `значки — лише те, чого не каже група`() {
        assertEquals(
            listOf("HDR10+", "Atmos"),
            vg("Д", 2160, "DV+HDR10+", audio = listOf(Track("Original English Atmos", "eng"))).extraChips(),
        )
        assertEquals(listOf("5.1"), vg("Д", 2160, "DV", names = mapOf("r" to "2160p · DV · 5.1 · 20 ГБ")).extraChips())
        assertEquals(emptyList<String>(), vg("Д", 1080, "").extraChips())
    }

    @Test fun `підпис праворуч`() {
        assertEquals("онлайн · 2 сайти", vg("Енеїда", 2160, "", sources = 2, from = listOf("site-a", "site-b")).rightNote())
    }

    @Test fun `українська — за мовою або прапорцем у назві варіанта`() {
        assertEquals(true, vg("Д", 2160, "DV", names = mapOf("r" to "🇺🇦 2160p · DV · 32 ГБ")).isUkrainian())
        assertEquals(true, VoiceGroup("Енеїда", 2160, "", 1, listOf("r"), lang = "ukr").isUkrainian())
        assertEquals(false, vg("Line", 1080, "", names = mapOf("r" to "1080p · 5.1")).isUkrainian())
    }
}
