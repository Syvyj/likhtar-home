package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.ContentLocale
import com.likhtar.home.core.data.http
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Клієнт TMDB — джерело каталогу.
 *
 * Мова й регіон зашиті як `uk-UA`: лаунчер українськомовний, і саме для UA має сенс питати
 * `watch/providers` (де насправді доступний фільм у цій країні).
 *
 * Ключ приходить із налаштувань, а не з коду: у релізі його вводить користувач. Порожній
 * ключ означає «каталог вимкнено» — усі методи мовчки віддають порожнє, і Кінозал показує
 * пояснення замість помилки.
 */
@Singleton
class TmdbClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CatalogConfig,
) {
    /**
     * Назви жанрів за id — словник TMDB українською, той самий, яким говорить наш сервер.
     *
     * `discover` віддає лише `genre_ids`, тож без цього словника картка з ряду не знає
     * свого жанру, а розкладка «Реаліті» будується саме на ньому. Читаємо один раз на
     * процес: два запити (фільми й серіали) на весь сеанс.
     */
    private val genreNames = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /**
     * Паралельні `discover` (ряд на кожну добірку) раніше всі разом бачили порожній словник
     * і всі разом його качали — десяток однакових запитів і гонка на звичайному HashMap
     *. Тепер вантажить перший, решта чекає на нього.
     */
    private val genreLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Не більше шести запитів до TMDB разом: екран Кінозалу, хронологія франшизи й картки
     * віялом слали десятки одночасно, і 1 ГБ приставки захлиналась потоками й сокетами.
     */
    private val requests = kotlinx.coroutines.sync.Semaphore(6)

    private suspend fun loadGenreNames(key: String) = genreLock.withLock {
        if (genreNames.isNotEmpty()) return@withLock
        listOf("movie", "tv").forEach { kind ->
            val body = get("${config.tmdbApi()}/genre/$kind/list?api_key=$key&language=${config.locale().tmdbLanguage}") ?: return@forEach
            runCatching {
                val array = JSONObject(body).optJSONArray("genres") ?: return@runCatching
                (0 until array.length()).forEach { index ->
                    val raw = array.optJSONObject(index) ?: return@forEach
                    val id = raw.optInt("id")
                    val name = raw.optString("name")
                    if (id > 0 && name.isNotBlank()) genreNames[id] = name
                }
            }.onFailure { Log.w(TAG, "Словник жанрів не розібрався", it) }
        }
    }

    /** Назва жанру мовою інтерфейсу — для плиток «Жанрів» публічної збірки. */
    suspend fun genreName(id: Int): String? {
        val key = config.tmdbKey() ?: return null
        loadGenreNames(key)
        return genreNames[id]
    }

    /** id жанрів за назвами картки — тим самим словником, тією ж мовою, що й назви. */
    suspend fun genreIds(names: List<String>): Set<Int> {
        if (names.isEmpty()) return emptySet()
        val key = config.tmdbKey() ?: return emptySet()
        loadGenreNames(key)
        return genreNames.filterValues { it in names }.keys
    }

    suspend fun discover(path: String, params: Map<String, String>, limit: Int): List<CatalogItem> {
        val key = config.tmdbKey() ?: return emptyList()
        loadGenreNames(key)
        // {current_date} — підстановка з курації Ліхтаря: «нове, але вже вийшло».
        // {current_date-120} — те саме назад: ковзне вікно свіжості.
        //
        // Рахуємо ТУТ, на кожен виклик, а не константою в описі ряду. Константа
        // обчислилась би при завантаженні класу й замерзла на весь час життя процесу, а
        // приставка не перезавантажується тижнями: «останні 120 днів» тихо ставало б
        // «останні 120 днів станом на позаминулий вівторок».
        //
        // Дата рахується раз на виклик: спільний SimpleDateFormat не потокобезпечний,
        // а discover ходить із паралельних корутин.
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = Date()
        val today = format.format(now)
        val resolved = params.mapValues { (_, value) ->
            DATE_BACK.replace(value.replace("{current_date}", today)) { match ->
                val back = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.DAY_OF_YEAR, -(match.groupValues[1].toIntOrNull() ?: 0))
                }
                format.format(back.time)
            }
        }
        val query = buildQuery(key, resolved, config.locale())
        val body = get("${config.tmdbApi()}/$path?$query") ?: return emptyList()

        return runCatching { parseResults(body, defaultKind(path)) }
            .onFailure { Log.w(TAG, "Не вдалося розібрати відповідь TMDB для $path", it) }
            .getOrDefault(emptyList())
            .take(limit)
    }

    suspend fun search(query: String, limit: Int): List<CatalogItem> {
        val key = config.tmdbKey()
            ?: throw IllegalStateException("TMDB API key is not configured")
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        if (encoded.isEmpty()) return emptyList()
        val body = getRequired(
            "${config.tmdbApi()}/search/multi?api_key=$key&language=${config.locale().tmdbLanguage}&region=${config.locale().country}&query=$encoded",
        )

        return parseResults(body, null, query)
            .take(limit)
    }

    /**
     * «Ліхтарик»-радник потоком: найкращий вибір, думка шматками й решта карток — щойно
     * модель їх написала, а сервер звірив з TMDB. Картки розбираємо тим самим кодом і
     * фільтрами, що й звичайний пошук.
     */
    fun ask(query: String): Flow<AskEvent> = flow {
        val base = config.homeOnlyUrl() ?: return@flow
        val url = "$base/api/ask?stream=1&q=" + URLEncoder.encode(query.trim(), "UTF-8")
        http(url, TIMEOUT_MS, ASK_TIMEOUT_MS) { connection ->
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    when (json.optString("type")) {
                        "thought" -> emit(AskEvent.Thought(json.optString("text")))
                        "thought2" -> emit(AskEvent.Refined(json.optString("text")))
                        "best", "item" -> {
                            val raw = json.optJSONObject("item") ?: continue
                            val item = runCatching { parseItems(JSONArray().put(raw), null).firstOrNull() }.getOrNull() ?: continue
                            emit(AskEvent.Found(json.optInt("rank"), item, raw.optString("why")))
                        }
                        "done" -> break
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Щоденні добірки профілю — кожна рядом: назва, чому саме вона, картки з «чому». */
    suspend fun shelves(profile: String): List<Advice> {
        val base = config.homeOnlyUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            httpGet("$base/api/shelves?profile=" + URLEncoder.encode(profile, "UTF-8"), TIMEOUT_MS)
        } ?: return emptyList()
        return runCatching {
            val array = JSONObject(body).optJSONArray("shelves") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { shelf ->
                val raw = shelf.optJSONArray("results") ?: JSONArray()
                Advice(
                    thought = shelf.optString("reason"),
                    title = shelf.optString("title"),
                    items = parseItems(raw, null),
                    why = whyOf(raw),
                )
            }.filter { it.items.size >= 3 }
        }.getOrDefault(emptyList())
    }

    /** «Чому» до карток за ключем, як у CatalogItem.id: фільтри могли проріджити масив. */
    private fun whyOf(raw: JSONArray): Map<String, String> = (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
        .associate { "${if (it.optString("media_type") == "tv") "tv" else "movie"}-${it.optInt("id")}" to it.optString("why") }

    /**
     * Переказ сезону українською: текст або пояснення, чому його нема. Сервер пише його за
     * описами серій (перший раз — до хвилини) і далі віддає з кешу.
     */
    suspend fun recap(tmdbId: Int, season: Int): Pair<String, String> {
        val base = config.streamsUrl() ?: return "" to context.getString(R.string.catalog_recap_no_server)
        val body = withContext(Dispatchers.IO) {
            httpGet("$base/api/recap?tmdb_id=$tmdbId&season=$season", RECAP_TIMEOUT_MS)
        } ?: return "" to context.getString(R.string.catalog_recap_no_reply)
        return runCatching { JSONObject(body).let { it.optString("text") to it.optString("error") } }
            .getOrDefault("" to context.getString(R.string.catalog_recap_failed))
    }

    /** Відео-переказ сезону українською — лише перевірені сервером за назвою. */
    suspend fun recapVideos(tmdbId: Int, season: Int): List<TitleVideo> {
        val base = config.streamsUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            httpGet("$base/api/recap?tmdb_id=$tmdbId&season=$season&part=video", TIMEOUT_MS)
        } ?: return emptyList()
        return runCatching {
            val array = JSONObject(body).optJSONArray("videos") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
                TitleVideo(key = it.optString("key"), name = it.optString("name"), type = context.getString(R.string.catalog_recap_season, season))
            }.filter { it.key.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    /** Український трейлер, який сервер знайшов і перевірив за назвою; `null` — немає. */
    suspend fun uaTrailer(tmdbId: Int, kind: TitleKind): TitleVideo? {
        val base = config.streamsUrl() ?: return null
        val body = withContext(Dispatchers.IO) {
            httpGet("$base/api/trailer?tmdb_id=$tmdbId&kind=${kind.tmdbPath}", TIMEOUT_MS)
        } ?: return null
        return runCatching {
            val json = JSONObject(body)
            json.optString("key").takeIf(String::isNotBlank)?.let {
                TitleVideo(key = it, name = json.optString("name").ifBlank { context.getString(R.string.catalog_trailer) }, type = "Trailer", language = "uk")
            }
        }.getOrNull()
    }

    /**
     * «Ліхтарик радить» за тим, що дивились і відклали: одна відповідь, не потоком. Лише домашній
     * сервер: публічний шлюз `/api/ask` не пропускає (404 на кожен виклик).
     */
    suspend fun advise(like: List<String>): Advice {
        val base = config.homeOnlyUrl() ?: return Advice()
        val body = withContext(Dispatchers.IO) {
            httpGet("$base/api/ask?like=" + URLEncoder.encode(like.joinToString("|"), "UTF-8"), ASK_TIMEOUT_MS)
        } ?: return Advice()
        return runCatching {
            val json = JSONObject(body)
            val raw = json.optJSONArray("results") ?: JSONArray()
            val items = parseItems(raw, null)
            Advice(json.optString("thought"), items = items, why = whyOf(raw))
        }.getOrDefault(Advice())
    }

    /**
     * Один фільм за назвою й роком — так хронології зберігаються назвами, а не id.
     *
     * Ліхтар робить те саме (`search/movie?query=…&year=…`, перший результат): id у списку
     * не перевірити очима, а помилка в ньому мовчки підсунула б не той фільм.
     */
    suspend fun findMovie(title: String, year: Int): CatalogItem? {
        val key = config.tmdbKey() ?: return null
        val encoded = URLEncoder.encode(title.trim(), "UTF-8")
        if (encoded.isEmpty()) return null
        val yearParam = if (year > 0) "&year=$year" else ""
        val body = get("${config.tmdbApi()}/search/movie?api_key=$key&language=${config.locale().tmdbLanguage}&query=$encoded$yearParam")
            ?: return null

        return runCatching { parseResults(body, TitleKind.MOVIE) }.getOrDefault(emptyList()).firstOrNull()
    }

    /**
     * Медіатека: фільми чи серіали за назвою (й роком, якщо є) — увесь перший аркуш, бо
     * однакових назв буває кілька («Темна матерія» 2015 і 2024), і вибирає викликач.
     */
    suspend fun searchKind(kind: TitleKind, title: String, year: Int): List<CatalogItem> {
        val key = config.tmdbKey() ?: return emptyList()
        val encoded = URLEncoder.encode(title.trim(), "UTF-8")
        if (encoded.isEmpty()) return emptyList()
        val yearParam = when {
            year <= 0 -> ""
            kind == TitleKind.TV -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        val body = get("${config.tmdbApi()}/search/${kind.tmdbPath}?api_key=$key&language=${config.locale().tmdbLanguage}&query=$encoded$yearParam")
            ?: return emptyList()
        return runCatching { parseResults(body, kind) }.getOrDefault(emptyList())
    }

    /** Франшиза: назва колекції та її частини в порядку виходу. */
    suspend fun collection(id: Int): TmdbCollection? {
        val key = config.tmdbKey() ?: return null
        val body = get("${config.tmdbApi()}/collection/$id?api_key=$key&language=${config.locale().tmdbLanguage}") ?: return null

        return runCatching {
            val json = JSONObject(body)
            val name = json.optString("name")
            val parts = parseItems(json.optJSONArray("parts") ?: return@runCatching null, TitleKind.MOVIE)
                // Частини франшизи TMDB віддає в довільному порядку, а сенс списку — саме
                // послідовність; без року в кінець, інакше анонс без дати відкриває франшизу.
                .sortedBy { it.year.toIntOrNull() ?: Int.MAX_VALUE }
            TmdbCollection(name, json.optString("backdrop_path").takeIf(String::isNotBlank), parts)
        }.onFailure { Log.w(TAG, "Не вдалося розібрати франшизу $id", it) }.getOrNull()
    }

    /** Постер колекції без напису (мова «null»): на ньому стоїть наше лого. */
    suspend fun textlessCollectionPoster(id: Int): String? {
        val key = config.tmdbKey() ?: return null
        val body = get("${config.tmdbApi()}/collection/$id/images?api_key=$key&include_image_language=null") ?: return null
        return runCatching {
            JSONObject(body).optJSONArray("posters")?.optJSONObject(0)?.optString("file_path")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private val logoCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Пошук логотипу фільму/серіалу: пріоритет UA -> EN -> без мови -> перше доступне. */
    suspend fun logo(item: CatalogItem): String? {
        val cacheKey = "${item.kind.name}-${item.tmdbId}"
        val cached = logoCache[cacheKey]
        if (cached != null) return cached.takeIf { it.isNotEmpty() }

        val key = config.tmdbKey() ?: return null
        val url = "${config.tmdbApi()}/${item.kind.tmdbPath}/${item.tmdbId}/images?api_key=$key&include_image_language=${config.locale().language},en,null"
        val body = get(url) ?: return null
        val logo = runCatching { selectBestLogo(JSONObject(body), config.locale().language) }.getOrNull()

        logoCache[cacheKey] = logo.orEmpty()
        return logo
    }

    /** Деталі: тривалість, жанри, країни, вікове обмеження й лого одним запитом. */
    /**
     * Одна картка за справжнім id TMDB.
     *
     * Потрібна українським рядам: позиції нашого каталогу приходять без зображень, і кадр
     * для них є лише тут. Ходимо ЛИШЕ зі справжнім `tmdb_id` — для позицій, у яких його
     * немає, картки не буде взагалі, бо з `catalog_id` у TMDB шукати нічого.
     */
    suspend fun card(tmdbId: Int, kind: TitleKind): CatalogItem? {
        val key = config.tmdbKey() ?: return null
        val body = get("${config.tmdbApi()}/${kind.tmdbPath}/$tmdbId?api_key=$key&language=${config.locale().tmdbLanguage}") ?: return null
        return runCatching {
            val json = JSONObject(body)
            val item = parseItems(JSONArray().put(json), kind).firstOrNull() ?: return@runCatching null
            // Сторінка тайтла віддає `genres` об'єктами з назвами, а список — `genre_ids`
            // числами. `parseItems` знає лише друге, тому на картці жанри лишались
            // порожніми — і фільтр «неігрове» відсіював УСЕ, що приходило цим шляхом.
            val names = json.optJSONArray("genres")?.let { array ->
                (0 until array.length()).mapNotNull {
                    array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)
                }
            }.orEmpty()
            if (names.isEmpty()) item else item.copy(genres = names)
        }.onFailure { Log.w(TAG, "Картка $tmdbId не розібралась", it) }.getOrNull()
    }

    private val lastAir = java.util.concurrent.ConcurrentHashMap<Int, String>()

    @Volatile
    private var lastAirAt = 0L

    suspend fun details(item: CatalogItem): TitleDetails {
        val key = config.tmdbKey() ?: return TitleDetails(item)
        val append = if (item.kind == TitleKind.MOVIE) {
            "release_dates,images,credits,videos,recommendations"
        } else {
            "content_ratings,images,aggregate_credits,videos,recommendations"
        }
        val url = "${config.tmdbApi()}/${item.kind.tmdbPath}/${item.tmdbId}" +
            "?api_key=$key&language=${config.locale().tmdbLanguage}&append_to_response=$append" +
                "&include_image_language=${config.locale().language},en,null&include_video_language=${config.locale().language},en,null"
        val body = get(url) ?: return TitleDetails(item)

        val region = config.locale().country
        val language = config.locale().language
        return runCatching { parseDetails(item, JSONObject(body), region, language) }
            .onFailure { Log.w(TAG, "Не вдалося розібрати деталі ${item.tmdbId}", it) }
            .getOrDefault(TitleDetails(item))
    }

    /** Серії сезону: кадр, назва, опис, тривалість і дата — картки епізодів на сторінці. */
    suspend fun seasonEpisodes(item: CatalogItem, season: Int): List<Episode> {
        if (item.kind != TitleKind.TV) return emptyList()
        val key = config.tmdbKey() ?: return emptyList()
        val body = get("${config.tmdbApi()}/tv/${item.tmdbId}/season/$season?api_key=$key&language=${config.locale().tmdbLanguage}")
            ?: return emptyList()
        return runCatching {
            val array = JSONObject(body).optJSONArray("episodes") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { index ->
                val raw = array.optJSONObject(index) ?: return@mapNotNull null
                Episode(
                    number = raw.optInt("episode_number"),
                    title = raw.optString("name").ifBlank { "Епізод ${raw.optInt("episode_number")}" },
                    overview = raw.optString("overview"),
                    stillPath = raw.optString("still_path").takeIf { it.isNotBlank() && it != "null" },
                    runtimeMinutes = raw.optInt("runtime"),
                    airDate = raw.optString("air_date"),
                )
            }
        }.onFailure { Log.w(TAG, "Серії s$season для ${item.tmdbId} не розібрались", it) }
            .getOrDefault(emptyList())
    }

    /** Назви сервісів, де фільм доступний у регіоні UA. Дані JustWatch — атрибуція обов'язкова. */
    suspend fun watchProviders(item: CatalogItem): List<String> {
        val key = config.tmdbKey() ?: return emptyList()
        val body = get("${config.tmdbApi()}/${item.kind.tmdbPath}/${item.tmdbId}/watch/providers?api_key=$key")
            ?: return emptyList()

        return runCatching {
            val region = JSONObject(body).optJSONObject("results")?.optJSONObject(config.locale().country)
                ?: return@runCatching emptyList()
            // flatrate — підписка, ads — з рекламою; оренду й купівлю не показуємо: у лаунчері
            // немає платіжного шляху, а «доступно за 4 €» на домашньому екрані лише дратує.
            listOf("flatrate", "ads", "free").flatMap { kind ->
                val array = region.optJSONArray(kind) ?: return@flatMap emptyList()
                (0 until array.length()).mapNotNull {
                    array.optJSONObject(it)?.optString("provider_name")?.takeIf(String::isNotBlank)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    /**
     * Тип за шляхом запиту.
     *
     * Не лише `discover/tv`: списки TMDB звуться `tv/on_the_air`, `movie/now_playing` — тобто
     * тип стоїть на початку. Без цього серіали з `tv/on_the_air` приїжджали як фільми, і
     * сторінка відкривала б їх не тим ендпоінтом.
     */
    private fun defaultKind(path: String): TitleKind? = when {
        path.endsWith("/tv") || path.startsWith("tv/") -> TitleKind.TV
        path.endsWith("/movie") || path.startsWith("movie/") -> TitleKind.MOVIE
        else -> null
    }

    private fun buildQuery(key: String, params: Map<String, String>, loc: ContentLocale): String {
        val all = linkedMapOf(
            "api_key" to key,
            "language" to loc.tmdbLanguage,
            "watch_region" to loc.country,
            "without_original_language" to "ru",
            "without_origin_country" to "RU,SU",
        )
        all.putAll(params)
        return all.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    private fun parseResults(body: String, kind: TitleKind?, searchQuery: String? = null): List<CatalogItem> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return parseItems(results, kind, searchQuery)
    }

    private fun parseItems(
        results: JSONArray,
        kind: TitleKind?,
        searchQuery: String? = null,
    ): List<CatalogItem> {
        val items = (0 until results.length()).mapNotNull { index ->
            val raw = results.optJSONObject(index) ?: return@mapNotNull null

            // Пропускаємо все, крім фільмів і серіалів (наприклад, TMDB person / акторів)
            val mediaType = raw.optString("media_type")
            if (kind == null && mediaType.isNotBlank() && mediaType != "movie" && mediaType != "tv") {
                return@mapNotNull null
            }

            // Відсікаємо сміття без постерів (порожні плашки без зображень)
            val posterPath = raw.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
            if (posterPath == null) return@mapNotNull null

            // Повна фільтрація російського та радянського контенту:
            // 1. За мовою оригіналу (ru)
            val originalLanguage = raw.optString("original_language").lowercase()
            if (originalLanguage in BLOCKED_LANGUAGES) return@mapNotNull null

            // 2. За країною походження (RU, SU)
            val originCountries = raw.optJSONArray("origin_country")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it).uppercase() }
            }.orEmpty()
            if (originCountries.any { it in BLOCKED_COUNTRIES }) return@mapNotNull null

            // 3. За наявністю специфічних російських літер в оригінальній назві
            val origTitle = raw.optString("original_title").ifBlank { raw.optString("original_name") }
            if (origTitle.any { it in RUSSIAN_LETTERS }) return@mapNotNull null

            // 4. Відсікаємо непопулярні сторонні архіви (наприклад, болгарські/сербські записи 60-х без глядачів)
            val popularity = raw.optDouble("popularity", 0.0)
            val voteCount = raw.optInt("vote_count", 0)
            if (originalLanguage != "uk" && originalLanguage != "en" && popularity < 0.6 && voteCount < 2) {
                return@mapNotNull null
            }

            val itemKind = kind ?: if (mediaType == "tv") TitleKind.TV else TitleKind.MOVIE
            val id = raw.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            // У фільмів назва в `title`, у серіалів у `name` — і навпаки для дати.
            val title = raw.optString("title").ifBlank { raw.optString("name") }
            if (title.isBlank()) return@mapNotNull null
            val date = raw.optString("release_date").ifBlank { raw.optString("first_air_date") }

            SearchCandidate(
                item = CatalogItem(
                tmdbId = id,
                kind = itemKind,
                title = title,
                originalTitle = origTitle,
                year = date.take(4),
                releaseDate = date,
                posterPath = posterPath,
                backdropPath = raw.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                rating = raw.optDouble("vote_average", 0.0),
                overview = raw.optString("overview"),
                genres = raw.optJSONArray("genre_ids")?.let { ids ->
                    (0 until ids.length()).mapNotNull { genreNames[ids.optInt(it)] }
                }.orEmpty(),
                anime = raw.optJSONArray("genre_ids")?.let { ids -> (0 until ids.length()).any { ids.optInt(it) == ANIMATION_GENRE } } == true &&
                    // Японське аніме й китайське (донхуа).
                    (originalLanguage in ANIME_LANGUAGES || originCountries.any { it in ANIME_COUNTRIES }),
                ),
                originalIndex = index,
                popularity = popularity,
                voteCount = voteCount,
            )
        }

        return if (searchQuery == null) {
            items.map { it.item }
        } else {
            SearchRelevance.sort(searchQuery, items)
        }
    }

    private fun selectBestLogo(json: JSONObject, language: String): String? {
        val array = json.optJSONObject("images")?.optJSONArray("logos")
            ?: json.optJSONArray("logos")
            ?: return null
        val logos = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        if (logos.isEmpty()) return null
        fun lang(o: JSONObject) = if (o.isNull("iso_639_1")) "" else o.optString("iso_639_1")

        // Порядок: мовою контенту → англійське → без мови → перше доступне.
        val best = logos.firstOrNull { lang(it).equals(language, ignoreCase = true) }
            ?: logos.firstOrNull { lang(it).equals("en", ignoreCase = true) }
            ?: logos.firstOrNull { lang(it).isBlank() }
            ?: logos.first()
        val path = best.optString("file_path").takeIf { it.isNotBlank() } ?: return null
        // Мову запам'ятовуємо разом зі шляхом: за нею шапка вирішує, чи підписати знак
        // українською назвою.
        TmdbImages.rememberLogoLanguage(path, lang(best).lowercase())
        return path
    }

    private fun parseDetails(item: CatalogItem, json: JSONObject, region: String, language: String): TitleDetails {
        // На сторінку фільму приходить лише тип і id, тому решту картки добираємо звідси.
        val logo = selectBestLogo(json, language) ?: item.logoPath

        val title = json.optString("title").ifBlank { json.optString("name") }
        val date = json.optString("release_date").ifBlank { json.optString("first_air_date") }

        val filled = item.copy(
            title = title.ifBlank { item.title },
            originalTitle = json.optString("original_title")
                .ifBlank { json.optString("original_name") }
                .ifBlank { item.originalTitle },
            year = date.take(4).ifBlank { item.year },
            posterPath = json.optString("poster_path").takeIf { it.isNotBlank() && it != "null" } ?: item.posterPath,
            backdropPath = json.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" } ?: item.backdropPath,
            logoPath = logo ?: item.logoPath,
            rating = json.optDouble("vote_average", item.rating),
            overview = json.optString("overview").ifBlank { item.overview },
        )

        val genres = json.optJSONArray("genres")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name") }
        }.orEmpty()

        val countries = json.optJSONArray("origin_country")?.let { array ->
            array.toStringList()
        }.orEmpty()

        val runtime = json.optInt("runtime").takeIf { it > 0 }
            ?: json.optJSONArray("episode_run_time")?.optInt(0) ?: 0

        // Франшиза — це поле самого TMDB: якщо фільм належить колекції, у ній лежать усі частини.
        val franchise = json.optJSONObject("belongs_to_collection")?.let { collection ->
            val id = collection.optInt("id").takeIf { it > 0 } ?: return@let null
            FranchiseRef(id = id, name = collection.optString("name").ifBlank { context.getString(R.string.catalog_franchise) })
        }

        // «У ролях» і режисер: у фільмів — credits, у серіалів — aggregate_credits
        // (звичайні credits віддають лише останній сезон).
        val credits = json.optJSONObject("credits") ?: json.optJSONObject("aggregate_credits")
        val cast = credits?.optJSONArray("cast")?.let { array ->
            (0 until minOf(array.length(), 3))
                .mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
        }.orEmpty()
        // Актори з фото: у фільмів роль — character, у серіалів — roles[0].character.
        val people = credits?.optJSONArray("cast")?.let { array ->
            (0 until minOf(array.length(), 12)).mapNotNull { i ->
                val p = array.optJSONObject(i) ?: return@mapNotNull null
                val name = p.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val role = p.optString("character").ifBlank {
                    p.optJSONArray("roles")?.optJSONObject(0)?.optString("character").orEmpty()
                }
                CastMember(name, role, p.optString("profile_path").takeIf { it.isNotBlank() && it != "null" })
            }
        }.orEmpty()
        // У фільмів job лежить на людині, у серіалів (aggregate_credits) — у масиві jobs.
        val director = credits?.optJSONArray("crew")?.let { array ->
            (0 until array.length()).asSequence()
                .mapNotNull { array.optJSONObject(it) }
                .firstOrNull { person ->
                    person.optString("job") == "Director" ||
                        person.optJSONArray("jobs")?.let { jobs ->
                            (0 until jobs.length()).any {
                                jobs.optJSONObject(it)?.optString("job") == "Director"
                            }
                        } == true
                }
                ?.optString("name").orEmpty()
        }.orEmpty()

        // Номери сезонів: спецвипуски (season 0) не показуємо.
        val seasonNumbers = json.optJSONArray("seasons")?.let { array ->
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.optInt("season_number", -1) }
                .filter { it > 0 }
                .sorted()
        }.orEmpty()

        // Ролики: тільки YouTube і тільки те, що можна показати глядачеві. Трейлери
        // попереду тизерів і уривків — саме їх шукають, а TMDB порядку не гарантує.
        val videos = json.optJSONObject("videos")?.optJSONArray("results")?.let { array ->
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .filter { it.optString("site").equals("YouTube", ignoreCase = true) }
                .mapNotNull { video ->
                    val videoKey = video.optString("key").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    TitleVideo(
                        key = videoKey,
                        name = video.optString("name").ifBlank { context.getString(R.string.catalog_video) },
                        type = video.optString("type"),
                        language = video.optString("iso_639_1"),
                    )
                }
                .sortedBy { VIDEO_ORDER.indexOf(it.type).takeIf { index -> index >= 0 } ?: VIDEO_ORDER.size }
        }.orEmpty()

        val similar = json.optJSONObject("recommendations")?.optJSONArray("results")?.let { array ->
            parseItems(array, item.kind)
        }.orEmpty()

        // Кадри без напису (мова `null`) — найкращі за оцінкою TMDB, головний кадр першим.
        val stills = json.optJSONObject("images")?.optJSONArray("backdrops")?.let { array ->
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .filter { it.isNull("iso_639_1") }
                .sortedByDescending { it.optDouble("vote_average") }
                .mapNotNull { it.optString("file_path").takeIf(String::isNotBlank) }
        }.orEmpty()
            .let { list -> (listOfNotNull(filled.backdropPath) + list).distinct().take(6) }

        return TitleDetails(
            item = filled,
            stills = stills,
            runtimeMinutes = runtime,
            genres = genres,
            countries = countries,
            ageRating = ageRating(json, item.kind, region),
            logoPath = logo ?: item.logoPath,
            franchise = franchise,
            cast = cast,
            director = director,
            people = people,
            tagline = json.optString("tagline").takeIf { it != "null" }.orEmpty(),
            creator = json.optJSONArray("created_by")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }.joinToString(", ")
            }.orEmpty(),
            network = json.optJSONArray("networks")?.optJSONObject(0)?.optString("name").orEmpty(),
            episodeCount = json.optInt("number_of_episodes"),
            premiere = date,
            seasonNumbers = seasonNumbers,
            videos = videos,
            similar = similar,
        )
    }

    /** Порядок роликів на сцені: трейлер важливіший за тизер, тизер — за уривок. */
    private val VIDEO_ORDER = listOf("Trailer", "Teaser", "Clip", "Featurette", "Behind the Scenes")

    private fun ageRating(json: JSONObject, kind: TitleKind, region: String): String {
        if (kind == TitleKind.TV) {
            val ratings = json.optJSONObject("content_ratings")?.optJSONArray("results")
                ?: return ""
            return (0 until ratings.length())
                .mapNotNull { ratings.optJSONObject(it) }
                .firstOrNull { it.optString("iso_3166_1") == region }
                ?.optString("rating")
                .orEmpty()
        }

        val releases = json.optJSONObject("release_dates")?.optJSONArray("results") ?: return ""
        return (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .firstOrNull { it.optString("iso_3166_1") == region }
            ?.optJSONArray("release_dates")
            ?.optJSONObject(0)
            ?.optString("certification")
            .orEmpty()
    }

    private suspend fun get(url: String): String? = requests.withPermit { getNow(url) }

    private suspend fun getNow(url: String): String? = withContext(Dispatchers.IO) {
        // Код відповіді не перевіряємо — не-2xx кидає виняток сам, і runCatching його ловить.
        //
        // Один повтор, коли не вдалося навіть з'єднатись. Хронологія шле 34 пошуки разом, і
        // кілька з'єднань з TMDB (CloudFront по IPv6) падали — фільм мовчки випадав зі
        // списку: «Знайдено 28 із 34» замість 34 (заміряно). Відмова швидка, тож
        // повтор майже нічого не коштує; таймаут не повторюємо — він і так довгий.
        com.likhtar.home.core.model.runCatchingCancellable {
            try {
                httpGet(url, TIMEOUT_MS, okOnly = false)
            } catch (e: java.net.ConnectException) {
                delay(RETRY_DELAY_MS)
                httpGet(url, TIMEOUT_MS, okOnly = false)
            }
        }.onFailure { Log.w(TAG, "TMDB недоступний", it) }.getOrNull()
    }

    /** Пошук має відрізняти мережеву помилку від коректної порожньої відповіді. */
    private suspend fun getRequired(url: String): String = requests.withPermit {
        withContext(Dispatchers.IO) { checkNotNull(httpGet(url, TIMEOUT_MS, okOnly = false)) }
    }

    private companion object {
        const val TAG = "LikhtarCatalog"

        /** Пауза перед повтором після невдалого з'єднання. */
        const val RETRY_DELAY_MS = 300L
        /**
         * `{current_date-120}` — сьогодні мінус стільки днів.
         *
         * Закривальна дужка екранована навмисно. На JVM `}` поза кванторами дозволена, а
         * ICU на Android — ні: складання шаблону падає з `PatternSyntaxException` у
         * статичному ініціалізаторі, тобто застосунок помирає на першому ж кадрі. Спіймано
         * лише на пристрої; на компіляції це не видно взагалі.
         */
        private val DATE_BACK = Regex("""\{current_date-(\d+)\}""")

        const val TIMEOUT_MS = 12_000

        /** Модель думає до ~15 с на поради; опис — 2–7 с. */
        const val ASK_TIMEOUT_MS = 45_000

        /** Перший переказ сезону сервер пише до хвилини. */
        const val RECAP_TIMEOUT_MS = 150_000

        const val ANIMATION_GENRE = 16
        val ANIME_LANGUAGES = setOf("ja", "zh")
        val ANIME_COUNTRIES = setOf("JP", "CN")
        val BLOCKED_LANGUAGES = setOf("ru")
        val BLOCKED_COUNTRIES = setOf("RU", "SU")
        val RUSSIAN_LETTERS = setOf('ы', 'э', 'ъ', 'ё', 'Ы', 'Э', 'Ъ', 'Ё')
    }
}

internal data class SearchCandidate(
    val item: CatalogItem,
    val originalIndex: Int,
    val popularity: Double,
    val voteCount: Int,
)

/** Стабільна релевантність: текстові збіги переважають сигнали популярності. */
internal object SearchRelevance {
    fun sort(query: String, candidates: List<SearchCandidate>): List<CatalogItem> {
        val normalizedQuery = normalize(query)
        val queryWords = normalizedQuery.split(' ').filter(String::isNotBlank)
        return candidates.sortedWith(
            compareByDescending<SearchCandidate> { candidate ->
                normalize(candidate.item.title) == normalizedQuery ||
                    normalize(candidate.item.originalTitle) == normalizedQuery
            }.thenByDescending { candidate ->
                normalize(candidate.item.title).startsWith(normalizedQuery) ||
                    normalize(candidate.item.originalTitle).startsWith(normalizedQuery)
            }.thenByDescending { candidate ->
                val words = (normalize(candidate.item.title) + " " + normalize(candidate.item.originalTitle))
                    .split(' ')
                    .filter(String::isNotBlank)
                queryWords.count { queryWord -> words.any { it == queryWord || it.startsWith(queryWord) } }
            }.thenBy { it.originalIndex }
                .thenByDescending { it.popularity }
                .thenByDescending { it.voteCount },
        ).map { it.item }
    }
}

/** Франшиза TMDB: назва, кадр і частини в порядку виходу. */
data class TmdbCollection(val name: String, val backdropPath: String?, val parts: List<CatalogItem>)
