package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Розбір `/api/watch`. Тіла — справжні відповіді сервера, зняті curl'ом 01.09.2026.
 *
 * Тести на `transformUrl` (переписування hbomax → play.max.com) прибрані разом із самою
 * функцією: заміряно `pm query-activities`, `com.wbd.stream` claim'ить обидва
 * хости, тож чинити адресу не було потреби.
 */
class WatchOptionsResolverTest {

    /** Скорочена, але справжня відповідь: «Носферату», `country=UA`. */
    private val body = """
        {"countries":["UA"],"counts":{"official":5,"own":4,"torrent":3},"results":[
          {"order":0,"priority":0,"group":"official","service":"megogo","label":"MEGOGO",
           "deeplink":"megogo://video/25891376","url":"https://megogo.net/ua/view/25891376-nosferatu.html",
           "package":"com.megogo.application","packages":["com.megogo.application"]},
          {"order":1,"priority":0,"group":"official","service":"sweet.tv","label":"SWEET.TV",
           "deeplink":"sweettv://movie/32999","url":"https://sweet.tv/ua-uk/movie/32999-x",
           "packages":["tv.sweet.tvplayer"]},
          {"order":2,"priority":0,"group":"official","service":"netflix","label":"Netflix",
           "monetization":"FLATRATE","url":"https://www.netflix.com/title/81928701",
           "packages":["com.netflix.ninja"]},
          {"order":3,"priority":0,"group":"official","service":"wuaki","label":"Rakuten TV",
           "monetization":"RENT","url":"https://www.rakuten.tv/ua/movies/53e015a1",
           "package":"tv.wuaki.apptv","packages":["tv.wuaki.apptv","tv.rakuten.android"]},
          {"order":4,"priority":0,"group":"official","service":"canalpluspremiumpl",
           "monetization":"FLATRATE","url":"https://www.canalplus.com/pl/film/x"},
          {"order":5,"priority":1,"group":"own","service":"site_ua","name":"🇺🇦 1080p · Джерело 2",
           "ref":"https://site.example/films/nosferatu/","quality":1080,
           "packages":["net.gtvbox.videoplayer","com.brouken.player"]},
          {"order":9,"priority":2,"group":"torrent","service":"torrent","quality":2160,
           "ref":"9bef60a14518dec66bd218871664c2379ac3ccb4","name":"🇺🇦 2160p · Торент",
           "packages":["net.gtvbox.videoplayer","com.brouken.player"]}
        ]}
    """.trimIndent()

    @Test
    fun `порядок сервера доживає до опцій, і megogo перед міжнародною підпискою`() {
        val options = parseWatch(body)

        // Це і є те, що сервер вирішив заміром: FLATRATE у сервісі без сесії показує екран
        // входу, а megogo — єдиний, у кого приставка пам'ятає історію відтворення.
        assertEquals(listOf("megogo", "sweet.tv", "netflix", "wuaki"), options.map { it.service })
        assertEquals(listOf(0, 1, 2, 3), options.map { it.order })
    }

    @Test
    fun `для megogo і sweet tv відкриваємо deeplink, а не адресу сайту`() {
        // `megogo.net` не claim'ить жоден застосунок, крім вбудованого браузера sweet.tv:
        // взята з відповіді веб-адреса відкрила б сайт, і виглядало б це як «запустився не той».
        val options = parseWatch(body)
        assertEquals("megogo://video/25891376", options.first { it.service == "megogo" }.deeplink)
        assertEquals("sweettv://movie/32999", options.first { it.service == "sweet.tv" }.deeplink)
        assertTrue(options.none { it.service == "megogo" && it.deeplink.startsWith("http") })
    }

    @Test
    fun `відкидаємо те, чим нічого відкрити`() {
        val services = parseWatch(body).map { it.service }
        // canalplus: пакета немає й не буде — застосунку на Android TV у нього немає.
        assertTrue("canalpluspremiumpl" !in services)
        // own і torrent несуть `ref`, а не адресу потоку: їх готує LikhtarStreams, не картка.
        assertTrue(services.none { it == "site_ua" || it == "torrent" })
    }

    @Test
    fun `кандидати в пакетах ідуть у порядку сервера, старий package теж читається`() {
        val options = parseWatch(body)
        // Перший кандидат заміряний — порядок сервера не переставляємо.
        assertEquals(
            listOf("tv.wuaki.apptv", "tv.rakuten.android"),
            options.first { it.service == "wuaki" }.packages,
        )
        // Відповідь лише з `package` мусить читатися далі: форма змінювалась на живому сервері.
        assertEquals(
            listOf("tv.sweet.tvplayer"),
            options.first { it.service == "sweet.tv" }.packages,
        )
    }

    @Test
    fun `порожня відповідь не є помилкою`() {
        assertEquals(emptyList<ResolvedOption>(), parseWatch("""{"countries":["UA"]}"""))
        assertEquals(emptyList<ResolvedOption>(), parseWatch("""{"results":[]}"""))
    }
}
