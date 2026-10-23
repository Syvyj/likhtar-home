package com.likhtar.home.core.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Звірка назв — єдине місце, де можна тихо показати чужий фільм як «є в застосунку».
 * Провайдер віддає все, що схоже; фільтр тут.
 */
class InstalledAppSearchTest {

    @Test
    fun `регістр і пунктуація не заважають`() {
        assertTrue(titleMatches("ОБСЕСІЯ", "Обсесія", "Obsession"))
        assertTrue(titleMatches("Obsession.", "Обсесія", "Obsession"))
    }

    @Test
    fun `рік і уточнення в хвості приймаються`() {
        assertTrue(titleMatches("Обсесія (2025)", "Обсесія", "Obsession"))
    }

    @Test
    fun `інший матеріал не проходить`() {
        assertFalse(titleMatches("Обсесія 2", "Обсесія", "Obsession"))
        assertFalse(titleMatches("Ще одна обсесія", "Обсесія", "Obsession"))
        assertFalse(titleMatches("", "Обсесія", "Obsession"))
    }
}
