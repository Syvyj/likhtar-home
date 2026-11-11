package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioRefTest {
    @Test fun `ключ сторінки студії розбирається назад`() {
        assertEquals(false to 420, StudioRef.parseKey(StudioRef(420, "Marvel").key))
        assertEquals(true to 49, StudioRef.parseKey(StudioRef(49, "HBO", network = true).key))
        assertNull(StudioRef.parseKey("x12"))
        assertNull(StudioRef.parseKey("c"))
    }
}

class WithQualityTest {
    private val item = CatalogItem(tmdbId = 1, kind = TitleKind.MOVIE, title = "x", originalTitle = "x", year = "2026")

    @Test fun `якість із сервера лягає на картку без неї`() {
        assertEquals(2160, item.withQuality(UaAudio(ua = true, quality = 2160, hdr = "HDR")).quality)
        assertEquals(1080, item.copy(quality = 1080).withQuality(UaAudio(ua = true, quality = 2160)).quality)
        assertEquals(0, item.withQuality(null).quality)
    }
}
