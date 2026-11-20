package com.likhtar.home.core.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCatalogRepositoryTest {

    @Test fun `свіжий знімок приймаємо, лише якщо він не обвалився`() {
        assertTrue(acceptSnapshot(previous = 0, fresh = 10))
        assertTrue(acceptSnapshot(previous = 1800, fresh = 1700))
        // API віддав порожньо чи кілька ігор — це збій, а не правда: вчорашній знімок лишається.
        assertFalse(acceptSnapshot(previous = 1800, fresh = 0))
        assertFalse(acceptSnapshot(previous = 1800, fresh = 300))
        assertFalse(acceptSnapshot(previous = 0, fresh = 0))
    }

    @Test fun `ринок Xbox — країна, де є хмара, інакше Польща`() {
        assertEquals("DE", xboxMarket("de"))
        assertEquals("PL", xboxMarket("UA"))
        assertEquals("PL", xboxMarket(""))
    }

    @Test fun `застаріло — понад добу або файлу немає`() {
        val day = 24 * 60 * 60 * 1000L
        assertTrue(isStale(savedAt = 0L, now = day))
        assertTrue(isStale(savedAt = 1L, now = 1 + day + 1))
        assertFalse(isStale(savedAt = 1L, now = 1 + day - 1))
    }
}
