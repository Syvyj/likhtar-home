package com.likhtar.home.core.catalog

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Випуск шоу, на якому людина зупинилась.
 *
 * [extKey] — ключ ПРОЄКТУ (`teleportal:<channel>/<slug>`, `1plus1:<id>`), [episode] — хеш
 * або id самого випуску. `catalog_id` тут від'ємний: у TMDB цієї позиції немає, і ходити з
 * ним у TMDB не можна.
 */
data class ShowContinue(
    val extKey: String,
    val source: String,
    val episode: String,
    val season: String,
    val title: String,
    val episodeTitle: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    /** Кадр проєкту з каталогу сервера: картка «Продовжити» не мусить чекати рядів. */
    val image: String = "",
    /** Коли дивились, секунди: спільна шкала з MEGOGO в ряду реаліті. */
    val updatedAt: Long = 0,
    /** Додивились — продовжувати треба з наступного випуску. */
    val finished: Boolean = false,
) {
    /** Підпис картки: «Серія 23», «Випуск 3» — без назви шоу й дати. */
    val episodeLabel: String get() = showEpisodeTitle(episodeTitle, title)

    /** Ключ картки: проєкт, а не випуск — у списку один рядок на проєкт. */
    val id: String get() = extKey

    /** Скільки лишилось, частками: нижня смужка на картці бере саме це. */
    val progress: Float
        get() = if (durationSeconds > 0) (positionSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f) else 0f
}

/**
 * «Продовжити перегляд» для випусків порталів — та сама ручка, що для фільмів.
 *
 * Тіло відрізняє їх: замість `tmdb_id` ідуть `ext_key` і `episode`. Список при цьому
 * лишається одним — `?kind=show` віддає випуски поруч зі звичайними шоу, `?kind=cinema`
 * не віддає їх ніколи (заміряно 16.09.2026: вісім рядків кіно без жодного випуску).
 *
 * Правило сервера, яке клієнт мусить не зламати: **один рядок на проєкт**, останній
 * випуск. П'ять переглянутих випусків «Мастер Шефа» дають один рядок — інакше
 * «Продовжити» перетворюється на історію переглядів.
 */
@Singleton
class ShowProgressRepository @Inject constructor(
    private val config: CatalogConfig,
) {

    /**
     * Записати позицію.
     *
     * Нуль у [positionSeconds] відоме місце не затирає — те саме правило, що для фільмів,
     * і саме воно дозволяє чесно писати на старті відтворення.
     */
    suspend fun save(
        extKey: String,
        episode: String,
        season: String,
        title: String,
        episodeTitle: String,
        positionSeconds: Int,
        durationSeconds: Int,
        /** Пройдений у черзі плеєра: позиції немає, а «переглянуто» — є. */
        finished: Boolean = false,
    ) {
        val base = config.homeOnlyUrl() ?: return
        val body = JSONObject()
            .put("ext_key", extKey)
            .put("episode", episode)
            .put("season", season)
            .put("title", title)
            .put("episode_title", episodeTitle)
            .put("position", positionSeconds)
            .put("duration", durationSeconds)
            .apply { if (finished) put("finished", true) }
            .toString()
        withContext(Dispatchers.IO) {
            runCatching {
                val reply = httpPostJson("$base/api/progress", body, TIMEOUT_MS)
                check(reply.ok) { "сервер відповів ${reply.code}" }
            }.onFailure { Log.w(TAG, "Позиція випуску не пішла", it) }
        }
    }

    suspend fun continueShows(limit: Int = LIMIT): List<ShowContinue> {
        val base = config.homeOnlyUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/continue?kind=show&limit=$limit", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "«Продовжити» для шоу не приїхало", it) }
                .getOrNull()
        } ?: return emptyList()
        return runCatching { parseShowContinue(body) }.getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "LikhtarShowProgress"
        const val TIMEOUT_MS = 6_000
        const val LIMIT = 20
    }
}

/**
 * Розбір `/api/continue?kind=show`.
 *
 * Беремо лише рядки з `ext_key`: шоу з TMDB приходять у тій самій відповіді, але їх малює
 * звичайний код Кінозалу, а тут потрібні саме випуски порталів. `episode` читаємо як
 * рядок навмисно — у Телепорталу це хеш, у 1+1 число, і в JSON воно приходить числом.
 */
internal fun parseShowContinue(body: String): List<ShowContinue> {
    val array = JSONObject(body).optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val raw = array.optJSONObject(index) ?: return@mapNotNull null
        val extKey = raw.optString("ext_key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val episode = raw.optString("episode").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ShowContinue(
            extKey = extKey,
            source = raw.optString("source"),
            episode = episode,
            season = raw.optString("season"),
            title = raw.optString("title"),
            episodeTitle = raw.optString("episode_title"),
            positionSeconds = raw.optInt("position"),
            durationSeconds = raw.optInt("duration"),
            image = raw.optString("backdrop").ifBlank { raw.optString("poster") },
            updatedAt = raw.optLong("updated"),
            finished = raw.optBoolean("finished"),
        )
    }
}
