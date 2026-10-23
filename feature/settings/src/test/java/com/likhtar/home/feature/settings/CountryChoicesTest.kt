package com.likhtar.home.feature.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryChoicesTest {
    @Test
    fun currentFirstThenNearThenRestWithoutDuplicates() {
        val codes = countryChoices("UA", Locale.ENGLISH).map { it.first }
        assertEquals(listOf("UA", "PL", "DE", "CZ", "GB", "US"), codes.take(6))
        assertEquals(codes.size, codes.distinct().size)
        assertEquals(Locale.getISOCountries().toSet(), codes.toSet())
    }

    @Test
    fun alphabetByLanguageNotByCodePoints() {
        val names = countryChoices("UA", Locale("uk")).drop(6).map { it.second }
        // «Є» за кодом символу стоїть перед «А»; за українською абеткою — після «Е».
        assertEquals(true, names.indexOf("Австралія") < names.indexOf("Єгипет"))
    }
}
