package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.EpgMatch
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.tv.ChannelLink
import com.likhtar.home.core.tv.ChannelSchedule
import com.likhtar.home.core.tv.NowOnAir
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRowsTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    private fun link(name: String, category: String) = ChannelLink(name = name, packageName = "p", url = "u:$name", category = category)
    private fun work(id: Int, kind: TitleKind, rating: Double) =
        CatalogItem(tmdbId = id, kind = kind, title = "t$id", year = "2020", backdropPath = "/b.jpg", overview = "o", rating = rating)

    @Test
    fun liveNow() {
        val kino = link("Кіно 1", "Кіно")
        val stb = link("СТБ HD", "Українське ТБ")
        val off = link("Off", "")
        val schedules = mapOf(
            kino.url to ChannelSchedule(live = NowOnAir("Аліта", image = null, startMillis = now - 10 * min, endMillis = now + 60 * min), upcoming = emptyList()),
            off.url to ChannelSchedule(live = NowOnAir("Скінчилось", image = null, startMillis = now - 60 * min, endMillis = now - min), upcoming = emptyList()),
        )
        val matches = mapOf("Аліта" to EpgMatch("Аліта", item = work(1, TitleKind.MOVIE, 7.3)))
        // Без програми й зі скінченою передачею каналів у «зараз» немає; збіг — до картки.
        assertEquals(listOf(1), liveCards(listOf(kino, stb, off), schedules, matches, now).map { it.work?.tmdbId })
        assertEquals("СТБ", channelLabel(stb.name))
    }

    @Test
    fun merge() {
        val own = listOf(ChannelLink(name = "Discovery Channel HD", packageName = "m", url = "megogo://1", category = "Пізнавальні та тревел"))
        val tv = com.likhtar.home.core.tv.TvCatalog(
            channels = listOf(
                com.likhtar.home.core.tv.CatalogChannel(ChannelLink("Discovery Channel", "s", "sweettv://channel/336", "Природа, наука й історія"), null),
                com.likhtar.home.core.tv.CatalogChannel(ChannelLink("Viasat Nature EU HD", "s", "sweettv://channel/1704", "Природа, наука й історія"), null),
            ),
            groups = emptyList(),
        )
        val (links, extra) = mergeChannels(own, tv)
        // Свій Discovery лишається своїм, але в жанрі каталогу; дубль зі Sweet.TV не додаємо.
        assertEquals(listOf("megogo://1", "sweettv://channel/1704"), links.map { it.url })
        assertEquals("Природа, наука й історія", links.first().category)
        assertEquals(1, extra.size)

        // Свій без HD поступається HD-версії з каталогу.
        val wild = listOf(ChannelLink(name = "Nat Geo Wild", packageName = "m", url = "megogo://2", category = "Пізнавальні та тревел"))
        val tvWild = com.likhtar.home.core.tv.TvCatalog(
            listOf(com.likhtar.home.core.tv.CatalogChannel(ChannelLink("Nat Geo Wild HD", "s", "sweettv://channel/524", "Природа, наука й історія"), null)),
            emptyList(),
        )
        assertEquals(listOf("sweettv://channel/524"), mergeChannels(wild, tvWild).first.map { it.url })

        // Знак із каталогу переходить на свій канал тієї ж назви.
        val ownStb = listOf(ChannelLink(name = "СТБ", packageName = "m", url = "megogo://3"))
        val tvStb = com.likhtar.home.core.tv.TvCatalog(
            listOf(com.likhtar.home.core.tv.CatalogChannel(ChannelLink("СТБ HD", "s", "sweettv://channel/1", "Українські", mark = "m.png"), null)),
            emptyList(),
        )
        assertEquals("m.png", mergeChannels(ownStb, tvStb).first.single().mark)
    }
}

class UaChannelOrderTest {
    @org.junit.Test
    fun `головні українські канали — у заданому порядку`() {
        val names = listOf("1+1 Україна HD", "2+2 HD", "ТЕТ HD", "Новий Канал HD", "СТБ HD", "ICTV 2 HD", "К1 HD")
        org.junit.Assert.assertEquals(
            listOf("СТБ HD", "1+1 Україна HD", "Новий Канал HD", "ТЕТ HD", "ICTV 2 HD", "2+2 HD", "К1 HD"),
            names.sortedBy { uaChannelRank(it, listOf("стб", "1+1", "новийканал", "тет", "ictv")) },
        )
    }

    @Test
    fun guideChannelsByGroupThenLead() {
        fun card(name: String, group: String) = LiveCard(ChannelLink(name = name, packageName = "p", url = "u:$name", category = group), "x", 0, 1, null, null)
        val list = listOf(
            card("Без групи", ""), card("Кіно 1", "Кіно"), card("ICTV", "Українські"),
            card("СТБ", "Українські"), card("Кіно 1", "Кіно"), card("Дивне", "Невідома"),
        )
        assertEquals(
            listOf("СТБ", "ICTV", "Кіно 1", "Дивне", "Без групи"),
            guideChannels(list, listOf("Українські", "Кіно"), listOf("стб", "ictv")).map { it.link.name },
        )
    }

    @Test
    fun favouritesFirst() {
        fun card(name: String) = LiveCard(ChannelLink(name = name, packageName = "p", url = "u:$name"), "x", 0, 1, null, null)
        val live = listOf(card("A"), card("B"), card("C"), card("D"))
        // Обраний D — першим, навіть якщо його не відкривали; нещодавні — далі, без повтору обраного.
        assertEquals(listOf("D", "B", "A"), mineCards(live, setOf("u:D"), listOf("u:B", "u:D", "u:A", "u:gone")).map { it.link.name })
    }
}
