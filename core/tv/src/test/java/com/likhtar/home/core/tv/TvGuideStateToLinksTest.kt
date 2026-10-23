package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvGuideStateToLinksTest {

    private val channels = listOf(
        TvChannel(id = "1plus1.ua", name = "1+1", country = "UA", categories = listOf("general")),
        TvChannel(id = "sport.ua", name = "Спорт", country = "UA", categories = listOf("sports"), logo = "logo.png"),
    )

    private val state = TvGuideState(
        channels = channels,
        groups = listOf(TvGroup("auto-sports", "Спорт", categoryId = "sports")),
        channelApps = mapOf("1plus1.ua" to "com.example.player"),
    )

    @Test
    fun `канал переходить у картку разом із застосунком і логотипом`() {
        val links = state.toChannelLinks()
        assertEquals(2, links.size)
        assertEquals("com.example.player", links[0].packageName)
        assertEquals("logo.png", links[1].logo)
        assertEquals("sport.ua", links[1].epgId)
    }

    @Test
    fun `категорія — назва групи, а канал без групи не зникає`() {
        val links = state.toChannelLinks()
        assertEquals("", links[0].category)
        assertEquals("Спорт", links[1].category)
    }
}
