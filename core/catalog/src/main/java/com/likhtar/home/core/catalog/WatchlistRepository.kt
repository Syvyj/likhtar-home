package com.likhtar.home.core.catalog

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.URLEncoder
import com.likhtar.home.core.data.SettingsRepository
import androidx.datastore.preferences.core.longPreferencesKey
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.watchlistDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_watchlist",
)

/**
 * «Мій список» — те, що користувач відклав подивитися.
 *
 * Лежить цілим JSON-масивом, а не множиною id: список має **порядок** (додане останнім —
 * зверху), і картку треба намалювати одразу, ще до будь-якого запиту в мережу. Тримати самі
 * id означало б порожній рядок на холодному старті, поки TMDB відповідає на двадцять запитів.
 */
/** Тайтл, на який профіль чекав, — уже з українською. [waitingSince] — unix-секунди. */
data class UaArrival(val item: CatalogItem, val waitingSince: Long)

@Singleton
class WatchlistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CatalogConfig,
    private val settings: SettingsRepository,
    private val tmdb: TmdbClient,
) {
    val items: Flow<List<CatalogItem>> = context.watchlistDataStore.data.map { prefs ->
        parse(prefs[KEY_ITEMS].orEmpty())
    }

    suspend fun contains(item: CatalogItem): Boolean = items.first().any { it.id == item.id }

    /** Повертає новий стан: true — у списку, false — прибрано. */
    suspend fun toggle(item: CatalogItem): Boolean {
        var added = false
        context.watchlistDataStore.edit { prefs ->
            val current = parse(prefs[KEY_ITEMS].orEmpty())
            val without = current.filterNot { it.id == item.id }
            added = without.size == current.size
            val updated = if (added) listOf(item) + without else without
            prefs[KEY_ITEMS] = serialize(updated.take(LIMIT))
        }
        // Локально вже змінено — картка перемалювалась. Сервер доганяє окремо й тихо:
        // без мережі список мусить лишатись робочим, це той самий локальний кеш, що й був.
        push(item, deleted = !added)
        return added
    }

    /**
     * Інкрементальна синхронізація: питаємо лише те, що змінилось після минулого разу.
     *
     * `since` віддає й видалене (`deleted: true`) — саме тому офлайнова приставка не
     * воскрешає закладку, яку прибрали з іншої: без цього прапорця вона побачила б лише
     * «додані» й повернула б своє.
     */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext
        val profile = settings.settings.first().activeProfileId
        runCatching {
            val since = context.watchlistDataStore.data.first()[KEY_SINCE] ?: 0L
            val url = "$base/api/watchlist?profile=${encode(profile)}&since=$since"
            val body = httpGet(url, TIMEOUT_MS) ?: return@runCatching
            val array = JSONObject(body).optJSONArray("results") ?: return@runCatching
            if (array.length() == 0) return@runCatching

            val added = mutableListOf<CatalogItem>()
            val removed = mutableSetOf<String>()
            (0 until array.length()).forEach { index ->
                val json = array.optJSONObject(index) ?: return@forEach
                val tmdbId = json.optInt("tmdb_id").takeIf { it > 0 } ?: return@forEach
                val kind = TitleKind.fromTmdb(json.optString("kind"))
                val item = CatalogItem(
                    tmdbId = tmdbId,
                    kind = kind,
                    title = json.optString("title"),
                    year = json.optInt("year").takeIf { it > 0 }?.toString().orEmpty(),
                    posterPath = json.optString("poster_path").takeIf { it.isNotBlank() },
                )
                if (json.optBoolean("deleted")) removed += item.id else added += item
            }

            context.watchlistDataStore.edit { prefs ->
                val current = parse(prefs[KEY_ITEMS].orEmpty())
                val addedIds = added.map { it.id }.toSet()
                val kept = current.filterNot { it.id in removed || it.id in addedIds }
                prefs[KEY_ITEMS] = serialize((added + kept).take(LIMIT))
                prefs[KEY_SINCE] = System.currentTimeMillis() / 1000
            }
        }.onFailure { Log.w(TAG, "«Мій список» не синхронізувався", it) }
        fillArtwork()
    }

    /**
     * Сервер зберігає закладку без картинок — з іншої приставки вона приїжджала порожньою
     * плашкою з літерою. Добираємо постер і кадр з TMDB один раз і кладемо в локальний список.
     */
    private suspend fun fillArtwork() {
        val bare = items.first().filter { it.posterPath == null }.take(ARTWORK_BATCH)
        if (bare.isEmpty()) return
        val filled = bare.mapNotNull { item -> runCatching { tmdb.details(item).item }.getOrNull()?.let { item.id to it } }.toMap()
        if (filled.isEmpty()) return
        context.watchlistDataStore.edit { prefs ->
            prefs[KEY_ITEMS] = serialize(
                parse(prefs[KEY_ITEMS].orEmpty()).map { item ->
                    val full = filled[item.id] ?: return@map item
                    item.copy(
                        posterPath = full.posterPath,
                        backdropPath = item.backdropPath ?: full.backdropPath,
                        rating = if (item.rating > 0) item.rating else full.rating,
                        overview = item.overview.ifBlank { full.overview },
                    )
                },
            )
        }
    }

    /**
     * Радар української: з якого часу профіль чекає озвучку цього тайтла (unix-секунди);
     * 0 — не чекає, `null` — сервер не відповів.
     */
    suspend fun uaWaitSince(item: CatalogItem): Long? = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext null
        val profile = settings.settings.first().activeProfileId
        runCatching {
            val body = httpGet(
                "$base/api/ua-wait?profile=${encode(profile)}&tmdb_id=${item.tmdbId}&kind=${item.kind.tmdbPath}",
                TIMEOUT_MS,
            ) ?: return@runCatching null
            JSONObject(body).optLong("since")
        }.getOrNull()
    }

    /** Увімкнути очікування — тайтл заодно стає в «Мій список» (на сервері й тут). */
    suspend fun setUaWait(item: CatalogItem, on: Boolean): Boolean {
        if (on && !contains(item)) {
            context.watchlistDataStore.edit { prefs ->
                val current = parse(prefs[KEY_ITEMS].orEmpty())
                prefs[KEY_ITEMS] = serialize((listOf(item) + current).take(LIMIT))
            }
        }
        return withContext(Dispatchers.IO) {
            val base = config.homeOnlyUrl() ?: return@withContext false
            runCatching {
                val payload = JSONObject()
                    .put("profile_id", settings.settings.first().activeProfileId)
                    .put("tmdb_id", item.tmdbId)
                    .put("kind", item.kind.tmdbPath)
                    .put("on", on)
                    .toString()
                httpPostJson("$base/api/ua-wait", payload, TIMEOUT_MS).code in 200..299
            }.getOrDefault(false)
        }
    }

    /** Один тайтл, на який чекали й до якого вже є українська; `null` — нічого. */
    suspend fun uaArrived(): UaArrival? = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext null
        val profile = settings.settings.first().activeProfileId
        runCatching {
            val json = JSONObject(httpGet("$base/api/ua-wait/arrived?profile=${encode(profile)}", TIMEOUT_MS) ?: return@runCatching null)
                .optJSONObject("item") ?: return@runCatching null
            val id = json.optInt("tmdb_id").takeIf { it > 0 } ?: return@runCatching null
            UaArrival(
                item = CatalogItem(
                    tmdbId = id,
                    kind = TitleKind.fromTmdb(json.optString("kind")),
                    title = json.optString("title"),
                    year = "",
                    posterPath = json.optString("poster").takeIf { it.isNotBlank() },
                    backdropPath = json.optString("backdrop").takeIf { it.isNotBlank() },
                ),
                waitingSince = json.optLong("waiting_since"),
            )
        }.getOrNull()
    }

    /** Картку «вийшла українською» показали — більше не пропонувати. */
    suspend fun markUaArrivalSeen(item: CatalogItem) = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext
        runCatching {
            val payload = JSONObject()
                .put("profile_id", settings.settings.first().activeProfileId)
                .put("tmdb_id", item.tmdbId)
                .put("kind", item.kind.tmdbPath)
                .toString()
            httpPostJson("$base/api/ua-wait/arrived", payload, TIMEOUT_MS)
        }
        Unit
    }

    private suspend fun push(item: CatalogItem, deleted: Boolean) = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext
        runCatching {
            val payload = JSONObject()
                .put("profile_id", settings.settings.first().activeProfileId)
                .put("tmdb_id", item.tmdbId)
                .put("kind", if (item.kind == TitleKind.TV) "tv" else "movie")
                .put("title", item.title)
                .put("year", item.year.toIntOrNull() ?: 0)
                .put("deleted", deleted)
                .toString()
            val code = httpPostJson("$base/api/watchlist", payload, TIMEOUT_MS).code
            Log.i(TAG, "watchlist ${item.tmdbId} deleted=$deleted → $code")
        }.onFailure { Log.w(TAG, "Закладка не доїхала до сервера", it) }
        Unit
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parse(raw: String): List<CatalogItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optInt("tmdbId").takeIf { it > 0 } ?: return@mapNotNull null
                CatalogItem(
                    tmdbId = id,
                    kind = TitleKind.fromTmdb(json.optString("kind")),
                    title = json.optString("title"),
                    year = json.optString("year"),
                    posterPath = json.optString("poster").takeIf { it.isNotBlank() },
                    backdropPath = json.optString("backdrop").takeIf { it.isNotBlank() },
                    logoPath = json.optString("logo").takeIf { it.isNotBlank() },
                    rating = json.optDouble("rating", 0.0),
                    overview = json.optString("overview"),
                )
            }
        }.onFailure { Log.w(TAG, "Не вдалося прочитати «Мій список»", it) }.getOrDefault(emptyList())
    }

    private fun serialize(items: List<CatalogItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("tmdbId", item.tmdbId)
                    .put("kind", if (item.kind == TitleKind.TV) "tv" else "movie")
                    .put("title", item.title)
                    .put("year", item.year)
                    .put("poster", item.posterPath.orEmpty())
                    .put("backdrop", item.backdropPath.orEmpty())
                    .put("logo", item.logoPath.orEmpty())
                    .put("rating", item.rating)
                    .put("overview", item.overview),
            )
        }
        return array.toString()
    }

    private companion object {
        const val TAG = "LikhtarCatalog"
        const val LIMIT = 100
        const val ARTWORK_BATCH = 20

        const val TIMEOUT_MS = 10_000

        val KEY_ITEMS = stringPreferencesKey("items")

        /** Позначка часу минулої синхронізації — з неї й питаємо `since`. */
        val KEY_SINCE = longPreferencesKey("synced_at")
    }
}
