package com.likhtar.home.core.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCatalogTest {

    @Test fun `назви одної гри з різних сервісів дають один ключ`() {
        val same = listOf(
            "Cyberpunk 2077" to "Cyberpunk 2077™",
            "Forza Horizon 5" to "Forza Horizon 5 (Xbox)",
            "Control" to "Control - Xbox Series X|S",
            "Assassin's Creed Valhalla" to "Assassin's Creed® Valhalla - Standard Edition",
            "Halo Infinite" to "Halo Infinite (Game Preview)",
            "World of Tanks" to "World of Tanks (EU)",
            "Dungeons & Dragons" to "Dungeons and Dragons",
            "Fortnite" to "Fortnite (Epic Games Store)",
            "Hades" to "Hades - Cross-Gen Bundle",
        )
        for ((a, b) in same) assertEquals("$a ~ $b", cloudKey(a), cloudKey(b))
        assertFalse(cloudKey("Contra") == cloudKey("Contra: Operation Galuga"))
    }

    @Test fun `назва без суфіксів магазину й видання`() {
        assertEquals("Forza Horizon 5", cleanTitle("Forza Horizon 5 (Steam)"))
        assertEquals("Assassin's Creed Valhalla", cleanTitle("Assassin's Creed® Valhalla - Standard Edition"))
    }

    @Test fun `демо, плейтести й відкриті бети — не окремі ігри`() {
        assertTrue(isJunkTitle("Dune: Awakening Demo"))
        assertTrue(isJunkTitle("Marvel Rivals Playtest"))
        assertTrue(isJunkTitle("Splitgate 2 - Open Beta"))
        assertFalse(isJunkTitle("Demon's Souls"))
    }

    private fun src(
        service: CloudService,
        title: String,
        id: String = title,
        desc: String = "",
        cover: String? = null,
        steam: String? = null,
        pad: String = "",
        controller: Boolean = false,
        free: Boolean = false,
        year: String = "",
        stores: Set<String> = emptySet(),
        coop: Set<Char> = emptySet(),
    ) = SourceGame(
        service = service, title = title, launch = listOf(CloudLaunch(id)), stores = stores, free = free,
        desc = desc, cover = cover, steamId = steam, pad = pad, controller = controller, year = year, coop = coop,
    )

    @Test fun `одна гра з трьох сервісів — одна картка з трьома джерелами`() {
        val games = mergeCloud(
            boost = listOf(src(CloudService.BOOSTEROID, "Cyberpunk 2077", "42", stores = setOf("Steam"), steam = "1091500", controller = true)),
            gfn = listOf(src(CloudService.GEFORCE_NOW, "Cyberpunk 2077™", "100", desc = "A long open-world RPG description that is clearly longer than sixty chars.", pad = "full", coop = setOf('c'))),
            xbox = listOf(src(CloudService.XBOX, "Cyberpunk 2077 - Xbox Series X|S", "9P", cover = "https://x/poster.jpg", year = "2020")),
        )
        assertEquals(1, games.size)
        val g = games.single()
        assertEquals("Cyberpunk 2077", g.title)
        assertEquals(setOf(CloudService.BOOSTEROID, CloudService.GEFORCE_NOW, CloudService.XBOX), g.sources.keys)
        assertEquals("42", g.sources.getValue(CloudService.BOOSTEROID).single().id)
        assertTrue("Game Pass" in g.stores && "Steam" in g.stores)
        // Обкладинка: Steam першим, далі Xbox.
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1091500/library_600x900.jpg", g.cover)
        assertEquals("https://x/poster.jpg", g.cover2)
        assertEquals("full", g.pad)
        assertEquals("2020", g.year)
        assertEquals("c", g.coop)
    }

    @Test fun `гасло замість опису поступається довшому опису з іншого джерела`() {
        val g = mergeCloud(
            boost = listOf(src(CloudService.BOOSTEROID, "Game", desc = "Boosteroid long description of the game that goes on and on for a while.")),
            gfn = listOf(src(CloudService.GEFORCE_NOW, "Game", desc = "Ready for Battle?")),
            xbox = emptyList(),
        ).single()
        assertTrue(g.desc.startsWith("Boosteroid long"))
    }

    @Test fun `рік 9998 з Xbox — невідомо`() {
        val g = mergeCloud(emptyList(), emptyList(), listOf(src(CloudService.XBOX, "Old", year = "9998"))).single()
        assertEquals("", g.year)
    }

    @Test fun `Xbox Cloud — завжди з геймпадом, Boosteroid з контролером — теж`() {
        assertEquals("full", mergeCloud(emptyList(), emptyList(), listOf(src(CloudService.XBOX, "X"))).single().pad)
        assertEquals("full", mergeCloud(listOf(src(CloudService.BOOSTEROID, "B", controller = true)), emptyList(), emptyList()).single().pad)
        assertEquals("", mergeCloud(listOf(src(CloudService.BOOSTEROID, "B2")), emptyList(), emptyList()).single().pad)
    }

    @Test fun `безкоштовна, якщо хоч один сервіс так каже, порядок — за назвою`() {
        val games = mergeCloud(
            listOf(src(CloudService.BOOSTEROID, "beta"), src(CloudService.BOOSTEROID, "Alpha", free = true)),
            listOf(src(CloudService.GEFORCE_NOW, "beta", free = true)),
            emptyList(),
        )
        assertEquals(listOf("Alpha", "beta"), games.map { it.title })
        assertTrue(games.all { it.free })
    }

    @Test fun `без джерела, що зламалось, решта зводиться як є`() {
        val g = mergeCloud(emptyList(), listOf(src(CloudService.GEFORCE_NOW, "Only GFN")), emptyList()).single()
        assertEquals(setOf(CloudService.GEFORCE_NOW), g.sources.keys)
        assertNull(g.cover)
    }
}
