package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** tvg-id плейлиста, що насправді ідентифікатор Sweet.TV, — за числом на початку. */
class SweetIdTest {
    @Test
    fun `ідентифікатор Sweet_TV — номер каналу`() {
        assertEquals(2924L, sweetId("2924-istorchni-serial-hd"))
        assertEquals(556L, sweetId(" 556-sweet-multfilm-hd "))
    }

    @Test
    fun `інші tvg-id — ні`() {
        assertNull(sweetId("polsat-romans"))
        assertNull(sweetId("BG_169-epic-drama-hd"))
        assertNull(sweetId("upltv1"))
        assertNull(sweetId("65d75546b5f4696e683d828e"))
        assertNull(sweetId(""))
    }
}
