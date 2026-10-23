package com.likhtar.home.core.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLocaleTest {
    @Test
    fun systemUntilChosen() {
        val box = ContentLocale.of("", "", Locale("uk", "UA"))
        assertEquals("uk-UA", box.tmdbLanguage)
        assertEquals("UA", box.country)
        assertFalse(box.chosen)
    }

    @Test
    fun chosenWins() {
        val pl = ContentLocale.of("pl", "pl", Locale("uk", "UA"))
        assertEquals("pl-PL", pl.tmdbLanguage)
        assertEquals("PL", pl.country)
        assertTrue(pl.chosen)
    }

    @Test
    fun unsupportedSystemLanguageIsEnglish() {
        assertEquals("en-US", ContentLocale.of("", "", Locale("de", "DE")).tmdbLanguage)
    }
}
