package com.likhtar.home.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceCaseTest {
    @Test
    fun `group titles become sentence case but keep names and acronyms`() {
        assertEquals("Над плиткою «LIKHTAR HOME»", sentenceCase("НАД ПЛИТКОЮ «LIKHTAR HOME»"))
        assertEquals("Над плиткою «ТБ ЕФІР»", sentenceCase("НАД ПЛИТКОЮ «ТБ ЕФІР»"))
        assertEquals("Мова й країна", sentenceCase("МОВА Й КРАЇНА"))
        // Польські лапки й прийменники («„likhtar home”», «„telewizja NA żywo”»).
        assertEquals("Nad kafelkiem „LIKHTAR HOME”", sentenceCase("NAD KAFELKIEM „LIKHTAR HOME”"))
        assertEquals("Nad kafelkiem „TELEWIZJA NA ŻYWO”", sentenceCase("NAD KAFELKIEM „TELEWIZJA NA ŻYWO”"))
        assertEquals("Above the “LIVE TV” tile", sentenceCase("ABOVE THE “LIVE TV” TILE"))
        assertEquals("Ряди кінозалу й реаліті", sentenceCase("РЯДИ КІНОЗАЛУ Й РЕАЛІТІ"))
        assertEquals("Погода", sentenceCase("ПОГОДА"))
        assertEquals("Already fine", sentenceCase("Already fine"))
    }
}
