package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.locale
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Готові ряди розділу з НАШОГО каталогу.
 *
 * Досі «Реаліті» будував ряди сам: одинадцять запитів у TMDB discover на кожну перезбірку,
 * а потім відсів по озвучці. Заміряно: із двадцяти позицій ряду «Реаліті-шоу»
 * придатних було нуль — ми платили запитами за те, що самі ж викидали. Причина не в
 * фільтрі: у TMDB немає відповіді на питання «що з цього можна ввімкнути тут».
 *
 * Тепер відповідає сервер, бо саме він знає всі чотири джерела — свої роздачі, портали
 * мовників, ютуб і сервіси. Клієнт лишає собі те, чого сервер не знає: що встановлено на
 * цій приставці й де людина зупинилась.
 */
@Singleton
class SectionRowsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CatalogConfig,
    private val settings: com.likhtar.home.core.data.SettingsRepository,
    private val tmdb: TmdbClient,
    private val curation: LikhtarRows,
) {

    /**
     * Спільні параметри рядів: `&country=PL` — країна людини (див. нижче); `&apps=netflix,amazon` — сервіси, застосунки яких стоять на
     * приставці, коли ввімкнено «Лише з моїх застосунків». Сервер тоді не будує топів і
     * новинок чужих сервісів.
     */
    private suspend fun appsParam(): String {
        val current = settings.settings.first()
        val loc = current.locale
        // Мова — назви рядів і підписи карток (сервер перекладає на виході); українська — без параметра.
        // Публічна збірка шле й невибрану (системну) країну: польський Google TV без майстра бачив
        // ряди України. Особиста — лише вибрану, щоб системна країна приставки не міняла Кінозал.
        val country = (if (loc.chosen || com.likhtar.home.core.model.Distribution.store) "&country=${loc.country}" else "") +
            (if (loc.language != "uk") "&lang=${loc.language}" else "")
        if (!current.onlyMyApps) return country
        val pm = context.packageManager
        val mine = com.likhtar.home.core.model.StreamingService.withPage.filter { svc ->
            svc.packages.any { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
        }.map { it.key }
        // Жодного сервісу — не звужуємо: порожній Кінозал гірший за чужі топи.
        return country + if (mine.isEmpty()) "" else "&apps=" + mine.joinToString(",")
    }

    private val cache = mutableMapOf<String, List<SectionRow>>()

    @Volatile
    private var cachedAt = 0L

    /** Плитки окремо на розділ: у Кінозалі це стрімінги, у Реаліті — мовники й сервіси. */
    private val cachedBrands = mutableMapOf<String, List<SectionBrand>>()

    /** Жанри розділу («Жанри», як в Apple TV) — тією ж відповіддю, що й ряди. */
    private val cachedGenres = mutableMapOf<String, List<SectionGenre>>()

    suspend fun cinemaGenres(onlyPlayable: Boolean): List<SectionGenre> {
        cinema(onlyPlayable)
        return synchronized(cachedGenres) { cachedGenres["cinema"].orEmpty() }
    }

    suspend fun realityGenres(): List<SectionGenre> {
        reality()
        return synchronized(cachedGenres) { cachedGenres["reality"].orEmpty() }
    }

    /** Назва жанру за ключем — для голови його сторінки; з кешу, без мережі. */
    fun genreTitle(key: String): String? = synchronized(cachedGenres) {
        // «genre-comedy@netflix» — жанр усередині сервісу: назва та сама.
        val base = key.substringBefore('@')
        cachedGenres.values.flatten().firstOrNull { it.key == base }?.title
    }

    /**
     * Плитки внизу розділу: чиє це — сервіс чи мовник.
     *
     * Приїжджають тією ж відповіддю, що й ряди: окремий запит заради шести рядків був би
     * другим походом за тим самим.
     */
    suspend fun realityBrands(): List<SectionBrand> {
        reality()
        return synchronized(cachedBrands) { cachedBrands["reality"].orEmpty() }
    }

    /** Плитки Кінозалу — стрімінги. Приїжджають тією ж відповіддю, що й ряди. */
    suspend fun cinemaBrands(onlyPlayable: Boolean): List<SectionBrand> {
        cinema(onlyPlayable)
        val hidden = settings.settings.first().hiddenServices
        return synchronized(cachedBrands) { cachedBrands["cinema"].orEmpty() }.filterNot { it.key in hidden }
    }

    /**
     * Сторінка плитки.
     *
     * Рядів може бути кілька: у наших мовників це одна вибірка, а в світової мережі —
     * її власні підбірки («Нові випуски», «Хіти», «Найкраще за оцінкою»).
     */
    suspend fun brandRows(key: String, limit: Int = BRAND_LIMIT): List<SectionRow> {
        val base = config.streamsUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/rows?section=reality&brand=${java.net.URLEncoder.encode(key, "UTF-8")}&limit=$limit&profile=${who()}${appsParam()}", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Сторінка бренду не приїхала", it) }
                .getOrNull()
        } ?: return emptyList()
        return runCatching { parseRows(body) }.getOrDefault(emptyList())
    }

    /**
     * Сторінка Кінозалу за «Відкрити всі» серверного ряду: «Всі фільми», «Всі серіали»,
     * «Суто українське». Їх немає в клієнтській курації стрімінгів — вони є лише на сервері.
     */
    suspend fun cinemaBrandRows(key: String, limit: Int = BRAND_LIMIT): List<SectionRow> {
        if (com.likhtar.home.core.model.Distribution.store) return openBrandRows(key)
        val base = config.streamsUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/rows?section=cinema&brand=${java.net.URLEncoder.encode(key, "UTF-8")}&limit=$limit&profile=${who()}${appsParam()}", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Сторінка Кінозалу «$key» не приїхала", it) }
                .getOrNull()
        } ?: return emptyList()
        return runCatching { parseRows(body) }.getOrDefault(emptyList())
    }

    /** Профіль — і сторінкам плиток: «не рекомендувати цей» ховає тайтл і там. */
    private suspend fun who(): String =
        java.net.URLEncoder.encode(settings.settings.first().activeProfileId, "UTF-8")

    suspend fun reality(): List<SectionRow> = section("reality", LIMIT)


    /**
     * Ряди Кінозалу з нашого каталогу.
     *
     * Там, де сервер ряд знає, він кращий за TMDB не порядком, а складом: у каталозі 2214
     * придатних фільмів і 1074 серіали, а двадцятка `discover` після нашого ж фільтра по
     * озвучці давала одиниці — це і був ряд «Топи» з пʼяти карток.
     */
    suspend fun cinema(onlyPlayable: Boolean): List<SectionRow> {
        // Сховані в Налаштуваннях сервіси — без їхніх рядів («Нове на …», топи сервісу).
        val hidden = settings.settings.first().hiddenServices
        return section("cinema", LIMIT, onlyPlayable).filterNot { it.openBrand in hidden }
    }

    /** Один ряд Кінозалу на повну сітку — для «Відкрити всі». */
    suspend fun cinemaRow(title: String, onlyPlayable: Boolean, limit: Int = BRAND_LIMIT): SectionRow? =
        section("cinema", limit, onlyPlayable).firstOrNull { it.key == title || it.title == title }

    private suspend fun section(
        name: String,
        limit: Int,
        onlyPlayable: Boolean = false,
    ): List<SectionRow> {
        val now = System.currentTimeMillis()
        // Профіль — у запит і в ключ кешу: «Нові серії» свої в кожного, і ряди одного
        // профілю не мають показуватись іншому.
        val profile = settings.settings.first().activeProfileId
        val apps = appsParam()
        val key = "$name@$limit@$onlyPlayable@$profile$apps@${settings.settings.first().hiddenServices.sorted()}"
        synchronized(cache) {
            // Позначка тайтла («переглянуто», «не рекомендувати») міняє склад рядів одразу.
            if (now - cachedAt > CACHE_MS || cachedAt < TitleMarksRevision.changedAt) {
                cache.clear()
                cachedAt = now
            }
            cache[key]?.let { return it }
        }
        // Два екрани (Головна й Кінозал, ряди й плитки) просили той самий розділ одночасно —
        // і йшли на сервер двічі. Тепер другий чекає на перший і бере
        // відповідь із кешу.
        // ponytail: один замок на всі розділи — паралельні «кіно» й «реаліті» теж ідуть по черзі;
        // замок на ключ, якщо це колись стане помітно.
        return fetchLock.withLock {
            synchronized(cache) { cache[key] }?.let { return@withLock it }
            fetch(name, limit, onlyPlayable, profile, apps, key)
        }
    }

    private val fetchLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun fetch(name: String, limit: Int, onlyPlayable: Boolean, profile: String, apps: String, key: String): List<SectionRow> {
        if (com.likhtar.home.core.model.Distribution.store) {
            return openRows(name).also { if (it.isNotEmpty()) synchronized(cache) { cache[key] = it } }
        }
        val base = config.streamsUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            val playable = if (onlyPlayable) "&playable=1" else ""
            val who = "&profile=" + java.net.URLEncoder.encode(profile, "UTF-8")
            runCatching { httpGet("$base/api/rows?section=$name&limit=$limit$playable$who$apps", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Ряди розділу не приїхали", it) }
                .getOrNull()
        } ?: return synchronized(cache) { cache[key].orEmpty() }
        val rows = runCatching { parseRows(body) }
            .onFailure { Log.w(TAG, "Ряди розділу не розібрались", it) }
            .getOrDefault(emptyList())
        if (rows.isNotEmpty()) {
            synchronized(cache) { cache[key] = rows }
            val brands = runCatching { parseBrands(body) }.getOrDefault(emptyList())
            if (brands.isNotEmpty()) synchronized(cachedBrands) { cachedBrands[name] = brands }
            val genres = runCatching { parseGenres(body) }.getOrDefault(emptyList())
            if (genres.isNotEmpty()) synchronized(cachedGenres) { cachedGenres[name] = genres }
        }
        return rows
    }

    /**
     * Публічна збірка — ряди з відкритих даних TMDB, без нашого сервера. Кінозал: тренди, нове в стрімінгах, чиї застосунки стоять, кіно й
     * найкраще; Реаліті — жанри TMDB неігрового. Регіон — країна людини (buildQuery).
     * Плитки сервісів і жанрів — теж звідси: [openBrandRows] будує їхні сторінки з TMDB.
     */
    private suspend fun openRows(name: String): List<SectionRow> = kotlinx.coroutines.coroutineScope {
        val country = settings.settings.first().locale.country
        val defs: List<Pair<String, List<Pair<String, Map<String, String>>>>> = when (name) {
            "cinema" -> buildList {
                add(SLIDER to listOf("trending/all/week" to emptyMap()))
                add(context.getString(R.string.catalog_starter_movies) to listOf("trending/movie/week" to emptyMap()))
                add(context.getString(R.string.catalog_starter_tv) to listOf("trending/tv/week" to emptyMap()))
                // Нове в сервісах, що стоять на пристрої: ряди курації без зашитого регіону.
                val installed = installedServices()
                val hidden = settings.settings.first().hiddenServices
                curation.load().studios.filter { it.id in installed && it.id !in hidden }.forEach { studio ->
                    val provider = OPEN_PROVIDERS[studio.id] ?: return@forEach
                    add(context.getString(R.string.catalog_open_new_on, studio.title) to
                        freshSources(mapOf("with_watch_providers" to provider)))
                }
                add(context.getString(R.string.catalog_open_in_cinemas) to listOf("movie/now_playing" to mapOf("region" to country)))
                add(context.getString(R.string.catalog_open_top_rated) to listOf("movie/top_rated" to emptyMap()))
            }
            "reality" -> {
                // Світова популярність неігрового — це корейські шоу; навперемін зі шоу країни
                // людини (країна походження = її країна, без таблиць під країни).
                fun both(path: String, genres: String, extra: Map<String, String> = emptyMap()): List<Pair<String, Map<String, String>>> {
                    val base = mapOf("with_genres" to genres, "sort_by" to "popularity.desc") + extra
                    return listOf(path to base + ("with_origin_country" to country), path to base)
                }
                listOf(
                    SLIDER to both("discover/tv", "10764|99"),
                    context.getString(R.string.catalog_open_reality) to both("discover/tv", "10764"),
                    context.getString(R.string.catalog_open_talk) to both("discover/tv", "10767"),
                    context.getString(R.string.catalog_open_docuseries) to both("discover/tv", "99"),
                    context.getString(R.string.catalog_open_documentaries) to both("discover/movie", "99", mapOf("vote_count.gte" to "20")),
                )
            }
            else -> emptyList()
        }
        if (name == "cinema") {
            val brands = openBrands()
            synchronized(cachedBrands) { cachedBrands["cinema"] = brands }
            val genres = OPEN_GENRES.map { id ->
                async {
                    val title = tmdb.genreName(id) ?: return@async null
                    val shots = runCatching { tmdb.discover("discover/movie", mapOf("with_genres" to "$id", "sort_by" to "popularity.desc"), 5) }
                        .getOrDefault(emptyList()).mapNotNull { it.backdropPath }
                    SectionGenre("genre-$id", title, shots.firstOrNull(), GENRE_TONES[id % GENRE_TONES.size], shots)
                }
            }.awaitAll().filterNotNull()
            synchronized(cachedGenres) { cachedGenres["cinema"] = genres }
        }
        rowsOf(defs)
    }

    /** Плитки сервісів: спершу ті, чиї застосунки стоять на пристрої. */
    private suspend fun openBrands(): List<SectionBrand> {
        val installed = installedServices()
        val only = settings.settings.first().onlyMyApps
        return curation.load().studios.filter { it.id in OPEN_PROVIDERS && (!only || it.id in installed) }
            .sortedByDescending { it.id in installed }
            .map { SectionBrand(it.id, it.title, count = 0) }
    }

    private fun installedServices(): Set<String> {
        val pm = context.packageManager
        return com.likhtar.home.core.model.StreamingService.withPage
            .filter { svc -> svc.packages.any { runCatching { pm.getPackageInfo(it, 0) }.isSuccess } }
            .mapNotNull { it.key }.toSet()
    }

    /**
     * Сторінка плитки в публічній збірці: сервіс («netflix») чи жанр («genre-35») — ряди TMDB
     * з назвами мовою інтерфейсу. Сервіс — за провайдером перегляду в країні людини.
     */
    private suspend fun openBrandRows(key: String): List<SectionRow> {
        val filter = key.removePrefix("genre-").toIntOrNull()?.let { mapOf("with_genres" to "$it") }
            ?: OPEN_PROVIDERS[key]?.let { mapOf("with_watch_providers" to it) }
            ?: return emptyList()
        val (movies, series) = freshSources(filter)
        val defs = listOf(
            context.getString(R.string.catalog_open_new_movies) to listOf(movies),
            context.getString(R.string.catalog_open_new_series) to listOf(series),
            context.getString(R.string.catalog_open_popular_movies) to listOf("discover/movie" to filter + ("sort_by" to "popularity.desc")),
            context.getString(R.string.catalog_open_popular_series) to listOf("discover/tv" to filter + ("sort_by" to "popularity.desc")),
            context.getString(R.string.catalog_open_top_rated) to listOf(
                "discover/movie" to filter + mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300"),
                "discover/tv" to filter + mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "200"),
            ),
        )
        return rowsOf(defs)
    }

    /**
     * «Нове» без маловідомого: вийшло за пів року й уже має оцінки, за популярністю. Сортування
     * лише за датою виводило першими будь-які свіжі релізи — тамільський бойовик у «Akcja».
     */
    private fun freshSources(filter: Map<String, String>): List<Pair<String, Map<String, String>>> = listOf(
        "discover/movie" to filter + mapOf(
            "primary_release_date.gte" to "{current_date-$FRESH_DAYS}", "primary_release_date.lte" to "{current_date}",
            "vote_count.gte" to "$FRESH_VOTES", "sort_by" to "popularity.desc",
        ),
        "discover/tv" to filter + mapOf(
            "first_air_date.gte" to "{current_date-$FRESH_DAYS}", "first_air_date.lte" to "{current_date}",
            "vote_count.gte" to "$FRESH_VOTES", "sort_by" to "popularity.desc",
        ),
    )

    private suspend fun rowsOf(defs: List<Pair<String, List<Pair<String, Map<String, String>>>>>): List<SectionRow> = kotlinx.coroutines.coroutineScope {
        defs.map { (title, sources) ->
            async {
                // Кілька джерел ряду (нові фільми й нові серіали сервісу) — навперемін.
                val lists = sources.map { (path, params) ->
                    val here = runCatching { tmdb.discover(path, params, LIMIT) }.getOrDefault(emptyList())
                    // Сервісу немає в країні людини — показуємо, що на ньому є (у США каталог
                    // найповніший); чим відкрити — уже справа людини.
                    if (here.isNotEmpty() || "with_watch_providers" !in params) here
                    else FALLBACK_REGIONS.firstNotNullOfOrNull { region ->
                        runCatching { tmdb.discover(path, params + ("watch_region" to region), LIMIT) }.getOrNull()?.takeIf { it.isNotEmpty() }
                    }.orEmpty()
                }
                val items = (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
                    .distinctBy { it.id }.take(LIMIT)
                SectionRow(title, items, russian = emptySet(), ukrainian = emptySet(), open = emptyMap(), extKeys = emptyMap())
            }
        }.awaitAll().filter { it.items.isNotEmpty() }
    }

    internal companion object {
        const val TAG = "LikhtarRows"

        /**
         * Провайдери перегляду TMDB для сторінок сервісів публічної збірки. Не країни — сервіси:
         * доступність у країні людини TMDB звіряє сам (`watch_region`).
         */
        val OPEN_PROVIDERS = mapOf(
            // «|» — будь-який із них: той самий сервіс TMDB у різних країнах веде під різними id
            // (Prime Video — 119 у Європі й 9 у США; Paramount+ у США — 2303/2616, а 531 там
            // давав чотири фільми).
            "netflix" to "8", "apple" to "350", "hbo" to "1899", "amazon" to "119|9",
            "disney" to "337", "paramount" to "531|2303|2616", "sky_showtime" to "1773",
        )

        /** Де шукати каталог сервісу, якого немає в країні людини: SkyShowtime у США немає. */
        val FALLBACK_REGIONS = listOf("US", "PL", "DE")

        /** Вікно «нового» і поріг оцінок, щоб у ньому не було нікому не відомого. */
        const val FRESH_DAYS = 180
        const val FRESH_VOTES = 20

        /** Жанри TMDB для плиток: бойовик, комедія, драма, трилер, фантастика, кримінал, мелодрама, фентезі, жахи, анімація. */
        val OPEN_GENRES = listOf(28, 35, 18, 53, 878, 80, 10749, 14, 27, 16)

        /** Тони плиток жанрів — теплі, у палітрі лаунчера. */
        val GENRE_TONES = listOf(0xFF8A4B2AL, 0xFF6B5A2EL, 0xFF5A3A4AL, 0xFF3E4A3AL, 0xFF4A3E2EL)

        /** Ряд-пул шапки — так його звуть і сервер, і екрани (CinemaViewModel, RealityViewModel). */
        const val SLIDER = "Слайдер"
        // 20 с, а не 8: перший запит після перезапуску сервера збирає всі ряди наново й
        // TMDB холодний, і Кінозал двічі відкрився з самими «Закладками». Поки
        // чекаємо, стоїть екран завантаження — це краще за напівпорожній розділ.
        const val TIMEOUT_MS = 20_000

        /** Сторінка плитки — сітка, тож карток більше, ніж у ряду. */
        const val BRAND_LIMIT = 60

        /** «Відкрити всі»: ряд цілим, розбитий на частини. */
        const val OPEN_ALL_LIMIT = 120

        /** Тридцять карток на ряд: видимих десять, решта — «Відкрити всі» без другого запиту. */
        const val LIMIT = 30

        /** Півгодини: приставка живе тижнями, і кеш «до перезапуску» ховав би нові випуски. */
        const val CACHE_MS = 30 * 60 * 1000L
    }
}

/**
 * Плитка внизу розділу.
 *
 * Це сервіс або мовник, а не студія-виробник, і це замір, а не спрощення: у придатному
 * неігровому 969 позицій, мережа TMDB відома у 83, а позиції MEGOGO й порталів TMDB не
 * знає взагалі — пошук по 15 назвах знайшов дві, і одна з них помилкова.
 */
/** Плитка сервісу. [top] — на його сторінці є «Топ-10» (з нього бере ряд Кінозал). */
data class SectionBrand(val key: String, val title: String, val count: Int, val top: Boolean = false)

/**
 * Плитка жанру: кадр TMDB під кольоровим тоном і назва. [color] — ARGB.
 * [backdrops] — кадри лише цього жанру: плитка щоразу бере інший.
 */
data class SectionGenre(
    val key: String,
    val title: String,
    val backdropPath: String?,
    val color: Long,
    val backdrops: List<String> = emptyList(),
)

/** Ряд від сервера разом із тим, чого немає в самій картці. */
data class SectionRow(
    val title: String,
    val items: List<CatalogItem>,
    /** Позиції, які тримаються на РОСІЙСЬКІЙ доріжці: на картці це інша мітка. */
    val russian: Set<Int>,
    /**
     * Позиції з підтвердженою українською доріжкою.
     *
     * Приїжджає з рядом, бо сервер щойно це й порахував. Клієнт більше не перепитує й не
     * фільтрує вдруге: ряд світовий, а мітка — наша анотація поверх нього.
     */
    val ukrainian: Set<Int>,
    /** Чим картку відкривати: `tmdb` | `portal` | `youtube` | `service`. */
    val open: Map<Int, String>,
    /** Ключ позиції поза TMDB — ним вона зводиться з каталогом порталів на клієнті. */
    val extKeys: Map<Int, String>,
    /**
     * Ключ сторінки за рядом: сервіс або «Українська стрічка».
     *
     * `null` — «Відкрити всі» веде у звичну сітку самого ряду, а не на чужу сторінку.
     */
    val openBrand: String? = null,
    /**
     * Ряд-довідка, що малюється ПІСЛЯ плиток стрімінгів: «Незабаром українською».
     * Ввімкнути з нього нічого, тож на екрані він останній.
     */
    val tail: Boolean = false,
    /** Плитки «Жанрів» — одразу після цього ряду (порядок Кінозалу задає сервер). */
    val genresAfter: Boolean = false,
    /** Офіційний топ сервісу: великі номери на картках. */
    val top: Boolean = false,
    /** Плитки жанрів сервісу замість карток (сторінка стрімінгу). */
    val genreTiles: List<SectionGenre> = emptyList(),
    /** Підпис картки від сервера: «S01E02 · ще 2» у «Нових серіях». */
    val notes: Map<Int, String> = emptyMap(),
    /** Банер франшизи дня на сторінці «Фільми». */
    val spotlight: RowSpotlight? = null,
    /**
     * Первісна назва ряду від сервера (`key`): назва перекладається мовою лаунчера, а шукаємо
     * ряд («Відкрити всі», підбірки) саме за нею. Старий сервер ключа не шле — тоді назва.
     */
    val key: String = title,
    /** Широка секція Кінозалу: `title` | `franchise`. `null` — звичайний ряд. */
    val wide: String? = null,
    /**
     * Сталий номер ряду в каталозі сервісу (з 0); `-1` — ряд не з каталогу («Незабаром»)
     * або старий сервер. Курація сторінки сервісу ставить ряди за ним, а не за позицією:
     * позиція зсувається, щойно сервер вставить свій ряд або відкине порожній.
     */
    val cat: Int = -1,
    /** Порядок сервера і є змістом (сталий перелік франшизи — за виходом): не пересортовувати. */
    val keep: Boolean = false,
)

/**
 * Розбір `/api/rows`.
 *
 * Окремо від репозиторію, щоб межу перевіряв тест: позиція без id або без дати в ряд не
 * потрапляє — відкривати її нічим, а сортувати нема за чим.
 */
internal fun parseRows(body: String): List<SectionRow> {
    val rows = JSONObject(body).optJSONArray("rows") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        val raw = rows.optJSONObject(index) ?: return@mapNotNull null
        val title = raw.optString("title")
        // Ряд плиток жанрів приходить без карток (`items: null`) — він не порожній.
        val array = raw.optJSONArray("items") ?: org.json.JSONArray()
        val items = mutableListOf<CatalogItem>()
        val russian = mutableSetOf<Int>()
        val ukrainian = mutableSetOf<Int>()
        val open = mutableMapOf<Int, String>()
        val keys = mutableMapOf<Int, String>()
        val notes = mutableMapOf<Int, String>()
        // Двічі той самий тайтл (сервер прислав catalog_id двічі) валив лаунчер: ключі
        // Lazy-рядів — id картки («tv-306762» на сторінці стрімінгу).
        val seen = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val card = array.optJSONObject(i) ?: continue
            val id = card.optInt("catalog_id")
            val date = card.optString("date")
            if (id == 0 || date.length < 10) continue
            val item = parseCard(card, id, date)
            if (!seen.add(item.id)) continue
            items += item
            when (card.optString("audio")) {
                "ru" -> russian += id
                // Збірка для магазину: мітка UA — лише коли її підтверджує офіційний сервіс.
                "uk" -> if (!com.likhtar.home.core.model.Distribution.store ||
                    card.strings("services").any(com.likhtar.home.core.model.StreamingService::isOfficial)
                ) {
                    ukrainian += id
                }
            }
            open[id] = card.optString("open")
            card.optString("note").takeIf(String::isNotBlank)?.let { notes[id] = it }
            card.optString("ext_key").takeIf(String::isNotBlank)?.let { keys[id] = it }
        }
        items.forEach(PortalShowHints::remember)
        // Ряд плиток жанрів сторінки сервісу карток не має — лише плитки.
        val tiles = genresOf(raw.optJSONArray("genre_tiles"))
        if ((items.isEmpty() && tiles.isEmpty()) || title.isBlank()) null
        else SectionRow(
            title, items, russian, ukrainian, open, keys,
            openBrand = raw.optString("open_brand").takeIf(String::isNotBlank),
            tail = raw.optBoolean("tail"),
            genresAfter = raw.optBoolean("genres_after"),
            top = raw.optBoolean("top"),
            genreTiles = tiles,
            notes = notes,
            wide = raw.optString("wide").takeIf(String::isNotBlank),
            cat = raw.optInt("cat", -1),
            keep = raw.optBoolean("keep"),
            key = raw.optString("key").ifBlank { title },
            spotlight = raw.optJSONObject("spotlight")?.let { spot ->
                RowSpotlight(
                    backdropPath = spot.optString("backdrop").takeIf(String::isNotBlank),
                    opens = spot.optString("opens"),
                    logoPath = spot.optString("logo").takeIf(String::isNotBlank),
                )
            },
        )
    }
}

/**
 * Картка з відповіді сервера — той самий формат у рядах розділів і в `epg-match`.
 * [date] — дата картки (поява української чи реліз TMDB); рік береться з неї, якщо окремого немає.
 */
internal fun parseCard(card: JSONObject, id: Int = card.optInt("catalog_id"), date: String = card.optString("date")): CatalogItem {
    val kind = if (card.optString("kind") == "movie") TitleKind.MOVIE else TitleKind.TV
    val genres = card.optJSONArray("genres")?.let { list ->
        list.toStringList()
    }.orEmpty()
    return CatalogItem(
        tmdbId = id,
        kind = kind,
        title = card.optString("title"),
        // Ряди з TMDB року окремо не мають — лише дату; без цього в шапці стояло
        // «★ 7.3 · фільм» без року.
        year = card.optInt("year").takeIf { it > 0 }?.toString()
            ?: card.optString("date").take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty(),
        posterPath = card.optString("poster").takeIf(String::isNotBlank),
        backdropPath = card.optString("backdrop").takeIf(String::isNotBlank),
        genres = genres,
        availabilityDate = date,
        note = card.optString("note"),
        // Опис і оцінка приходять тим самим рядом: шапка розділу без опису — це
        // назва на кадрі, а картка без оцінки мовчить про те, чи варто.
        overview = card.optString("overview"),
        rating = card.optDouble("rating", 0.0),
        // Якість і HDR — наших власних джерел: у збірці для магазину їх немає (Distribution).
        quality = if (com.likhtar.home.core.model.Distribution.store) 0 else card.optInt("quality"),
        hdr = if (com.likhtar.home.core.model.Distribution.store) "" else card.optString("hdr"),
        // Логотип назви — від сервера, для кожної картки: сам клієнт питав його лише
        // під фокусом, і в рядах стояла назва текстом.
        logoPath = card.optString("logo").takeIf(String::isNotBlank)?.also { path ->
            TmdbImages.rememberLogoLanguage(path, card.optString("logo_lang"))
        },
        opens = card.optString("opens"),
        showId = portalShowId(card.optString("open"), card.optString("ext_key"), kind),
        channel = card.optString("channel"),
        trailerKey = card.optString("trailer"),
    ).also(PortalShowHints::remember)
}

internal fun parseGenres(body: String): List<SectionGenre> = genresOf(JSONObject(body).optJSONArray("genres"))

private fun genresOf(array: org.json.JSONArray?): List<SectionGenre> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val raw = array.optJSONObject(index) ?: return@mapNotNull null
        val key = raw.optString("key")
        val title = raw.optString("title")
        if (key.isBlank() || title.isBlank()) return@mapNotNull null
        val color = raw.optString("color").removePrefix("#").toLongOrNull(16)?.or(0xFF000000) ?: 0xFF2F6FB0
        val pool = raw.optJSONArray("backdrops")
        SectionGenre(
            key, title, raw.optString("backdrop").takeIf(String::isNotBlank), color,
            pool?.let { a -> (0 until a.length()).map(a::optString).filter(String::isNotBlank) }.orEmpty(),
        )
    }
}

internal fun parseBrands(body: String): List<SectionBrand> {
    val array = JSONObject(body).optJSONArray("brands") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val raw = array.optJSONObject(index) ?: return@mapNotNull null
        val key = raw.optString("key")
        val title = raw.optString("title")
        if (key.isBlank() || title.isBlank()) null
        else SectionBrand(key, title, raw.optInt("count"), raw.optBoolean("top"))
    }
}

/**
 * Адреса сторінки шоу для картки порталу — та сама, що в Реаліті: `serials/<канал>/<slug>`
 * для серіалу Телепорталу, `plus1/<id>` для 1+1. Не портал — порожньо.
 */
/**
 * Що сервер знав про твір порталу, коли показував картку: назва, кадр, опис. Сторінка шоу
 * бере це, коли сам портал не відповів — Телепортал лежить годинами, і сторінка
 * стояла порожньою, хоч картка щойно мала і картинку, і опис.
 */
object PortalShowHints {
    private val hints = java.util.concurrent.ConcurrentHashMap<String, CatalogItem>()
    private val overviews = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun remember(item: CatalogItem) {
        if (item.showId.isNotBlank()) hints[item.showId] = item
        // Опис українською з ряду — для сторінки тайтла, коли TMDB перекладу не має
        // (світове неігрове: опис дав MEGOGO, з якого позицію зіставили).
        if (item.tmdbId > 0 && item.overview.any { it in 'а'..'я' || it in 'А'..'Я' || it in "іїєґІЇЄҐ" }) {
            overviews["${item.kind}:${item.tmdbId}"] = item.overview
        }
    }

    /** Опис з картки ряду; `null` — картку не бачили або опису в неї немає. */
    fun overviewOf(item: CatalogItem): String? = overviews["${item.kind}:${item.tmdbId}"]
    fun of(showId: String): CatalogItem? = hints[showId]
}

internal fun portalShowId(open: String, extKey: String, kind: TitleKind): String = when {
    // Твір YouTube-книги — та сама сторінка шоу, серії з його плейлиста: картка не має
    // вмикати YouTube наосліп, а сторінка TMDB про нього нічого не знає.
    open == "youtube" && extKey.startsWith("nashe:") -> "youtube/" + extKey.removePrefix("nashe:")
    // Позиція MEGOGO / SWEET без TMDB — теж сторінка шоу, а не застосунок наосліп: на ній
    // кнопка сервісу (коли він є) і наш «Дивитись» живим пошуком.
    open == "service" && (extKey.startsWith("megogo:") || extKey.startsWith("sweet:")) -> "service/$extKey"
    open != "portal" -> ""
    extKey.startsWith("teleportal:") ->
        (if (kind == TitleKind.TV) "serials/" else "show/") + extKey.removePrefix("teleportal:")
    extKey.startsWith("1plus1:") -> "plus1/" + extKey.removePrefix("1plus1:")
    else -> ""
}
