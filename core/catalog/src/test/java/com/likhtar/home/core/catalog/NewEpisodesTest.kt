package com.likhtar.home.core.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {
    private fun aired(s: Int, e: Int) = AiredEpisode(CatalogItem(tmdbId = 1, kind = TitleKind.TV, title = "x", originalTitle = "x", year = "2026"), s, e, "", "2026-11-09")

    @Test fun `серія до наступної в Продовжити — переглянута`() {
        assertTrue(NewEpisodes.watched(aired(2, 4), 2 to 5))
        assertTrue(NewEpisodes.watched(aired(1, 9), 2 to 1))
    }

    @Test fun `наступна чи нова — ні`() {
        assertFalse(NewEpisodes.watched(aired(2, 5), 2 to 5))
        assertFalse(NewEpisodes.watched(aired(3, 1), 2 to 5))
        assertFalse(NewEpisodes.watched(aired(1, 1), null))
    }
}
