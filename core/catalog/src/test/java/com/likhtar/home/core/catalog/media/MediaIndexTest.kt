package com.likhtar.home.core.catalog.media

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIndexTest {
    @Test
    fun indexRoundTrip() {
        val file = LocalFile("s1", "Темна матерія/Сезон 2/Серія 03.mkv", "Серія 03.mkv", "", 1_000_000_000, 2, 3)
        val state = LibraryState(
            titles = listOf(LocalTitle(CatalogItem(1, TitleKind.TV, "Темна матерія", year = "2024", posterPath = "/p.jpg"), listOf(file))),
            other = listOf(LocalFile("s2", "Відпустка.mp4", "Відпустка.mp4", "http://nas/1.mp4", 50_000_000)),
            status = "Файлів: 2",
            scannedAt = 42,
        )
        assertEquals(state, MediaLibrary.decodeIndex(MediaLibrary.encodeIndex(state)))
    }

    @Test
    fun sourcesRoundTrip() {
        val list = listOf(
            MediaSource("a", MediaSource.Kind.SMB, "Мак", "smb://192.168.0.5/Movies", "admin", "p@ss"),
            MediaSource("b", MediaSource.Kind.DLNA, "NAS", "http://192.168.0.10:8200/rootDesc.xml"),
        )
        assertEquals(list, MediaLibrary.decodeSources(MediaLibrary.encodeSources(list)))
    }

    @Test
    fun smbAddress() {
        assertEquals(SmbAddress("192.168.0.5", "Movies", "Кіно/2024"), SmbAddress.parse("smb://192.168.0.5/Movies/Кіно/2024/"))
        assertEquals(SmbAddress("nas", "video", ""), SmbAddress.parse("\\\\nas\\video"))
        assertEquals(null, SmbAddress.parse("smb://nas"))
    }

    @Test
    fun matchNeedsSameName() {
        val dune = CatalogItem(1, TitleKind.MOVIE, "Дюна: Частина друга", originalTitle = "Dune: Part Two", year = "2024")
        assertEquals(true, MediaLibrary.sameTitle(dune, "Dune Part Two", 2024))
        assertEquals(false, MediaLibrary.sameTitle(dune, "Dune Part Two", 2019))
        assertEquals(false, MediaLibrary.sameTitle(CatalogItem(2, TitleKind.MOVIE, "Відпустка мрії", year = "2023"), "Відпустка", 2023))
        assertEquals(false, MediaLibrary.sameTitle(CatalogItem(3, TitleKind.MOVIE, "Канікули", originalTitle = "Vacation", year = "2015"), "Відпустка", 2023))
        assertEquals(true, MediaLibrary.sameTitle(CatalogItem(4, TitleKind.TV, "Темна матерія", originalTitle = "Dark Matter", year = "2024"), "Темна матерія", 0))
    }

    @Test
    fun pickNewestWithoutYear() {
        val old = CatalogItem(1, TitleKind.TV, "Темна матерія", originalTitle = "Dark Matter", year = "2015")
        val new = CatalogItem(2, TitleKind.TV, "Темна матерія", originalTitle = "Dark Matter", year = "2024")
        val other = CatalogItem(3, TitleKind.TV, "Темна матерія: Повернення", year = "2026")
        assertEquals(new, MediaLibrary.pick(listOf(old, other, new), "Dark Matter", 0))
        assertEquals(old, MediaLibrary.pick(listOf(new, old), "Dark Matter", 2015))
    }
}
