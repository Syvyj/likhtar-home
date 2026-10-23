package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConditionalCacheTest {

    private var now = 0L
    private val cache = ConditionalCache<List<String>>(ttlMs = 100, now = { now })

    @Test
    fun `свіже — без мережі, протухле — умовним запитом, 304 лишає старе`() {
        val asked = mutableListOf<String?>()
        assertEquals(listOf("a"), cache.get("u") { etag, _ -> asked += etag; ConditionalCache.Fetched(listOf("a"), "e1", null) })

        now = 50
        assertEquals(listOf("a"), cache.get("u") { _, _ -> error("не мало питати") })

        now = 150
        assertEquals(listOf("a"), cache.get("u") { etag, _ -> asked += etag; null })
        assertEquals(listOf(null, "e1"), asked)

        // 304 продовжив TTL.
        now = 200
        assertEquals(listOf("a"), cache.get("u") { _, _ -> error("не мало питати") })
    }

    @Test
    fun `невдачу й порожнє не кешуємо`() {
        assertThrows(IllegalStateException::class.java) { cache.get("u") { _, _ -> error("мережа") } }
        assertEquals(emptyList<String>(), cache.get("u") { _, _ -> ConditionalCache.Fetched(emptyList(), null, null) })

        var calls = 0
        cache.get("u") { _, _ -> calls++; ConditionalCache.Fetched(listOf("b"), null, null) }
        assertEquals(1, calls)
    }
}
