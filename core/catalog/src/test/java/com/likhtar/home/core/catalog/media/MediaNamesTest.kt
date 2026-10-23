package com.likhtar.home.core.catalog.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNamesTest {
    @Test
    fun movies() {
        assertEquals(MediaName("Dune Part Two", 2024), MediaNames.parse("Movies/Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.mkv"))
        assertEquals(MediaName("Дюна", 2021), MediaNames.parse("Дюна (2021) 4K.mkv"))
        assertEquals(MediaName("The Matrix", 1999), MediaNames.parse("The_Matrix_[1999]_1080p.mp4"))
        assertEquals(MediaName("Інтерстеллар"), MediaNames.parse("Інтерстеллар.BDRip.avi"))
    }

    @Test
    fun episodes() {
        assertEquals(MediaName("Dark Matter", 0, 2, 8), MediaNames.parse("TV/Dark.Matter.S02E08.1080p.WEB-DL.mkv"))
        assertEquals(MediaName("Шоґун", 2024, 1, 3), MediaNames.parse("Шоґун 2024 s01e03.mkv"))
        assertEquals(MediaName("Friends", 0, 3, 12), MediaNames.parse("Friends 3x12.avi"))
    }

    @Test
    fun episodeNamedOnlyByNumberTakesShowFromFolder() {
        assertEquals(MediaName("Темна матерія", 0, 2, 3), MediaNames.parse("Серіали/Темна матерія/Сезон 2/Серія 03.mp4"))
        assertEquals(MediaName("Severance", 0, 1, 5), MediaNames.parse("Severance/Season 1/E05.mkv"))
        assertEquals(MediaName("Severance", 0, 1, 5), MediaNames.parse("Severance/S01E05.mkv"))
    }

    @Test
    fun video() {
        assertTrue(MediaNames.isVideo("a.MKV"))
        assertFalse(MediaNames.isVideo("a.srt"))
        assertFalse(MediaNames.isVideo("README"))
    }
}
