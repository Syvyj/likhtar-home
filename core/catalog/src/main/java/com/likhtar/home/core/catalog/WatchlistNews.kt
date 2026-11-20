package com.likhtar.home.core.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Новина закладки для дзвіночка: тайтл з'явився на сервісі чи вийшов трейлер ([label] — сервіс чи назва ролика). */
data class TitleNews(val item: CatalogItem, val kind: Kind, val label: String, val date: String) {
    enum class Kind { SERVICE, TRAILER }

    val key: String get() = "news:${item.kind.tmdbPath}:${item.tmdbId}:$kind:$label"
}

/** Що відомо про тайтл: сервіси й трейлери (ключ YouTube → назва). Лише поповнюється — див. [merged]. */
data class TitleSnapshot(val providers: Set<String>, val trailers: Map<String, String>)

/** Новини між відомим знімком і щойно знятим; першої перевірки тайтлу немає з чим порівняти — новин нема. */
internal fun titleNews(item: CatalogItem, old: TitleSnapshot?, now: TitleSnapshot, date: String): List<TitleNews> {
    old ?: return emptyList()
    return (now.providers - old.providers).map { TitleNews(item, TitleNews.Kind.SERVICE, it, date) } +
        (now.trailers - old.trailers.keys).values.map { TitleNews(item, TitleNews.Kind.TRAILER, it, date) }
}

/**
 * Відоме лише росте: сервіс, якого TMDB сьогодні не віддав (збій мережі, регіон на день випав),
 * завтра повернеться — і це не новина «тепер на Netflix».
 */
internal fun merged(old: TitleSnapshot?, now: TitleSnapshot): TitleSnapshot =
    if (old == null) now else TitleSnapshot(old.providers + now.providers, old.trailers + now.trailers)

/** Новини живуть [NEWS_DAYS] днів — як вікно нових серій. */
internal fun freshNews(news: List<TitleNews>, today: String): List<TitleNews> {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val since = runCatching { format.format(java.util.Date(format.parse(today)!!.time - NEWS_DAYS * DAY_MS)) }.getOrDefault("")
    return news.filter { it.date >= since }
}

internal fun encodeSnapshots(snapshots: Map<String, TitleSnapshot>): String = JSONObject().apply {
    snapshots.forEach { (key, snap) ->
        put(key, JSONObject().put("p", JSONArray(snap.providers.toList())).put("t", JSONObject(snap.trailers)))
    }
}.toString()

internal fun decodeSnapshots(raw: String): Map<String, TitleSnapshot> = runCatching {
    val json = JSONObject(raw)
    json.keys().asSequence().associateWith { key ->
        val o = json.getJSONObject(key)
        val p = o.optJSONArray("p") ?: JSONArray()
        val t = o.optJSONObject("t") ?: JSONObject()
        TitleSnapshot((0 until p.length()).map { p.getString(it) }.toSet(), t.keys().asSequence().associateWith { t.getString(it) })
    }
}.getOrDefault(emptyMap())

internal fun encodeNews(news: List<TitleNews>): String = JSONArray().apply {
    news.forEach {
        put(
            JSONObject().put("id", it.item.tmdbId).put("kind", it.item.kind.tmdbPath).put("title", it.item.title)
                .put("year", it.item.year).put("poster", it.item.posterPath.orEmpty())
                .put("news", it.kind.name).put("label", it.label).put("date", it.date),
        )
    }
}.toString()

internal fun decodeNews(raw: String): List<TitleNews> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val item = CatalogItem(
            tmdbId = o.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null,
            kind = TitleKind.fromTmdb(o.optString("kind")),
            title = o.optString("title"),
            year = o.optString("year"),
            posterPath = o.optString("poster").takeIf { it.isNotBlank() },
        )
        val kind = runCatching { TitleNews.Kind.valueOf(o.optString("news")) }.getOrNull() ?: return@mapNotNull null
        TitleNews(item, kind, o.optString("label"), o.optString("date"))
    }
}.getOrDefault(emptyList())

private const val NEWS_DAYS = 14L
private const val DAY_MS = 24 * 60 * 60 * 1000L

private val Context.watchlistNewsStore: DataStore<Preferences> by preferencesDataStore(name = "likhtar_watchlist_news")

/**
 * C4 (Nottilo Plus): раз на добу приставка сама звіряє закладки з TMDB — де дивитись і які
 * трейлери — і кладе новини в дзвіночок нових серій. Без нашого сервера: закладки й знімки
 * лежать на приставці, TMDB — той самий, що в каталозі.
 */
@Singleton
class WatchlistNews @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdb: TmdbClient,
    private val watchlist: WatchlistRepository,
) {
    /** Новини лише тих тайтлів, що досі в закладках. */
    val news: Flow<List<TitleNews>> = context.watchlistNewsStore.data.map { decodeNews(it[NEWS].orEmpty()) }

    /** Не частіше за раз на добу; вікно дзвіночка викликає на кожному своєму циклі. */
    suspend fun check() {
        val store = context.watchlistNewsStore
        val prefs = store.data.first()
        val now = System.currentTimeMillis()
        if (now - (prefs[CHECKED] ?: 0L) < DAY_MS) return
        val items = watchlist.items.first().take(MAX_TITLES)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        val snapshots = decodeSnapshots(prefs[SNAPSHOTS].orEmpty())
        val taken = coroutineScope {
            items.map { item ->
                async {
                    // Деталі не прийшли — тайтл цього разу пропускаємо, а не записуємо «трейлерів нема».
                    val details = runCatching { tmdb.details(item) }.getOrNull() ?: return@async null
                    val trailers = details.videos.filter { it.type == "Trailer" }.associate { it.key to it.name }
                    item to TitleSnapshot(tmdb.watchProviders(item).toSet(), trailers)
                }
            }.mapNotNull { it.await() }
        }
        val found = taken.flatMap { (item, snap) -> titleNews(item, snapshots[keyOf(item)], snap, today) }
        val ids = items.map(::keyOf).toSet()
        store.edit { p ->
            val known = decodeSnapshots(p[SNAPSHOTS].orEmpty()).filterKeys { it in ids }.toMutableMap()
            taken.forEach { (item, snap) -> known[keyOf(item)] = merged(known[keyOf(item)], snap) }
            p[SNAPSHOTS] = encodeSnapshots(known)
            val kept = freshNews(decodeNews(p[NEWS].orEmpty()), today).filter { keyOf(it.item) in ids }
            p[NEWS] = encodeNews((found + kept).distinctBy { it.key })
            p[CHECKED] = now
        }
        android.util.Log.i("LikhtarBell", "закладки: перевірено ${taken.size}/${items.size}, нових новин ${found.size}")
    }

    private fun keyOf(item: CatalogItem) = "${item.kind.tmdbPath}:${item.tmdbId}"

    private companion object {
        const val MAX_TITLES = 40
        val CHECKED = longPreferencesKey("checked")
        val SNAPSHOTS = stringPreferencesKey("snapshots")
        val NEWS = stringPreferencesKey("news")
    }
}
