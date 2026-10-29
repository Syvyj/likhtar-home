package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class RowArrangeTest {

    private fun row(key: String, wide: String? = null) =
        SectionRow(key, emptyList(), emptySet(), emptySet(), emptyMap(), emptyMap(), key = key, wide = wide)

    @Test
    fun `own order moves stable rows around daily ones that stay in place`() {
        val rows = listOf(row("A"), row("Тайтл дня", wide = "title"), row("B"), row("Бо вам сподобались «X»"), row("C"))
        val keys = rows.arranged(order = listOf("C", "A"), hidden = setOf("B", "Тайтл дня")).map { it.key }
        // B сховано; ряди дня сховати не можна, і вони на своїх місцях; C і A міняються місцями.
        assertEquals(listOf("C", "Тайтл дня", "Бо вам сподобались «X»", "A"), keys)
    }

    @Test
    fun `no settings keeps server order`() {
        val rows = listOf(row("A"), row("B"))
        assertEquals(rows, rows.arranged(emptyList(), emptySet()))
    }
}
