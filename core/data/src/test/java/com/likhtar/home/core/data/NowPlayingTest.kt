package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Що грає»: яку медіасесію показати плашкою у верхньому меню. */
class NowPlayingTest {
    private val min = 60_000L
    private fun s(pkg: String, state: Int, title: String = "Song") = MediaSnapshot(pkg, state, title, "Artist")

    @Test fun `грає — показуємо першу, що грає`() =
        assertEquals("music", pickNowPlaying(listOf(s("yt", PAUSED), s("music", PLAYING)), emptyMap(), 0)?.pkg)

    @Test fun `буферизація — теж «грає»`() = assertEquals("radio", pickNowPlaying(listOf(s("radio", BUFFERING)), emptyMap(), 0)?.pkg)

    @Test fun `без назви — не показуємо`() = assertNull(pickNowPlaying(listOf(s("x", PLAYING, title = " ")), emptyMap(), 0))

    @Test fun `пауза з плашки лишає плашку, щоб можна було продовжити`() =
        assertEquals("music", pickNowPlaying(listOf(s("music", PAUSED)), mapOf("music" to 0L), 10 * min)?.pkg)

    @Test fun `давня пауза — плашки немає`() =
        assertNull(pickNowPlaying(listOf(s("music", PAUSED)), mapOf("music" to 0L), 31 * min))

    @Test fun `пауза, яку ми не бачили, — плашки немає`() =
        assertNull(pickNowPlaying(listOf(s("yt", PAUSED)), emptyMap(), 0))

    private companion object {
        const val PLAYING = 3
        const val PAUSED = 2
        const val BUFFERING = 6
    }
}
