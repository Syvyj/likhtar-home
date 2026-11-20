package com.likhtar.home.core.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Розбір відповідей API — на JSON тієї ж форми, що віддають сервіси. */
class CloudSourcesTest {

    @Test fun `Boosteroid — сторінка, магазин, жанр, скриншоти рядком, трейлер`() {
        val page = """{"meta":{"current_page":1,"last_page":19},"data":[
          {"id":23,"name":"Fortnite","description":"Battle royale.","genre":[8],"platform":[8],"publisher":"Epic Games","developer":"Epic Games",
           "libraryImageUrl":"https://cdn/lib.jpg","bannerImage":"https://cdn/banner.webp",
           "images":"['https://cdn/s1.jpg', 'https://cdn/s2.jpg']","unavailable":false,"controller":true,"popularity":3826,
           "trailers":[{"url":"https://www.youtube.com/watch?v=7ZIbokU9tRg","embedded_provider":"youtube"}],
           "monetizeType":"free","releaseDate":"2017-07-25","stores":{"steam":"https://store.steampowered.com/app/1091500/x/"}},
          {"id":5,"name":"Old Game (NA)","genre":[],"platform":[],"unavailable":false,"monetizeType":"paid"},
          {"id":6,"name":"Gone","genre":[],"platform":[],"unavailable":true,"monetizeType":"paid"}
        ]}"""
        val (games, last) = parseBoostPage(page, platforms = mapOf(8 to "Epic Games Store"), genres = mapOf(8 to "Action"), install = false)
        assertEquals(19, last)
        val g = games.single()
        assertEquals("Fortnite", g.title)
        assertEquals(CloudLaunch("23", store = "Epic", install = false), g.launch.single())
        assertEquals(setOf("Epic"), g.stores)
        assertEquals(setOf("action"), g.genres)
        assertTrue(g.free && g.controller)
        assertEquals(listOf("https://cdn/s1.jpg", "https://cdn/s2.jpg"), g.shots)
        assertEquals("7ZIbokU9tRg", g.trailer)
        assertEquals("1091500", g.steamId)
        assertEquals("2017-07-25", g.year)
        assertEquals("https://cdn/lib.jpg", g.cover)
    }

    @Test fun `Boosteroid Install — позначка встановлення`() {
        val page = """{"meta":{"last_page":1},"data":[{"id":7,"name":"GTA V","genre":[],"platform":[],"unavailable":false,"monetizeType":"paid"}]}"""
        assertTrue(parseBoostPage(page, emptyMap(), emptyMap(), install = true).first.single().launch.single().install)
    }

    @Test fun `Boosteroid — довідники магазинів і жанрів`() {
        assertEquals(mapOf(7 to "Battle.net", 10 to "EA"), parseBoostNames("""[{"id":7,"name":"Battle.net","icon":"x"},{"id":10,"name":"EA"}]"""))
    }

    @Test fun `GeForce NOW — варіанти, кооп з тегів, геймпад, курсор сторінки`() {
        val page = """{"data":{"apps":{"pageInfo":{"hasNextPage":true,"endCursor":"Mg==","totalCount":2245},"items":[
          {"id":"d8eb","title":"Wolcen: Lords of Mayhem","genres":["ACTION","FREE_TO_PLAY","ROLE_PLAYING"],
           "images":{"GAME_BOX_ART":"https://img/box.jpg","TV_BANNER":"https://img/tv.jpg","HERO_IMAGE":null,"SCREENSHOTS":["https://img/s1.jpg"]},
           "variants":[{"id":"300","appStore":"STEAM","shortName":"wolcen_gfn_pc"},{"id":"301","appStore":"EPIC","shortName":null}],
           "keywords":["Co-op","Split Screen","Loot"],"supportedControls":["KEYBOARD","MOUSE","GAMEPAD_PARTIAL"],
           "shortDescription":"Hack'n'slash.","developerName":"Wolcen Studio","publisherName":"Wolcen Studio"}
        ]}}}"""
        val (games, cursor) = parseGfnPage(page)
        assertEquals("Mg==", cursor)
        val g = games.single()
        assertEquals(listOf(CloudLaunch("300", "wolcen_gfn_pc", "Steam")), g.launch)
        assertEquals(setOf("action", "rpg"), g.genres)
        assertTrue(g.free)
        assertEquals(setOf('c', 'l'), g.coop)
        assertEquals("part", g.pad)
        assertEquals("https://img/box.jpg", g.cover)
        assertEquals("https://img/tv.jpg", g.hero)
        assertEquals(listOf("https://img/s1.jpg"), g.shots)
    }

    @Test fun `GeForce NOW — остання сторінка без курсора`() {
        assertNull(parseGfnPage("""{"data":{"apps":{"pageInfo":{"hasNextPage":false,"endCursor":"Mw=="},"items":[]}}}""").second)
    }

    @Test fun `Xbox — список id без заголовка`() {
        assertEquals(listOf("9P8LR42PTRGJ", "9NGLST31DG26"), parseXboxIds("""[{"siglId":"29a8","title":"All games"},{"id":"9P8LR42PTRGJ"},{"id":"9NGLST31DG26"}]"""))
    }

    @Test fun `Xbox — продукт, slug, кооп з атрибутів, зображення з https`() {
        val json = """{"Products":[{"ProductId":"9P8LR42PTRGJ","LocalizedProperties":[{"ProductTitle":"33 Immortals: Deluxe!","ShortDescription":"Co-op roguelike.",
            "DeveloperName":"Thunder Lotus","PublisherName":"Thunder Lotus",
            "Images":[{"ImagePurpose":"SuperHeroArt","Uri":"//store-images/hero"},{"ImagePurpose":"Poster","Uri":"//store-images/poster"},{"ImagePurpose":"Screenshot","Uri":"https://store-images/s1"}]}],
            "Properties":{"Categories":["Action & adventure"],"Attributes":[{"Name":"XblOnlineCoop"},{"Name":"XblLocalCoop"},{"Name":"XboxLive"}]},
            "MarketProperties":[{"OriginalReleaseDate":"2026-06-11T15:00:00.0000000Z"}]}]}"""
        val g = parseXboxProducts(json).single()
        assertEquals(CloudLaunch("9P8LR42PTRGJ", "33-immortals-deluxe"), g.launch.single())
        assertEquals(setOf('c', 'l'), g.coop)
        assertEquals(setOf("action"), g.genres)
        assertEquals("https://store-images/poster", g.cover)
        assertEquals("https://store-images/hero", g.hero)
        assertEquals(listOf("https://store-images/s1"), g.shots)
        assertEquals("2026-06-11", g.year)
        assertFalse(g.free)
    }

    @Test fun `запис джерела переживає збереження у файл`() {
        val g = SourceGame(
            CloudService.GEFORCE_NOW, "T", listOf(CloudLaunch("1", "t", "Steam", true)), setOf("Steam"), setOf("rpg"), true, setOf('c', 'l'),
            "full", true, "d", "dev", "pub", "2020", listOf("s"), "c", null, "123", 5, "yt",
        )
        assertEquals(listOf(g), readSourceGames(writeSourceGames(listOf(g))))
    }
}
