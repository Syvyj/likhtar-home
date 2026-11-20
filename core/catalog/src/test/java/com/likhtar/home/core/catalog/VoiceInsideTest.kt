package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInsideTest {
    @Test fun `українські й оригінал, решта — ще N`() {
        val g = VoiceGroup(
            "Дубляж", 2160, "DV", 1, listOf("r"),
            audio = listOf(
                Track("Дубляж Postmodern", "ukr"), Track("Original English Atmos", "eng"),
                Track("MVO", "ukr"), Track("Commentary", "eng"), Track("Polski", "pol"),
            ),
            subs = listOf(Track("українська", "ukr"), Track("англійська", "eng")),
        )
        assertEquals("Дубляж Postmodern, MVO · оригінал: Original English Atmos · ще 2 · укр. субтитри", g.inside)
    }

    @Test fun `без розбору рядка немає`() {
        assertEquals("", VoiceGroup("Дубляж", 1080, "", 1, listOf("r")).inside)
    }

    @Test fun `підпис безіменної озвучки — сайтом`() {
        val group = VoiceGroup(
            voice = "Українська", quality = 1080, hdr = "", sources = 1, refs = listOf("ref"),
            unnamed = true, from = listOf("teleportal"),
        )
        assertEquals("Українська · 1080p · Телепортал", group.label)
    }

    @Test fun `незнайомий код мови показуємо як є`() {
        assertEquals("українська", langName("ukr"))
        assertEquals("", langName("und"))
        assertEquals("nld", langName("nld"))
    }
}
