package com.likhtar.home.core.catalog

import com.likhtar.home.core.model.StreamingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C1: пряма сторінка тайтлу в сервісі за TMDB id через Wikidata, коли сервер посилання не дав. */
class WikidataLinksTest {
    /** Справжня відповідь query.wikidata.org для «Бійцівського клубу» (TMDB 550). */
    private val fightClub = """
        {"head":{"vars":["netflix","disney","prime","apple","max"]},"results":{"bindings":[
          {"netflix":{"type":"literal","value":"26004747"},
           "disney":{"type":"literal","value":"38HCX4uW3BlA"},
           "apple":{"type":"literal","value":"umc.cmc.70dbl3043tb6ealg0u12k1pih"},
           "max":{"type":"literal","value":"feature/urn:hbo:feature:GXSycSQud4r29bAEAAA10"}}
        ]}}
    """.trimIndent()

    @Test fun `фільм — сторінки Netflix, Disney+ і Apple TV`() = assertEquals(
        mapOf(
            StreamingService.NETFLIX to "https://www.netflix.com/title/26004747",
            StreamingService.DISNEY to "https://www.disneyplus.com/movies/x/38HCX4uW3BlA",
            StreamingService.APPLE to "https://tv.apple.com/movie/umc.cmc.70dbl3043tb6ealg0u12k1pih",
        ),
        parseWikidataLinks(fightClub, TitleKind.MOVIE),
    )

    @Test fun `старий id HBO Max застосунок Max не відкриває — не беремо`() =
        assertTrue(StreamingService.HBO !in parseWikidataLinks(fightClub, TitleKind.MOVIE))

    @Test fun `серіал — інші адреси Disney+ і Apple TV`() {
        val links = parseWikidataLinks(fightClub, TitleKind.TV)
        assertEquals("https://www.disneyplus.com/series/x/38HCX4uW3BlA", links[StreamingService.DISNEY])
        assertEquals("https://tv.apple.com/show/umc.cmc.70dbl3043tb6ealg0u12k1pih", links[StreamingService.APPLE])
    }

    @Test fun `Prime — ASIN і gti`() {
        fun prime(id: String) = parseWikidataLinks(
            """{"results":{"bindings":[{"prime":{"type":"literal","value":"$id"}}]}}""", TitleKind.MOVIE,
        )[StreamingService.PRIME]
        assertEquals("https://app.primevideo.com/detail?asin=B00TU9UFTS", prime("B00TU9UFTS"))
        assertEquals("https://app.primevideo.com/detail?gti=amzn1.dv.gti.1", prime("amzn1.dv.gti.1"))
    }

    @Test fun `id з чужими символами в адресу не йде`() =
        assertTrue(parseWikidataLinks("""{"results":{"bindings":[{"netflix":{"value":"1/../x?y"}}]}}""", TitleKind.MOVIE).isEmpty())

    @Test fun `порожньо чи зламано — нічого`() {
        assertTrue(parseWikidataLinks("""{"results":{"bindings":[]}}""", TitleKind.MOVIE).isEmpty())
        assertTrue(parseWikidataLinks("<html>", TitleKind.MOVIE).isEmpty())
    }

    @Test fun `запит — за властивістю TMDB свого типу`() {
        assertTrue("wdt:P4947 \"550\"" in wikidataSparql(550, TitleKind.MOVIE))
        assertTrue("wdt:P4983 \"1399\"" in wikidataSparql(1399, TitleKind.TV))
    }
}
