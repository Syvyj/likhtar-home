package com.likhtar.home.feature.cinema

import com.likhtar.home.core.tv.channelLabel
import com.likhtar.home.core.tv.mergeChannels
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
    fun `передача скінчилась після завантаження — канал лишається з наступною`() {
        val kino = link("Кіно 1", "Кіно")
        val schedules = mapOf(
            kino.url to ChannelSchedule(
                live = NowOnAir("Стара", image = null, startMillis = now - 60 * min, endMillis = now - min),
                upcoming = listOf(com.likhtar.home.core.tv.EpgProgramme("Нова", now - min, now + 30 * min)),
            ),
        )
        assertEquals(listOf("Нова"), liveCards(listOf(kino), schedules, emptyMap(), now).map { it.raw })
    }

    @Test
    fun `пауза ефіру — канал з найближчою передачею`() {
        val kino = link("Кіно 1", "Кіно")
        val schedules = mapOf(kino.url to ChannelSchedule(live = null, upcoming = listOf(com.likhtar.home.core.tv.EpgProgramme("Згодом", now + 10 * min, now + 70 * min))))
        assertEquals(listOf(now + 10 * min), liveCards(listOf(kino), schedules, emptyMap(), now).map { it.startMillis })
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

class SearchChannelsTest {
    private fun card(name: String) = LiveCard(com.likhtar.home.core.tv.ChannelLink(name = name, packageName = "", url = "http://h/$name"), "", 0, 0, null, null)

    @org.junit.Test
    fun `пошук за частиною назви без огляду на регістр, порожній запит — нічого`() {
        val all = listOf(card("TVP 1 HD"), card("Polsat News"), card("Новий канал"))
        org.junit.Assert.assertEquals(listOf("TVP 1 HD"), searchChannels(all, " tvp ").map { it.link.name })
        org.junit.Assert.assertEquals(listOf("Новий канал"), searchChannels(all, "новий").map { it.link.name })
        org.junit.Assert.assertTrue(searchChannels(all, "  ").isEmpty())
    }

    @Test
    fun backups() {
        val own = listOf(
            ChannelLink(name = "Polsat Rodzina", packageName = "", url = "http://a/PolsatRodzina/video.m3u8"),
            ChannelLink(name = "Polsat Rodzina HD", packageName = "", url = "stalker://p/1-2/video.m3u8"),
            ChannelLink(name = "Polsat Rodzina", packageName = "s", url = "sweettv://channel/9"),
            ChannelLink(name = "TVP 3", packageName = "", url = "http://a/TVP3/video.m3u8"),
        )
        val b = com.likhtar.home.core.tv.channelBackups(own, com.likhtar.home.core.tv.TvCatalog(emptyList(), emptyList()))
        // Запасне — лише своє джерело (застосунок плеєр не відкриє); канал з одним джерелом без запасних.
        assertEquals(listOf("stalker://p/1-2/video.m3u8"), b["http://a/PolsatRodzina/video.m3u8"])
        assertEquals(listOf("http://a/PolsatRodzina/video.m3u8"), b["stalker://p/1-2/video.m3u8"])
        assertEquals(null, b["http://a/TVP3/video.m3u8"])
    }

    @Test
    fun frame() {
        val archived = ChannelLink(name = "A", packageName = "", url = "http://cdn.example/live/A/video.m3u8?token=t",
            archive = com.likhtar.home.core.tv.ChannelArchive("flussonic", 3, ""))
        // 1_000_000 с − 90 с, вниз до 5 хв.
        assertEquals("http://cdn.example/live/A/999900-preview.jpg?token=t", channelFrame(archived, 1_000_000_000L))
        assertEquals("http://cdn.example/live/A/999900-preview.jpg?token=t", channelFrame(archived.copy(archive = null), 1_000_000_000L))
        assertEquals(null, channelFrame(archived.copy(url = "http://other/live/A.m3u8"), 1_000_000_000L))
        assertEquals(null, channelFrame(archived.copy(url = "stalker://p/1-2/video.m3u8"), 1_000_000_000L))
    }

    @Test
    fun groupEdits() {
        val links = listOf(
            ChannelLink(name = "A", packageName = "", url = "a", category = "Польща"),
            ChannelLink(name = "B", packageName = "", url = "b", category = "Болгарія"),
            ChannelLink(name = "C", packageName = "", url = "c", category = ""),
        )
        val edits = com.likhtar.home.core.tv.ChannelEdits(groupNames = mapOf("Польща" to "PL"), groupHidden = setOf("Болгарія"))
        assertEquals(listOf("PL", ""), groupEdited(links, edits).map { it.category })
        // Перейменування перейменованого: ключ — первісна назва, порядок і сховані йдуть за назвою.
        val twice = edits.copy(groupOrder = listOf("PL", "Болгарія")).renameGroup("PL", "Polska")
        assertEquals(mapOf("Польща" to "Polska"), twice.groupNames)
        assertEquals(listOf("Polska", "Болгарія"), twice.groupOrder)
        assertEquals(mapOf<String, String>(), twice.renameGroup("Polska", "Польща").groupNames)
    }

    @Test
    fun userGroupOrder() {
        val card = { url: String, g: String -> LiveCard(ChannelLink(name = url, packageName = "", url = url, category = g), "", 0, 0, null, null) }
        val live = listOf(card("a", "X"), card("b", "Y"), card("c", "Z"))
        assertEquals(listOf("c", "a", "b"), guideChannels(live, listOf("X", "Y", "Z"), emptyList(), userGroups = listOf("Z")).map { it.link.url })
        // Перейменована група стоїть там, де стояла під первісною назвою.
        val renamed = listOf(card("a", "X"), card("b", "Mine"), card("c", "Z"))
        assertEquals(listOf("a", "b", "c"), guideChannels(renamed, listOf("X", "Y", "Z"), emptyList(), originals = mapOf("Mine" to "Y")).map { it.link.url })
    }
}
