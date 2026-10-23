package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

/** Доріжки взято з роздачі «Tuner (2025) [Hurtom]» — дві українські озвучки в одному файлі. */
class VoiceInsideTest {
    @Test fun `рядок під пігулкою називає доріжки й субтитри`() {
        val group = VoiceGroup(
            voice = "Українська", quality = 1080, hdr = "", sources = 1, refs = listOf("ref"),
            unnamed = true, from = listOf("teleportal"),
            audio = listOf("Багатоголосий · В одне рило", "Двоголосий · Студія"),
            subs = listOf("українська", "англійська"),
        )
        assertEquals(
            "доріжки: Багатоголосий · В одне рило, Двоголосий · Студія   субтитри: українська, англійська",
            group.inside,
        )
        assertEquals("Українська · 1080p · Телепортал", group.label)
    }

    @Test fun `без розбору рядка немає`() {
        assertEquals("", VoiceGroup("Дубляж", 1080, "", 1, listOf("ref")).inside)
    }

    @Test fun `незнайомий код мови показуємо як є`() {
        assertEquals("українська", langName("ukr"))
        assertEquals("", langName("und"))
        assertEquals("nld", langName("nld"))
    }
}
