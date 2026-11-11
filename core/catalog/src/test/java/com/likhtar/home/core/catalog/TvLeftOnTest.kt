package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.RecentChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Канал, на який перемкнулись у плеєрі, — у «Мої канали»; той, з якого зайшли, там уже є. */
class TvLeftOnTest {
    private val channels = listOf("a", "b", "c").map { RecentChannel(url = "http://h/$it", name = it) }
    private val lineup = TvLineup(urls = channels.map { it.url }, names = channels.map { it.name }, now = listOf("", "", ""), index = 1, channels = channels)

    @Test
    fun `вийшли на іншому каналі — він`() = assertEquals("c", lineup.leftOn(2)?.name)

    @Test
    fun `вийшли на тому ж, плеєр не сказав або індекс поза списком — нічого`() {
        assertNull(lineup.leftOn(1))
        assertNull(lineup.leftOn(-1))
        assertNull(lineup.leftOn(7))
    }
}
