package com.likhtar.home.core.catalog.media

import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinParseTest {
    private val source = MediaSource("j", MediaSource.Kind.JELLYFIN, "Дім", "http://192.168.0.10:8096", "u1", "tok")

    // Скорочена відповідь /Users/{id}/Items тестового Jellyfin 10.10: серія TMDB id не має —
    // він у серіалу, і серія бере його через SeriesId.
    private val reply = """
        {"Items":[
          {"Type":"Episode","Id":"e1","Name":"Пілотна серія","SeriesId":"s1","SeriesName":"Пуститися берега",
           "ParentIndexNumber":1,"IndexNumber":1,"ProviderIds":{"Tvdb":"349232"},"MediaSources":[{"Size":24722498}]},
          {"Type":"Series","Id":"s1","Name":"Пуститися берега","ProviderIds":{"Tmdb":"1396"}},
          {"Type":"Movie","Id":"m1","Name":"Інтерстеллар","ProviderIds":{"Tmdb":"157336"},"MediaSources":[{"Size":49429966}]},
          {"Type":"Movie","Id":"m2","Name":"Домашнє відео","ProviderIds":{}}
        ]}
    """.trimIndent()

    @Test
    fun `серії беруть TMDB id серіалу, фільми — свій`() {
        val files = Jellyfin.parseItems(reply, source).associateBy { it.path }
        assertEquals(3, files.size)
        assertEquals(1396, files.getValue("e1").tmdbId)
        assertEquals(true, files.getValue("e1").tv)
        assertEquals(1 to 1, files.getValue("e1").season to files.getValue("e1").episode)
        assertEquals(157336, files.getValue("m1").tmdbId)
        assertEquals(false, files.getValue("m1").tv)
        assertEquals(49429966L, files.getValue("m1").size)
        assertEquals(0, files.getValue("m2").tmdbId)
    }

    @Test
    fun `адреса без схеми — http, без хвостової риски`() {
        assertEquals("http://192.168.0.10:8096", MediaServer.normalize(" 192.168.0.10:8096/ "))
        assertEquals("https://jf.example.org", MediaServer.normalize("https://jf.example.org/"))
    }

    @Test
    fun `посилання на файл — оригінал без перекодування`() {
        assertEquals(
            "http://192.168.0.10:8096/Videos/m1/stream?static=true&api_key=tok",
            Jellyfin.streamUrl(source, LocalFile("j", "m1", "Інтерстеллар")),
        )
    }

    @Test
    fun `продовжити — позиція й тривалість у мс, без нульових`() {
        val got = Jellyfin.parseResume(
            """{"Items":[
              {"Id":"m1","RunTimeTicks":600230000,"UserData":{"PlaybackPositionTicks":200000000,"LastPlayedDate":"2026-10-24T20:10:00.0000000Z"}},
              {"Id":"m2","RunTimeTicks":600230000,"UserData":{"PlaybackPositionTicks":0}}
            ]}""",
        )
        assertEquals(1, got.size)
        assertEquals(ServerResume("m1", 20_000, 60_023, 1792872600000), got[0])
    }
}
