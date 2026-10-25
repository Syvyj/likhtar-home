package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.DeviceId
import com.likhtar.home.core.data.SettingsRepository
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_playback_positions",
)

/**
 * Рядок «Продовжити перегляд»: що людина дивилась і що вмикати далі.
 *
 * [nextEpisode] рахує сервер: якщо остання серія позначена переглянутою — це наступна,
 * інакше та сама. Клієнт нічого не домислює.
 */
data class ContinueItem(
    val tmdbId: Int,
    val kind: TitleKind,
    val title: String,
    val originalTitle: String,
    val year: String,
    val season: Int,
    val nextEpisode: Int,
    val nextName: String,
    val nextRuntimeMinutes: Int,
    /** Artwork повертає Worker разом із прогресом; старі відповіді лишаються сумісними. */
    val posterPath: String? = null,
    val backdropPath: String? = null,
    /** Логотип назви (шлях TMDB) — сервер кладе з тієї ж бази знаків, що й для рядів. */
    val logoPath: String? = null,
    /** Де зупинились і скільки всього, секунди; 0 — не знаємо. */
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    /** Коли дивились востаннє, unix-секунди; 0 — не знаємо. */
    val updatedAt: Long = 0,
    val finished: Boolean = false,
    /** Наступна серія ще не вийшла — її дата (`2026-10-08`); порожньо — вже є. */
    val nextAirDate: String = "",
    /** Наступна серія вийшла вже після того, як додивились попередню: «Нова серія». */
    val newEpisode: Boolean = false,
) {
    val id: String get() = "${kind.name.lowercase()}-$tmdbId"

    /** Частка переглянутого, `null` — тривалість невідома. */
    val progress: Float?
        get() = if (durationSeconds > 0) (positionSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f) else null

    /** Скільки хвилин лишилось — коли тривалість відома й дивитись ще є що. */
    val leftMinutes: Int?
        get() = if (finished || durationSeconds <= 0) null else
            ((durationSeconds - positionSeconds) / 60).takeIf { it > 0 }

    /** «лишилось 32 хв» мовою інтерфейсу. */
    fun leftLabel(context: Context): String? =
        leftMinutes?.let { context.getString(R.string.catalog_minutes_left, it) }

    @Deprecated("Завжди українською; для екрана — leftLabel(context) або leftMinutes")
    val leftLabel: String?
        get() = leftMinutes?.let { "лишилось $it хв" }

    /**
     * Той самий тайтл у вигляді [CatalogItem].
     *
     * «Продовжити» приходить із сервера вже плоским рядком, а запис таймкоду говорить
     * мовою каталогу — тут вони й зустрічаються.
     */
    fun asCatalogItem(): CatalogItem = CatalogItem(
        tmdbId = tmdbId,
        kind = kind,
        title = title,
        originalTitle = originalTitle,
        year = year,
        posterPath = posterPath,
        backdropPath = backdropPath,
    )

    /** «S01E04 · Правда» — рівно те, що має стояти під карткою, мовою інтерфейсу. */
    fun episodeLabel(context: Context): String = episodeLabel(
        newEpisode = context.getString(R.string.catalog_new_episode),
        airs = { context.getString(R.string.catalog_airs_on, it) },
    )

    /** Те саме без Context: тексти «Нова серія» і «вийде …» дає викликач. */
    internal fun episodeLabel(newEpisode: String, airs: (String) -> String): String =
        if (kind == TitleKind.TV) {
            listOf(
                newEpisode.takeIf { this.newEpisode },
                String.format(java.util.Locale.US, "S%02dE%02d", season, nextEpisode),
                // Ще не вийшла — замість назви дата: «S02E07 · вийде 8 жовт.».
                airDate(nextAirDate)?.let(airs) ?: nextName.takeIf { it.isNotBlank() },
            ).filterNotNull().joinToString(" · ")
        } else {
            year
        }
}

/**
 * Відстеження переглянутого через власний сервер.
 *
 * Позиція відтворення тут довго була нулем: лаунчер кидав потік у зовнішній плеєр через
 * `startActivity` і більше про нього не чув. Тепер запуск іде «на результат», і плеєр на
 * виході повертає, де людина зупинилась, — див. [PlaybackController].
 *
 * Позиція живе у двох місцях навмисно. Локальна копія потрібна, щоб «продовжити з
 * таймкоду» працювало миттєво й без мережі; серверна — щоб те саме працювало на іншому
 * пристрої. Розходяться вони лише тоді, коли пристрій дивився офлайн, і тоді локальна
 * свіжіша.
 */
@Singleton
class WatchProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CatalogConfig,
    private val tmdb: TmdbClient,
    private val deviceId: DeviceId,
    private val settings: SettingsRepository,
    private val ownTorrentServer: OwnTorrentServer,
) {
    /**
     * Де зупинились, у мілісекундах. Ключ — тайтл разом із серією: у серіалі кожна серія
     * має власний таймкод.
     */
    suspend fun positionOf(tmdbId: Int, kind: TitleKind, season: Int, episode: Int): Long =
        context.playbackDataStore.data.first()[positionKey(tmdbId, kind, season, episode)] ?: 0L

    /**
     * Плеєр закрився — запамʼятовуємо, де саме.
     *
     * [durationMs] потрібне не менше за позицію: без нього не порахувати, скільки
     * лишилось, і не зрозуміти, що серію додивились до кінця.
     */
    suspend fun position(
        item: CatalogItem,
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        val watched = durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION
        context.playbackDataStore.edit { prefs ->
            val key = positionKey(item.tmdbId, item.kind, season, episode)
            // Додивлену серію не тримаємо: інакше наступного разу вона пропонувала б
            // «продовжити» за десять секунд до титрів.
            if (watched) prefs.remove(key) else prefs[key] = positionMs
            prefs[durationKey(item.tmdbId, item.kind, season, episode)] = durationMs
        }
        post(item, season, episode, finished = watched, positionMs = positionMs, durationMs = durationMs)
    }
    /** Почали дивитись — сервер запамʼятовує тайтл і позицію в сезоні. */
    suspend fun started(item: CatalogItem, season: Int, episode: Int) =
        post(item, season, episode, finished = false)

    /** Серію (чи фільм) вважаємо переглянутою. */
    suspend fun finished(item: CatalogItem, season: Int, episode: Int) =
        post(item, season, episode, finished = true)

    private suspend fun post(
        item: CatalogItem,
        season: Int,
        episode: Int,
        finished: Boolean,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        val base = config.homeOnlyUrl() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("tmdb_id", item.tmdbId)
                    .put("kind", if (item.kind == TitleKind.TV) "tv" else "movie")
                    .put("season", season)
                    .put("episode", episode)
                    .put("finished", finished)
                    .put("position", positionMs / 1000)
                    .put("duration", durationMs / 1000)
                    .put("title", item.title)
                    .put("original_title", item.originalTitle)
                    .put("year", item.year.toIntOrNull() ?: 0)
                    .put("profile_id", settings.settings.first().activeProfileId)
                    .put("device_id", deviceId.value)
                    .toString()

                val code = httpPostJson("$base/api/progress", body, TIMEOUT_MS).code
                Log.i(TAG, "progress s${season}e$episode finished=$finished → $code")
            }.onFailure { Log.w(TAG, "Прогрес не записався", it) }
        }
    }

    /**
     * Що цей профіль уже додивився — [CatalogItem.id] кожного завершеного тайтла.
     *
     * Локально цього знати нізвідки: закінчений перегляд ми з `progress_positions`
     * якраз видаляємо, бо позиції в ньому більше немає. Тож питаємо сервер, і саме
     * `/api/progress`, а не `/api/continue`: другий завершене й не показує.
     *
     * Порожньо на будь-якій негаразді — краще показати картку двічі, ніж прибрати з
     * каталогу те, чого людина не дивилась.
     */
    suspend fun finishedKeys(): Set<String> {
        val base = config.homeOnlyUrl() ?: return emptySet()
        return withContext(Dispatchers.IO) {
            runCatching {
                val profile = settings.settings.first().activeProfileId
                val body = httpGet("$base/api/progress?profile=${encode(profile)}", TIMEOUT_MS)
                    ?: return@runCatching emptySet()
                parseFinished(body).also { Log.i(TAG, "переглянуто: ${it.size} тайтлів") }
            }.onFailure { Log.w(TAG, "Список переглянутого недоступний", it) }
                .getOrDefault(emptySet())
        }
    }

    /**
     * Що продовжити дивитись. По рядку на тайтл, найсвіжіше зверху.
     *
     * Не кешуємо: ряд має оновитись одразу після перегляду, а запит — один і дешевий.
     *
     * [all] — це вже не «що продовжити», а «що дивились»: сервер додає й додивлене,
     * якого в ряду «Продовжити» бути не має. Потрібно сторінці історії: людина шукає
     * там фільм, який пам'ятає, а не наступну серію.
     */
    suspend fun continueWatching(
        limit: Int = LIMIT,
        all: Boolean = false,
        /**
         * `cinema` — без випусків порталів. Без нього сервер домішує їх у список, а тут
         * вони відкидаються (немає tmdb_id) — і з'їдали ліміт: на Головній з limit=7 ряд
         * лишався без нашого кіно.
         */
        kind: String? = null,
    ): List<ContinueItem> {
        val base = config.homeOnlyUrl() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val profile = settings.settings.first().activeProfileId
                val query = "?limit=$limit&profile=${encode(profile)}" +
                    (if (all) "&all=1" else "") + (kind?.let { "&kind=$it" } ?: "")
                val body = httpGet("$base/api/continue$query", TIMEOUT_MS)
                    ?: return@runCatching emptyList()
                parse(JSONObject(body).optJSONArray("results")).map { item ->
                    if (item.posterPath != null || item.backdropPath != null) return@map item
                    val stub = CatalogItem(
                        tmdbId = item.tmdbId,
                        kind = item.kind,
                        title = item.title,
                        originalTitle = item.originalTitle,
                        year = item.year,
                    )
                    val artwork = com.likhtar.home.core.model.runCatchingCancellable { tmdb.details(stub).item }.getOrNull()
                    item.copy(
                        posterPath = artwork?.posterPath,
                        backdropPath = artwork?.backdropPath,
                    )
                }
            }.onFailure { Log.w(TAG, "Список «продовжити» недоступний", it) }
                .getOrDefault(emptyList())
        }
    }

    /**
     * Запуск того, що продовжуємо: сервер сам вибирає джерело, яке реально тягне.
     *
     * Це той самий ендпойнт, що й прогрів роздачі, тому окремий `prepare` тут не потрібен.
     * Порожня відповідь означає «нема чого вмикати» — для серіалу це кінець сезону, і
     * саме за цим сигналом тайтл зникає з ряду.
     */
    suspend fun play(item: ContinueItem, waitSeconds: Int = WAIT_S): PlayResult? {
        val base = config.homeOnlyUrl() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val current = settings.settings.first()
                val profile = current.activeProfileId
                val serial = item.kind == TitleKind.TV
                val url = buildString {
                    append(base).append("/api/play?wait=").append(waitSeconds)
                    if (ownTorrentServer.url != null) append("&own_ts=1") // див. LikhtarStreams.playVoice
                    if (!DolbyVision.supported) append("&nodv=1")
                    // Хто і з чого дивиться: сервер сам пише кожен запуск у
                    // `playback_attempts`, але без цих двох не може сказати, чий саме
                    // запуск і на якій приставці.
                    append("&profile=").append(encode(profile))
                    append("&device=").append(encode(deviceId.value))
                    // Що саме вмикати. Без цього сервер отримував
                    // порожній запит і відповідав `tried: 0` — «Продовжити» й Історія
                    // не відкривали нічого. `tmdb_id` — щоб не плутати однойменні.
                    append("&tmdb_id=").append(item.tmdbId)
                    append("&kind=").append(if (serial) "tv" else "movie")
                    append("&title=").append(encode(item.title))
                    append("&original_title=").append(encode(item.originalTitle))
                    append("&year=").append(encode(item.year))
                    append("&serial=").append(if (serial) 1 else 0)
                    if (serial) {
                        append("&s=").append(item.season)
                        append("&e=").append(item.nextEpisode)
                    }
                }
                val body = httpGet(url, (waitSeconds + 5) * 1_000) ?: return@runCatching null
                val json = JSONObject(body)
                // Джерело віддаємо разом із посиланням: його треба переслати назад у
                // `/api/attempt`, інакше сервер не знає, якої якості була роздача.
                PlayResult(
                    url = json.optString("url"),
                    name = json.optString("name"),
                    reason = json.optString("reason"),
                    ref = json.optString("ref"),
                    source = json.optString("source"),
                ).takeIf { it.ok }
            }.onFailure { Log.w(TAG, "«Продовжити» не змогло стартувати", it) }.getOrNull()
        }
    }

    /**
     * Дозвіт того, чого сервер не бачить: перегляд відбувся, а не лише почався.
     *
     * `played_ms` беремо з позиції, яку віддав плеєр, — іншої міри в нас немає. Шлемо
     * тільки коли плеєр справді відповів: ViMu повертає результат не завжди, а вигадувати
     * «переглянуто» без підстав гірше, ніж не надіслати нічого.
     *
     * [result] вільним рядком, і в ньому має бути ЯКИЙ плеєр і на чому: порядок `packages`
     * сервер тримає за широтою claim'ів, а не за кодеками, тож який плеєр тягне DV+HDR10+
     * на цьому пристрої — знання наше, не його. Заміряно: ViMu на 2160p-ремуксі не
     * читає потік узагалі, Nova грає.
     *
     * [playedMs] `0` дозволено саме для таких випадків: «плеєр не відкрив потік» — це
     * подія без жодної зіграної секунди, і не надіслати її означало б лишити сервер із
     * тими самими claim'ами.
     */
    suspend fun reportAttempt(
        item: CatalogItem,
        playedMs: Long,
        result: String = "player_returned",
        player: String = "",
        ref: String = "",
        source: String = "",
        quality: Int = 0,
    ) {
        if (playedMs <= 0 && result == "player_returned") return
        val base = config.homeOnlyUrl() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("profile_id", settings.settings.first().activeProfileId)
                    .put("device_id", deviceId.value)
                    .put("tmdb_id", item.tmdbId)
                    .put("kind", if (item.kind == TitleKind.TV) "tv" else "movie")
                    .put("result", result)
                    // Окремим полем, хоч сервер і вміє виймати пакет із `result`: у нього
                    // під це колонка, і розбирати рядок — зайва ланка, яка може розійтися
                    // з нашим форматом.
                    .put("player", player)
                    // `ref` сервер перетворює на якість роздачі — без нього розріз
                    // «який плеєр тягне 2160p» не збирається взагалі, і дірка тиха:
                    // ніде не падає, просто відповіді немає назавжди.
                    .put("ref", ref)
                    .put("source", source)
                    // Число, а не рядок: сервер бере спершу те, що сказав клієнт, і лише
                    // без нього виводить якість із роздачі за `ref`.
                    .put("quality", quality)
                    .put("played_ms", playedMs)
                    .toString()
                val code = httpPostJson("$base/api/attempt", payload, TIMEOUT_MS).code
                Log.i(TAG, "attempt ${item.tmdbId} $result played=${playedMs}мс → $code")
            }.onFailure { Log.w(TAG, "Спроба не доїхала", it) }
        }
    }

    private fun parse(array: JSONArray?): List<ContinueItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = if (raw.optString("kind") == "tv") TitleKind.TV else TitleKind.MOVIE
            ContinueItem(
                tmdbId = raw.optInt("tmdb_id").takeIf { it > 0 } ?: return@mapNotNull null,
                kind = kind,
                title = raw.optString("title"),
                originalTitle = raw.optString("original_title"),
                year = raw.optInt("year").takeIf { it > 0 }?.toString().orEmpty(),
                // Наступна серія може бути вже в наступному сезоні: без next_season картка
                // казала «S03E01», маючи на увазі S04E01 («Таємниця бункера»).
                season = raw.optInt("next_season").takeIf { it > 0 } ?: raw.optInt("season"),
                nextEpisode = raw.optInt("next_episode"),
                nextName = raw.optString("next_name"),
                nextRuntimeMinutes = raw.optInt("next_runtime"),
                posterPath = raw.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                backdropPath = raw.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                logoPath = raw.optString("logo").takeIf { it.isNotBlank() },
                positionSeconds = raw.optInt("position"),
                durationSeconds = raw.optInt("duration"),
                updatedAt = raw.optLong("updated"),
                finished = raw.optBoolean("finished") || raw.optString("status") == "finished",
                nextAirDate = raw.optString("next_air").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                newEpisode = raw.optBoolean("new_episode"),
            )
        }
    }

    /** Прибрати тайтл з історії профілю — як «Приховати з історії» в Netflix. */
    suspend fun forget(item: ContinueItem): Boolean {
        val base = config.homeOnlyUrl() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val profile = settings.settings.first().activeProfileId
                val kind = if (item.kind == TitleKind.TV) "tv" else "movie"
                httpDelete("$base/api/progress?profile=${encode(profile)}&tmdb_id=${item.tmdbId}&kind=$kind", TIMEOUT_MS) in 200..299
            }.getOrDefault(false)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun positionKey(tmdbId: Int, kind: TitleKind, season: Int, episode: Int) =
        longPreferencesKey("pos_${kind.name.lowercase()}_${tmdbId}_s${season}e$episode")

    private fun durationKey(tmdbId: Int, kind: TitleKind, season: Int, episode: Int) =
        longPreferencesKey("dur_${kind.name.lowercase()}_${tmdbId}_s${season}e$episode")

    private companion object {
        const val TAG = "LikhtarProgress"
        const val TIMEOUT_MS = 10_000
        const val LIMIT = 20

        /** Той самий запас, що й у прогріві роздачі: жива віддає за 0.5–2 с. */
        const val WAIT_S = 25
    }
}

/**
 * `/api/progress` → [CatalogItem.id] тайтлів, які профіль додивився.
 *
 * Окремо від репозиторію, бо це єдине місце з правилом, яке легко зламати: серіал
 * приходить рядком на серію, і одна додивлена серія — не додивлений серіал. Тайтл
 * переглянутий лише коли жоден його рядок не лишився незавершеним.
 */
internal fun parseFinished(body: String): Set<String> {
    val array = JSONObject(body).optJSONArray("results") ?: return emptySet()
    return (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optInt("tmdb_id").takeIf { it > 0 } ?: return@mapNotNull null
        // Той самий ключ, що й `CatalogItem.id`, — щоб на екрані нічого не перекладати:
        // `movie-155`, `tv-291496`.
        "${row.optString("kind").lowercase()}-$id" to row.optBoolean("finished")
    }.groupBy({ it.first }, { it.second })
        .filterValues { states -> states.all { it } }
        .keys
}

/** `2026-10-08` → «8 жовт.» мовою інтерфейсу; `null` — дати немає. */
internal fun airDate(iso: String, locale: java.util.Locale = java.util.Locale.getDefault()): String? {
    val date = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { isLenient = false }.parse(iso)
    }.getOrNull() ?: return null
    return java.text.SimpleDateFormat("d MMM", locale).format(date)
}

/** Після цієї частки вважаємо, що додивились: далі йдуть титри. */
internal const val FINISHED_FRACTION = 0.92
