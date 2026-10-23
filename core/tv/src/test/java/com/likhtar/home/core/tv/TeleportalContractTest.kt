package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Розбір контракту сервера. Усі тіла — з `PROMPT-launcher-7.md`, тобто з того, що ручки
 * реально віддають, а не з наших припущень про них.
 */
class TeleportalContractTest {

    private val client = TeleportalClient(object : LikhtarServer {
        override suspend fun baseUrl(): String? = null
    }, TvHttp())

    @Test
    fun `каталог розбирається, серіальність береться з поля`() {
        val body = """
            {"count":2,"results":[
              {"name":"Хто зверху?","channel":"novyy-kanal","slug":"khto-zverkhu","serial":false,
               "image":"http://host:8100/img/a.jpg"},
              {"name":"Кріпосна","channel":"stb","slug":"kriposna","serial":true,
               "image":"http://host:8100/img/b.jpg"}
            ]}
        """.trimIndent()

        val items = client.parseCatalogue(body)

        assertEquals(listOf("Хто зверху?", "Кріпосна"), items.map { it.name })
        assertEquals(listOf(false, true), items.map { it.serial })
        // Картинка приходить повним URL — базу дописувати не треба.
        assertEquals("http://host:8100/img/a.jpg", items.first().imagePath)
    }

    @Test
    fun `сезон адресується слагом, числового id у відповіді немає`() {
        val body = """
            {"title":"Хто зверху?","description":"Шоу","results":[
              {"title":"Сезон 15","season":"sezon-15"},
              {"title":"Сила таланту","season":"sila-talantu"}
            ]}
        """.trimIndent()

        val project = client.parseProject(body)

        assertEquals("Хто зверху?", project.title)
        assertEquals(listOf("sezon-15", "sila-talantu"), project.seasons.map { it.slug })
    }

    @Test
    fun `випуски беруться з results, запобіжник проти нульового хеша лишається`() {
        // Сервер `hash = "0"` уже не віддає, але запобіжник лишається: колись такі випуски
        // («Палка помста 2018» — їх у сезоні було кілька) роняли екран серіала, бо сітка
        // ключується хешем, а два однакові ключі для Compose — виняток, а не дублікат.
        // Рядок коду проти впалого екрана — обмін, який не обговорюється.
        val body = """
            {"results":[
              {"hash":"d004e0","title":"Випуск 3","image":"http://host/1.jpg"},
              {"hash":"0","title":"Анонс","image":""},
              {"hash":"d004e0","title":"Повтор","image":""}
            ],"dropped":0}
        """.trimIndent()

        assertEquals(listOf("d004e0"), client.parseEpisodes(body).map { it.hash })
    }

    @Test
    fun `плеєр віддає повний URL без дописування бази`() {
        val body = """{"ok":true,"name":"Випуск","hls":"http://host:8100/api/hls.m3u8?url=x","poster":"https://p"}"""

        val playback = client.parsePlayer(body)

        assertEquals("http://host:8100/api/hls.m3u8?url=x", playback.url)
        assertEquals("https://p", playback.poster)
        assertNull(playback.reason)
    }

    @Test
    fun `причини відмови не зливаються в одну`() {
        // geo означає «не буде», server — «спробуйте пізніше». Одне слово на обидва
        // змусило б людину чекати того, що не настане.
        assertEquals(TeleportalReason.GEO, client.parsePlayer("""{"ok":false,"reason":"geo"}""").reason)
        assertEquals(TeleportalReason.SERVER, client.parsePlayer("""{"ok":false,"reason":"server"}""").reason)
        assertEquals(TeleportalReason.NO_STREAM, client.parsePlayer("""{"ok":false,"reason":"no_stream"}""").reason)
        assertEquals(TeleportalReason.REFUSED, client.parsePlayer("""{"ok":false,"reason":"щось нове"}""").reason)
    }

    @Test
    fun `порожній hls при ok true — це «відео немає», а не посилання`() {
        val playback = client.parsePlayer("""{"ok":true,"hls":""}""")
        assertNull(playback.url)
        assertEquals(TeleportalReason.NO_STREAM, playback.reason)
    }
}
