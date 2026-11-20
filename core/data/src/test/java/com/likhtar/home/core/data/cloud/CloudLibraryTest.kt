package com.likhtar.home.core.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLibraryTest {
    private fun game(key: String, vararg services: CloudService, free: Boolean = false) = CloudGame(
        key = key, title = key, sources = services.associateWith { listOf(CloudLaunch("1", "n")) }, stores = emptyList(), genres = emptyList(),
        free = free, coop = "", popularity = 0, pad = "", desc = "", dev = "", pub = "", year = "", shots = emptyList(), trailer = "",
        cover = null, cover2 = null, hero = null,
    )

    @Test fun `мої — безкоштовні, Game Pass з Xbox, позначені «У мене є»`() {
        val lib = CloudLibrary(gamePass = true, owned = setOf("elden"))
        assertTrue(lib.isMine(game("fortnite", CloudService.BOOSTEROID, free = true)))
        assertTrue(lib.isMine(game("forza", CloudService.XBOX)))
        assertTrue(lib.isMine(game("elden", CloudService.GEFORCE_NOW)))
        assertFalse(lib.isMine(game("cyberpunk", CloudService.GEFORCE_NOW)))
        // Xbox вимкнено — Game Pass не робить гру моєю, і гри не видно взагалі.
        val noXbox = lib.copy(services = setOf(CloudService.GEFORCE_NOW))
        assertFalse(noXbox.isMine(game("forza", CloudService.XBOX)))
        assertFalse(noXbox.visible(game("forza", CloudService.XBOX)))
    }

    @Test fun `запуск піднімає гру на початок «Граю зараз»`() {
        val lib = CloudLibrary(playing = listOf("a", "b", "c")).played("c")
        assertEquals(listOf("c", "a", "b"), lib.playing)
        assertEquals(listOf("d", "c", "a", "b"), lib.played("d").playing)
    }

    @Test fun `список — додати на початок, повторно — прибрати`() {
        val lib = CloudLibrary(favorites = listOf("a")).toggleFavorite("b")
        assertEquals(listOf("b", "a"), lib.favorites)
        assertEquals(listOf("a"), lib.toggleFavorite("b").favorites)
        assertEquals(listOf("x"), CloudLibrary().togglePlaying("x").playing)
        assertEquals(setOf("x"), CloudLibrary().toggleOwned("x").owned)
        assertEquals(emptySet<String>(), CloudLibrary(owned = setOf("x")).toggleOwned("x").owned)
    }

    @Test fun `фільтр «Лише мої» бере правило бібліотеки`() {
        val lib = CloudLibrary(owned = setOf("elden"))
        assertTrue(game("elden", CloudService.GEFORCE_NOW).passes(CloudFilter(mine = true), lib::isMine))
        assertFalse(game("other", CloudService.GEFORCE_NOW).passes(CloudFilter(mine = true), lib::isMine))
    }

    @Test fun `стан переживає збереження`() {
        val lib = CloudLibrary(setOf(CloudService.XBOX), true, listOf("a", "b"), listOf("c"), setOf("d"), setupDone = true)
        assertEquals(lib, readLibrary(writeLibrary(lib)))
    }
}
