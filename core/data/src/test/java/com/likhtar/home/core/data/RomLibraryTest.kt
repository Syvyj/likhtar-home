package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomLibraryTest {
    @Test
    fun systemByExtensionAndFolder() {
        assertEquals(RetroSystem.GB, RomLibrary.systemOf("Tetris.gb", "ROMs"))
        assertEquals(RetroSystem.SNES, RomLibrary.systemOf("mario.SFC", null))
        assertEquals(RetroSystem.PSP, RomLibrary.systemOf("game.iso", "PSP"))
        assertEquals(RetroSystem.MEGA_DRIVE, RomLibrary.systemOf("sonic.zip", "Mega Drive"))
        assertNull(RomLibrary.systemOf("game.zip", "ROMs"))
        assertNull(RomLibrary.systemOf("photo.jpg", "gb"))
    }

    @Test
    fun coverFoundWithoutRegionTags() {
        val index = listOf("Tetris (Japan)", "Tetris (World) (Rev 1)", "Tetris 2 (USA)", "Tetris DX (World)")
        assertEquals("Tetris (World) (Rev 1)", RomLibrary.pickCover("tetris", index))
        assertEquals("Tetris 2 (USA)", RomLibrary.pickCover(RomLibrary.titleOf("Tetris_2.gb"), index))
        assertEquals("Tetris (Japan)", RomLibrary.pickCover("Tetris (Japan)", index))
        assertNull(RomLibrary.pickCover("ucity", index))
    }

    @Test
    fun indexAndUrl() {
        val html = """<a href="Pok%C3%A9mon%20-%20Red%20Version%20(USA%2C%20Europe).png">x</a><a href="../">up</a>"""
        assertEquals(listOf("Pokémon - Red Version (USA, Europe)"), RomLibrary.parseIndex(html))
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Boxarts/Tom%20_%20Jerry%20%28USA%29.png",
            RomLibrary.coverUrl(RetroSystem.GB, "Tom & Jerry (USA)"),
        )
    }
}
