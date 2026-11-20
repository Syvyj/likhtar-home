package com.likhtar.home.core.data.cloud

import com.likhtar.home.core.data.RetroGame
import com.likhtar.home.core.data.RetroSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ретро — четверте джерело в тих самих списках (docs/cloud-games-design.md, «Ретро-ігри»). */
class CloudRetroTest {
    private val rom = RetroGame("/storage/emulated/0/ROMs/nes/contra.nes", "Contra", RetroSystem.NES, cover = "https://c", snap = "https://s").toCloudGame()

    @Test fun `образ — картка з ключем за шляхом, приставкою й жанром «ретро»`() {
        assertEquals("rom:/storage/emulated/0/ROMs/nes/contra.nes", rom.key)
        assertEquals("Contra", rom.title)
        assertEquals(CloudRom("/storage/emulated/0/ROMs/nes/contra.nes", "NES"), rom.rom)
        assertEquals(listOf("retro"), rom.genres)
        assertEquals("https://c", rom.cover)
        assertEquals("https://s", rom.hero)
        assertTrue(rom.sources.isEmpty())
        assertNull(rom.mainService())
    }

    @Test fun `ретро видно завжди й воно завжди моє, навіть без жодного сервісу`() {
        val lib = CloudLibrary(services = emptySet())
        assertTrue(lib.visible(rom))
        assertTrue(lib.isMine(rom))
    }

    @Test fun `ретро з хмарною грою не зводиться, навіть з тією ж назвою`() {
        val cloud = mergeCloud(emptyList(), listOf(SourceGame(CloudService.GEFORCE_NOW, "Contra", listOf(CloudLaunch("1", "c")))), emptyList()).single()
        assertFalse(cloud.key == rom.key)
    }

    @Test fun `фільтр «Ретро» лишає образи, фільтр сервісу — прибирає`() {
        val cloud = mergeCloud(emptyList(), listOf(SourceGame(CloudService.GEFORCE_NOW, "Halo", listOf(CloudLaunch("1", "h")))), emptyList()).single()
        assertTrue(rom.passes(CloudFilter(retro = true)))
        assertFalse(cloud.passes(CloudFilter(retro = true)))
        assertFalse(rom.passes(CloudFilter(service = CloudService.GEFORCE_NOW)))
    }

    @Test fun `«Відкрити всі» ряду приставки — лише її образи`() {
        val gb = RetroGame("/r/gb/tetris.gb", "Tetris", RetroSystem.GB).toCloudGame()
        assertTrue(rom.passes(CloudFilter(retro = true, system = "NES")))
        assertFalse(gb.passes(CloudFilter(retro = true, system = "NES")))
    }

    @Test fun `зниклий образ у списку — тьмяна картка з назвою файлу, а не пропуск`() {
        val gone = missingRom("rom:/storage/USB/ROMs/snes/super-mario-world.sfc")!!
        assertEquals("super-mario-world", gone.title)
        assertTrue(gone.rom!!.missing)
        assertNull(missingRom("halo"))
    }

    @Test fun `ретро — свій блок «Ретро», навіть коли образів мало`() {
        val cloud = (1..20).map { SourceGame(CloudService.GEFORCE_NOW, "Game $it", listOf(CloudLaunch("$it", "g")), genres = setOf("action")) }
        val games = mergeCloud(emptyList(), cloud, emptyList()) + rom
        assertTrue(cloudBlocks(games).any { it.key == "retro" && it.games == listOf(rom) })
    }
}
