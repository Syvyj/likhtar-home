package com.likhtar.home.core.model

/**
 * Стрімінги — одна таблиця на весь лаунчер. Досі той самий сервіс жив у
 * дванадцяти місцях під трьома ключами («prime», «amazon», «amazonprimevideo»), з різними
 * пакетами й назвами, і правка в одному не доходила до решти.
 *
 * [key] — ключ сторінки сервісу на сервері (`/rows?brand=`), його ж шлемо в `&apps=`;
 * `null` — своєї сторінки в Кінозалі в сервісу немає. [packages] — застосунки на ТБ,
 * упорядковані: першим той, що стоїть найчастіше. [providers] — назви провайдера в TMDB
 * після [providerKey]: TMDB пише «Netflix basic with Ads», «Amazon Prime Video».
 */
enum class StreamingService(
    val key: String?,
    val title: String,
    val packages: List<String>,
    val providers: Set<String>,
) {
    NETFLIX("netflix", "Netflix", listOf("com.netflix.ninja"), setOf("netflix", "netflixbasicwithads")),
    PRIME(
        "amazon",
        "Prime Video",
        listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        setOf("amazonprimevideo", "amazonvideo", "prime", "amazon"),
    ),
    DISNEY("disney", "Disney+", listOf("com.disney.disneyplus"), setOf("disney", "disneyplus")),
    HBO("hbo", "Max", listOf("com.wbd.stream", "com.hbo.hbonow"), setOf("max", "hbomax")),
    APPLE(
        "apple",
        "Apple TV+",
        listOf("com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv"),
        setOf("appletv", "appletvplus", "itunes"),
    ),
    // ponytail: пакет SkyShowtime на ТБ не перевірений на пристрої — звірити, коли поставлять.
    SKYSHOWTIME(
        "sky_showtime",
        "SkyShowtime",
        listOf("com.skyshowtime.skyshowtime.google", "com.skyshowtime.skyshowtime"),
        setOf("skyshowtime"),
    ),
    PARAMOUNT("paramount", "Paramount+", listOf("com.cbs.ott"), setOf("paramountplus", "paramountpluspremium")),
    // `tv.wuaki.apptv` першим: саме він claim'ить `rakuten.tv` і стоїть на приставках
    // (`pm query-activities`). Решта — неперевірені кандидати, лишаємо.
    RAKUTEN(null, "Rakuten TV", listOf("tv.wuaki.apptv", "tv.rakuten.android", "com.rakuten.tv"), setOf("rakutentv")),
    MEGOGO(null, "MEGOGO", listOf("com.megogo.application"), setOf("megogo")),
    SWEET(null, "SWEET.TV", listOf("tv.sweet.tvplayer"), setOf("sweettv")),
    // ТБ-версія першою; телефонна — запасна, на деяких приставках стоїть лише вона.
    YOUTUBE(null, "YouTube", listOf("com.google.android.youtube.tv", "com.google.android.youtube"), setOf("youtube")),
    ;

    /** Головний пакет: ним вмикаємо й за ним шукаємо. */
    val packageName: String get() = packages.first()

    companion object {
        /** Сервіси зі сторінкою в Кінозалі. */
        val withPage: List<StreamingService> get() = entries.filter { it.key != null }

        fun byProvider(name: String): StreamingService? = providerKey(name).let { k -> entries.firstOrNull { k in it.providers } }

        /**
         * Чи це офіційний сервіс: сервер кладе в `services` і сервіси, і студії озвучки
         * («dniprofilm»), а збірка для магазину спирається лише на перші (Distribution).
         */
        fun isOfficial(name: String): Boolean = byProvider(name) != null

        fun byPackage(packageName: String): StreamingService? = entries.firstOrNull { packageName in it.packages }

        fun providerKey(name: String): String = name.lowercase().replace(Regex("[^a-z0-9+]"), "")
    }
}
