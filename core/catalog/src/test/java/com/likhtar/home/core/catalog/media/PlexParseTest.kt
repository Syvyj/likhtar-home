package com.likhtar.home.core.catalog.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlexParseTest {
    private val source = MediaSource("p", MediaSource.Kind.PLEX, "Дім", "http://192.168.0.10:32400", "", "tok")

    // Скорочені відповіді тестового сервера 1.43: TMDB id лежить у Guid при includeGuids=1,
    // у серії його немає — він у серіалу, і серія бере його через grandparentRatingKey.
    @Test
    fun `фільм — tmdb із Guid, файл — шлях частини, переглянуто — viewCount`() {
        val got = Plex.parseMovies(
            """{"MediaContainer":{"Metadata":[
              {"ratingKey":"1","title":"Interstellar","viewCount":1,
               "Guid":[{"id":"imdb://tt0816692"},{"id":"tmdb://157336"}],
               "Media":[{"Part":[{"key":"/library/parts/1/1792871861/file.mkv","size":4892861}]}]}
            ]}}""",
            source,
        ).single()
        assertEquals("1", got.path)
        assertEquals(157336, got.tmdbId)
        assertEquals("/library/parts/1/1792871861/file.mkv", got.url)
        assertEquals(true, got.played)
        assertEquals(
            "http://192.168.0.10:32400/library/parts/1/1792871861/file.mkv?X-Plex-Token=tok",
            Plex.streamUrl(source, got),
        )
    }

    @Test
    fun `серія бере TMDB id серіалу`() {
        val shows = Plex.showTmdb("""{"MediaContainer":{"Metadata":[{"ratingKey":"19","Guid":[{"id":"tmdb://1396"}]}]}}""")
        val got = Plex.parseEpisodes(
            """{"MediaContainer":{"Metadata":[
              {"ratingKey":"21","grandparentRatingKey":"19","grandparentTitle":"Breaking Bad","parentIndex":1,"index":1,"title":"Pilot",
               "Media":[{"Part":[{"key":"/library/parts/53/1792871861/file.mkv","size":24722498}]}]}
            ]}}""",
            shows,
            source,
        ).single()
        assertEquals(1396, got.tmdbId)
        assertEquals(true, got.tv)
        assertEquals(1 to 1, got.season to got.episode)
    }

    @Test
    fun `на черзі — лише розпочате, час у мс`() {
        val got = Plex.parseResume(
            """{"MediaContainer":{"Metadata":[
              {"ratingKey":"1","viewOffset":210000,"duration":600046,"lastViewedAt":1792878676},
              {"ratingKey":"22","duration":30000}
            ]}}""",
        )
        assertEquals(listOf(ServerResume("1", 210_000, 600_046, 1_792_878_676_000)), got)
    }

    @Test
    fun `сервер без входу — посилання без токена`() {
        val open = source.copy(password = "")
        assertEquals("http://192.168.0.10:32400/library/parts/1/x/file.mkv", Plex.streamUrl(open, LocalFile("p", "1", "x", url = "/library/parts/1/x/file.mkv")))
    }

    @Test
    fun `сервери акаунта — адреса в домашній мережі першою, без relay`() {
        val got = Plex.parseServers(
            """[
              {"name":"Дім","provides":"server","accessToken":"t1","connections":[
                {"uri":"https://1-2-3-4.abc.plex.direct:32400","local":false,"relay":false},
                {"uri":"http://192.168.0.10:32400","local":true,"relay":false},
                {"uri":"https://relay.plex.direct","local":false,"relay":true}]},
              {"name":"Телефон","provides":"player","connections":[]}
            ]""",
        )
        assertEquals(listOf(PlexServer("Дім", "http://192.168.0.10:32400", "t1")), got)
    }
}
