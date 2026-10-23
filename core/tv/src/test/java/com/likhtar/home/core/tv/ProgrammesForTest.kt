package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammesForTest {

    /** Канали беремо зі справжнього плейлиста — там `tvg-id` є не в усіх, тобто живі обидві гілки. */
    private val playlist = M3uParser()
        .parse(checkNotNull(javaClass.classLoader?.getResourceAsStream("playlist-iptv-org.m3u")))
        .map { it.id to it.name }

    /** 07.09.2026, 10:30 UTC — у вікні, яке парсер лишає навколо «зараз» (−3 год … +горизонт). */
    private val now = 1_788_777_000_000L

    private fun guide(xml: String) = XmltvParser(nowMillis = { now }).parse(xml.byteInputStream())

    @Test
    fun `перша гілка — збіг за id`() {
        val programmes = guide(
            xmltv(
                channelId = "1Plus1Marafon.ua@SD",
                channelName = "щось інше, ніж у плейлисті",
                title = "Марафон",
            ),
        ).programmesFor(playlist)

        assertEquals(listOf("Марафон"), programmes["1Plus1Marafon.ua@SD"]?.map { it.title })
    }

    @Test
    fun `друга гілка — збіг за назвою, коли id чужий`() {
        val programmes = guide(
            xmltv(channelId = "ictv.provider.local", channelName = "ICTV", title = "Факти"),
        ).programmesFor(playlist)

        // Канал у плейлисті названий «ICTV (1080p)»: за id не збігся б жодним чином.
        val target = playlist.single { it.second == "ICTV (1080p)" }.first
        assertEquals(listOf("Факти"), programmes[target]?.map { it.title })
    }

    @Test
    fun `друга гілка згортає абетки`() {
        // Кириличні С і Т замість латинських C і T — на око той самий ICTV.
        val programmes = guide(
            xmltv(channelId = "x.provider.local", channelName = "IСТV", title = "Свобода слова"),
        ).programmesFor(playlist)

        val target = playlist.single { it.second == "ICTV (1080p)" }.first
        assertEquals(listOf("Свобода слова"), programmes[target]?.map { it.title })
    }

    @Test
    fun `чужий канал не чіпляється до випадкового нашого`() {
        val programmes = guide(
            xmltv(channelId = "bbc.uk", channelName = "BBC One", title = "News at Ten"),
        ).programmesFor(playlist)

        assertTrue(programmes.isEmpty())
    }

    @Test
    fun `передачі одного каналу віддаються за часом початку`() {
        val xml = """
            <tv>
              <channel id="1Plus1Marafon.ua@SD"><display-name>1+1 Marafon</display-name></channel>
              <programme channel="1Plus1Marafon.ua@SD" start="20260907120000 +0000" stop="20260907130000 +0000">
                <title>Друга</title>
              </programme>
              <programme channel="1Plus1Marafon.ua@SD" start="20260907100000 +0000" stop="20260907110000 +0000">
                <title>Перша</title>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = guide(xml).programmesFor(playlist)

        assertEquals(listOf("Перша", "Друга"), programmes["1Plus1Marafon.ua@SD"]?.map { it.title })
    }

    private fun xmltv(channelId: String, channelName: String, title: String) = """
        <tv>
          <channel id="$channelId"><display-name>$channelName</display-name></channel>
          <programme channel="$channelId" start="20260907100000 +0000" stop="20260907110000 +0000">
            <title>$title</title>
          </programme>
        </tv>
    """.trimIndent()
}
