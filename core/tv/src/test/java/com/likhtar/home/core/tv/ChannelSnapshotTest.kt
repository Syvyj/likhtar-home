package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D2: свої канали ТБ Ефіру на диску — перший кадр без розбору плейлиста й порталу. */
class ChannelSnapshotTest {
    private val full = ChannelLink(
        name = "Polsat Rodzina", packageName = "", url = "http://x/1.ts", category = "Кіно", epgId = "77",
        image = "img", logo = "logo", mark = "mark",
        variants = listOf(ChannelVariant("Голі XL", "2015", "опис", "мета", "app", "url")),
        archive = ChannelArchive("shift", 7, "src"), key = "polsat-rodzina",
    )

    @Test fun `канал з варіантами й архівом переживає запис і читання`() {
        val snap = OwnChannels(listOf(full, ChannelLink("Sweet", "tv.sweet.tvplayer", "sweettv://1")), setOf("http://x/1.ts"))
        assertEquals(snap, decodeOwnChannels(encodeOwnChannels(snap)))
    }

    @Test fun `зламане — нічого`() = assertTrue(decodeOwnChannels("{").channels.isEmpty())
}
