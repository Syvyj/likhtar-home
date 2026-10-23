package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Назви країн у налаштуваннях — українською, а не англійською з iptv-org. */
class CountryNameTest {
    @Test
    fun `код країни дає українську назву`() {
        assertEquals("Україна", ukrainianName("ua"))
        assertEquals("Польща", ukrainianName("PL"))
    }

    @Test
    fun `невідомий код — без назви, береться запасна`() {
        assertNull(ukrainianName("ZZ"))
    }
}
