package com.likhtar.home.core.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBrowseTest {

    private fun game(
        title: String,
        sources: Map<CloudService, List<CloudLaunch>> = mapOf(CloudService.GEFORCE_NOW to listOf(CloudLaunch("1", "n"))),
        genres: List<String> = emptyList(),
        free: Boolean = false,
        coop: String = "",
        popularity: Int = 0,
        stores: List<String> = emptyList(),
    ) = CloudGame(
        key = cloudKey(title), title = title, sources = sources, stores = stores, genres = genres, free = free, coop = coop,
        popularity = popularity, pad = "", desc = "", dev = "", pub = "", year = "", shots = emptyList(), trailer = "",
        cover = null, cover2 = null, hero = null,
    )

    @Test fun `головний сервіс — перший, де гра стартує одразу, Boosteroid-встановлення — лише якщо більше ніде`() {
        val installOnly = mapOf(
            CloudService.BOOSTEROID to listOf(CloudLaunch("7", install = true)),
            CloudService.GEFORCE_NOW to listOf(CloudLaunch("100", "gta")),
        )
        assertEquals(CloudService.GEFORCE_NOW, game("GTA V", installOnly).mainService())
        val ready = mapOf(CloudService.BOOSTEROID to listOf(CloudLaunch("2")), CloudService.XBOX to listOf(CloudLaunch("9P", "x")))
        assertEquals(CloudService.BOOSTEROID, game("WoT", ready).mainService())
        assertEquals(CloudService.BOOSTEROID, game("Only install", mapOf(CloudService.BOOSTEROID to listOf(CloudLaunch("3", install = true)))).mainService())
        // Сервіс вимкнено людиною — головним стає наступний.
        assertEquals(CloudService.XBOX, game("WoT", ready).mainService(enabled = setOf(CloudService.XBOX)))
        assertNull(game("WoT", ready).mainService(enabled = emptySet()))
    }

    @Test fun `посилання запуску — перевірені на приставці формати`() {
        assertEquals(CloudLink("stream://boosteroid.tv/play?id=2", "https://cloud.boosteroid.com/application/2"), cloudLink(CloudService.BOOSTEROID, CloudLaunch("2")))
        assertEquals(
            CloudLink("nvidia://deeplink?action=play-game&cms-id=100133011", "https://play.geforcenow.com/mall/#/streamer?launchSource=GeForceNOW&cmsId=100133011&shortName=wot&appLaunchMode=Default"),
            cloudLink(CloudService.GEFORCE_NOW, CloudLaunch("100133011", "wot")),
        )
        // Better xCloud читає посилання лише в onCreate — тому зі скиданням задачі.
        assertEquals(
            CloudLink("bxc://play/forza-horizon-5/9NKX70BBCDRN", "https://www.xbox.com/play/launch/forza-horizon-5/9NKX70BBCDRN", clearTask = true),
            cloudLink(CloudService.XBOX, CloudLaunch("9NKX70BBCDRN", "forza-horizon-5")),
        )
    }

    @Test fun `фільтри — безкоштовні, кооп, спліт-скрін, сервіс, магазин, жанр, пошук`() {
        val a = game("Alpha Strike", genres = listOf("action"), free = true, coop = "cl", stores = listOf("Steam"))
        val b = game("Beta", sources = mapOf(CloudService.XBOX to listOf(CloudLaunch("9", "b"))), genres = listOf("rpg"), coop = "c", stores = listOf("Game Pass"))
        val all = listOf(a, b)
        fun names(f: CloudFilter) = all.filter { it.passes(f) }.map { it.title }
        assertEquals(listOf("Alpha Strike"), names(CloudFilter(free = true)))
        assertEquals(listOf("Alpha Strike", "Beta"), names(CloudFilter(coop = true)))
        assertEquals(listOf("Alpha Strike"), names(CloudFilter(split = true)))
        assertEquals(listOf("Beta"), names(CloudFilter(service = CloudService.XBOX)))
        assertEquals(listOf("Alpha Strike"), names(CloudFilter(stores = setOf("Steam", "Epic"))))
        assertEquals(listOf("Beta"), names(CloudFilter(genres = setOf("rpg"))))
        assertEquals(listOf("Alpha Strike"), names(CloudFilter(query = " strike ")))
        assertEquals(listOf("Beta"), names(CloudFilter(only = setOf(b.key))))
        assertEquals(listOf("Alpha Strike"), names(CloudFilter(mine = true)))
    }

    @Test fun `мало ігор — один блок без назви, за популярністю`() {
        val few = (1..5).map { game("G$it", popularity = it) }
        val blocks = cloudBlocks(few)
        assertEquals(1, blocks.size)
        assertEquals("", blocks.single().key)
        assertEquals("G5", blocks.single().games.first().title)
    }

    @Test fun `блоки — популярні, теми, жанри, дрібні жанри в «Інше»`() {
        val games = (1..20).map { game("Act$it", genres = listOf("action"), popularity = it, coop = if (it <= 3) "cl" else "") } +
            (1..8).map { game("Rpg$it", genres = listOf("rpg"), free = it == 1, coop = if (it == 2) "c" else "") } +
            (1..2).map { game("Puz$it", genres = listOf("puzzle")) } +
            game("NoGenre")
        val blocks = cloudBlocks(games)
        assertEquals(listOf("popular", "split", "coop", "free", "action", "rpg", "other"), blocks.map { it.key })
        assertTrue(blocks.first().popular)
        assertEquals("Act20", blocks.first().games.first().title)
        // У «Інше» — і дрібний жанр, і гра без жанру.
        assertEquals(setOf("Puz1", "Puz2", "NoGenre"), blocks.last().games.map { it.title }.toSet())
        // Кооп онлайн — без тих, хто вже у спліт-скріні.
        assertEquals(listOf("Rpg2"), blocks.single { it.key == "coop" }.games.map { it.title })
    }

    @Test fun `тема, що покриває майже весь список, не показується`() {
        val games = (1..20).map { game("F$it", genres = listOf("action"), free = true) }
        assertFalse(cloudBlocks(games).any { it.key == "free" })
    }

    @Test fun `свій порядок і сховані блоки — за ключем`() {
        val blocks = listOf("popular", "split", "action", "rpg").map { CloudBlock(it, emptyList()) }
        assertEquals(listOf("rpg", "popular", "action"), arrangeBlocks(blocks, order = listOf("rpg", "popular"), hidden = setOf("split")).map { it.key })
        assertEquals(listOf("rpg", "popular", "split", "action"), arrangeBlocks(blocks, listOf("rpg", "popular"), setOf("split"), withHidden = true).map { it.key })
    }
}
