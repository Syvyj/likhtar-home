package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private fun parse(name: String) =
        M3uParser().parse(checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { name })

    @Test
    fun `справжній плейлист iptv-org розбирається без утрат`() {
        // Файл узятий як є з iptv-org.github.io/iptv/countries/ua.m3u — 192 рядки #EXTINF.
        val channels = parse("playlist-iptv-org.m3u")
        assertEquals(192, channels.size)
        assertEquals("1Plus1Marafon.ua@SD", channels[0].id)
        assertEquals("1+1 Marafon (1080p)", channels[0].name)
        assertEquals("General", channels[0].group)
        assertEquals("https://dash2.antik.sk/live/1plus1_marathon/playlist.m3u8", channels[0].url)
    }

    @Test
    fun `кома в атрибуті не відрізає назву`() {
        // У справжньому файлі є http-user-agent із «KHTML, like Gecko» — перша кома в рядку
        // потрапляє в середину User-Agent, а не перед назвою.
        val channels = parse("playlist-iptv-org.m3u")
        assertEquals("1+1 Ukraina (1080p)", channels.single { it.id == "1Plus1Ukraina.ua@SD" }.name)
    }

    @Test
    fun `директива між EXTINF і посиланням не збиває канал`() {
        // #EXTVLCOPT стоїть саме там, і на ньому наївний парсер губить канал.
        val channels = parse("playlist-iptv-org.m3u")
        assertEquals(
            "http://stream.mcquack.net/219/index.m3u8",
            channels.single { it.id == "1Plus1Ukraina.ua@SD" }.url,
        )
    }

    @Test
    fun `канали, що різняться лише плюсом, лишаються різними`() {
        // matchKey згортає «+» — на цьому файлі це злило б «Inter» з «Inter+».
        val channels = parse("playlist-iptv-org.m3u")
        assertEquals(1, channels.count { it.name == "Inter (1080p)" })
        assertEquals(1, channels.count { it.name == "Inter+ (720p)" })
    }

    @Test
    fun `брудний плейлист не валить парсер і не дає дублікатів`() {
        val channels = parse("playlist-messy.m3u")
        // З шести рядків #EXTINF лишаються три канали: два дублікати згорнулись,
        // а обрізаний останній рядок без посилання відкривати нічого.
        assertEquals(listOf("1+1 HD", "ICTV", "2+2, повний день"), channels.map { it.name })
    }

    @Test
    fun `дублікат в іншій абетці згортається в той самий канал`() {
        // Другий ICTV написаний кириличними С і Т — на екрані не відрізнити.
        val channels = parse("playlist-messy.m3u")
        assertEquals(1, channels.count { normalizeChannelName(it.name) == "ictv" })
        assertEquals("http://provider.example.com/live/ictv.ts", channels.single { it.id == "ictv.ua" }.url)
    }

    @Test
    fun `назва береться після першої коми, а не після останньої`() {
        val channels = parse("playlist-messy.m3u")
        assertEquals("2+2, повний день", channels.single { it.id == "2plus2" }.name)
    }

    @Test
    fun `пробіли в group-title обрізаються, порожній лишається порожнім`() {
        val channels = parse("playlist-messy.m3u")
        assertEquals("Українське ТБ", channels.single { it.id == "1plus1" }.group)
        assertEquals("", channels.single { it.id == "ictv.ua" }.group)
    }

    @Test
    fun `логотип провайдера доходить до картки`() {
        val channels = parse("playlist-messy.m3u")
        assertEquals("https://cdn.example.com/logo/1plus1.png", channels.single { it.id == "1plus1" }.logo)
    }

    @Test
    fun `порожній плейлист — порожній список, а не падіння`() {
        assertTrue(M3uParser().parse("#EXTM3U\n".byteInputStream()).isEmpty())
    }

    @Test
    fun `кілька груп через крапку з комою — вкладка за першою`() {
        val m3u = "#EXTM3U\n#EXTINF:-1 tvg-id=\"K.ua\" group-title=\"Animation;Kids;Religious\",Kids TV\nhttp://x/k.m3u8\n"
        assertEquals("Animation", M3uParser().parse(m3u.byteInputStream()).single().group)
    }
}
