package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelEditsTest {
    private fun link(name: String, url: String, pkg: String = "") = ChannelLink(name = name, packageName = pkg, url = url)
    private val own = listOf(link("ICTV", "http://p/ictv"), link("Новий", "http://p/novyi"), link("СТБ", "http://p/stb"))
    private val tv = TvCatalog(listOf(CatalogChannel(link("ICTV HD", "sweettv://1", "tv.sweet"), null), CatalogChannel(link("Новий канал", "sweettv://2", "tv.sweet"), null)), emptyList())

    @Test fun autoPrefersHdAndDedupes() {
        val (links, _) = mergeChannels(own, tv)
        assertEquals(listOf("sweettv://1", "http://p/novyi", "http://p/stb", "sweettv://2"), links.map { it.url })
    }

    @Test fun editsPickSourceRenameJoinHide() {
        val edits = ChannelEdits(
            hidden = setOf("стб"),
            source = mapOf("ictv" to "http://p/ictv"),
            names = mapOf("ictv" to "ICTV Україна"),
            joins = mapOf("новий" to "новийканал"),
            groups = mapOf("ictv" to "Кіно"),
        )
        val (links, _) = mergeChannels(own, tv, edits)
        assertEquals(listOf("http://p/ictv" to "ICTV Україна", "http://p/novyi" to "Новий"), links.map { it.url to it.name })
        assertEquals(listOf("ictv", "новийканал"), links.map { it.key })
        assertEquals("Кіно", links.first().category)
    }

    @Test fun encodeRoundTrip() {
        val e = ChannelEdits(listOf("a", "b"), setOf("c"), mapOf("a" to "u"), mapOf("b" to "Б"), mapOf("d" to "a"), mapOf("a" to "Кіно"))
        assertEquals(e, ChannelEdits.decode(e.encode()))
        assertEquals(ChannelEdits(), ChannelEdits.decode(null))
    }
}
