package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Відповідь зовнішнього плеєра — єдине місце в таймкодах, де легко помилитися мовчки:
 * поганий розбір не падає, він просто щоразу віддає нуль, і «продовжити з місця» тихо
 * не працює.
 */
class PlaybackResultTest {

    @Test
    fun `MX-сумісний плеєр віддає Int у мілісекундах`() {
        val (position, duration) = playbackResultOf(
            mapOf("position" to 125_000, "duration" to 2_700_000),
        )
        assertEquals(125_000L, position)
        assertEquals(2_700_000L, duration)
    }

    @Test
    fun `VLC кладе ті самі числа під своїми ключами і типом Long`() {
        val (position, duration) = playbackResultOf(
            mapOf("extra_position" to 42_000L, "extra_duration" to 3_600_000L),
        )
        assertEquals(42_000L, position)
        assertEquals(3_600_000L, duration)
    }

    @Test
    fun `додивлений до кінця стає позицією в кінці, а не нулем`() {
        val (position, duration) = playbackResultOf(
            mapOf("position" to 0, "duration" to 2_700_000, "end_by" to "playback_completion"),
        )
        assertEquals(2_700_000L, position)
        assertEquals(2_700_000L, duration)
    }

    @Test
    fun `ViMu каже про кінець не в extras, а кодом результату`() {
        val (position, duration) = playbackResultOf(
            mapOf("duration" to 3_779_374, "url" to "https://cdn.example/x.m3u8"),
            resultCode = VIMU_COMPLETED,
        )
        assertEquals(3_779_374L, position)
        assertEquals(3_779_374L, duration)
    }

    @Test
    fun `мовчазний плеєр не вигадує таймкод`() {
        assertEquals(0L to 0L, playbackResultOf(emptyMap()))
        assertEquals(0L to 0L, playbackResultOf(mapOf("position" to "хтозна")))
    }
}
