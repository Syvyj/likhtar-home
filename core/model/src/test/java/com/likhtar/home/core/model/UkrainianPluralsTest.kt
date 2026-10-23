package com.likhtar.home.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UkrainianPluralsTest {

    @Test
    fun `single app uses singular form`() {
        assertEquals("застосунок", UkrainianPlurals.apps(1))
        assertEquals("застосунок", UkrainianPlurals.apps(21))
        assertEquals("застосунок", UkrainianPlurals.apps(101))
    }

    @Test
    fun `two to four apps use few form`() {
        assertEquals("застосунки", UkrainianPlurals.apps(2))
        assertEquals("застосунки", UkrainianPlurals.apps(4))
        assertEquals("застосунки", UkrainianPlurals.apps(34))
    }

    @Test
    fun `five and more apps use many form`() {
        assertEquals("застосунків", UkrainianPlurals.apps(5))
        assertEquals("застосунків", UkrainianPlurals.apps(0))
        assertEquals("застосунків", UkrainianPlurals.apps(48))
    }

    @Test
    fun `teens are the exception and use many form`() {
        assertEquals("застосунків", UkrainianPlurals.apps(11))
        assertEquals("застосунків", UkrainianPlurals.apps(12))
        assertEquals("застосунків", UkrainianPlurals.apps(14))
        assertEquals("застосунків", UkrainianPlurals.apps(114))
    }
}
