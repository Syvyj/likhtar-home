package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceOptionLabelTest {
    private val group = VoiceGroup(
        voice = "Дубляж", quality = 2160, hdr = "HDR", sources = 2, refs = listOf("t1", "o1"),
        names = mapOf("t1" to "🇺🇦 2160p · HDR · WEB-DL · 58 ГБ · Торент", "o1" to "🇺🇦 1080p · Енеїда"),
        sourceOf = mapOf("t1" to "torrent", "o1" to "eneyida_ua"),
        seeders = mapOf("t1" to 12),
    )

    @Test
    fun torrentShowsSizeAndSeeders() =
        assertEquals("2160p · HDR · WEB-DL · 58 ГБ · 12 сідів", group.optionLabel("t1"))

    @Test
    fun onlineShowsSiteAndQuality() =
        assertEquals("${siteName("eneyida_ua")} · 1080p", group.optionLabel("o1"))
}
