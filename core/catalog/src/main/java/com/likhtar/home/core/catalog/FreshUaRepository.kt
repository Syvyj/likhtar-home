package com.likhtar.home.core.catalog

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * «Що з'явилось українською» — і це ІНША дата, ніж усе решта в каталозі.
 *
 * TMDB знає дату виходу у світі, наш сканер — коли МИ знайшли джерело. Жодне з двох не
 * відповідає на питання «що нового подивитись українською»: фільм 2017 року, якому торент
 * знайшовся минулого тижня, за нашою датою новинка, а для глядача ні.
 *
 * Сервер віддає сюди готову картку — постер, кадр, жанри вже в відповіді, — тому окремого
 * походу в TMDB за кожною позицією тут немає. Це свідомо: ряд із двадцяти карток коштував
 * би двадцять запитів на кожен захід у розділ.
 */
@Singleton
class FreshUaRepository @Inject constructor(
    private val config: CatalogConfig,
) {

    private val cached = mutableMapOf<String, List<CatalogItem>>()
    private var cachedAt = 0L

    /** Нове українською: `kind` порожній — і фільми, і серіали разом. */
    suspend fun fresh(kind: String = "", limit: Int = LIMIT): List<CatalogItem> =
        load("kind=$kind&days=$DAYS&audio=1&limit=$limit")

    /**
     * «Незабаром українською» — дата ще не настала.
     *
     * Окремий ряд, а не хвіст попереднього: у цих позицій НЕМА чого вмикати, і змішувати
     * їх із тим, що вже грає, означає обіцяти кнопку, якої не буде.
     */
    suspend fun soon(limit: Int = SOON_LIMIT): List<CatalogItem> =
        load("future=1&days=$SOON_DAYS&limit=$limit")

    private suspend fun load(query: String): List<CatalogItem> {
        val now = System.currentTimeMillis()
        synchronized(cached) {
            if (now - cachedAt > CATALOG_CACHE_MS) {
                cached.clear()
                cachedAt = now
            }
            cached[query]?.let { return it }
        }
        val base = config.streamsUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/fresh-ua?$query", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Свіже українською не приїхало", it) }
                .getOrNull()
        } ?: return emptyList()
        val items = runCatching { parseFreshUa(body) }
            .onFailure { Log.w(TAG, "Свіже українською не розібралось", it) }
            .getOrDefault(emptyList())
        if (items.isNotEmpty()) synchronized(cached) { cached[query] = items }
        return items
    }

    private companion object {
        const val TAG = "LikhtarFreshUa"
        const val TIMEOUT_MS = 8_000

        /** Два місяці: за коротше вікно ряд порожніє в тижні без гучних прем'єр. */
        const val DAYS = 60
        const val LIMIT = 20

        /** Наперед дивимось так само далеко: анонси рідші за релізи. */
        const val SOON_DAYS = 60
        const val SOON_LIMIT = 12
    }
}

/**
 * Розбір `/api/fresh-ua`.
 *
 * Окремо від репозиторію, щоб межу перевіряв тест: позиція без `tmdb_id` або без дати в
 * ряд потрапити не може — відкривати її нічим, а сортувати нема за чим.
 */
internal fun parseFreshUa(body: String): List<CatalogItem> {
    val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { index ->
        val raw = results.optJSONObject(index) ?: return@mapNotNull null
        val id = raw.optInt("tmdb_id")
        val date = raw.optString("online_date")
        if (id <= 0 || date.length < 10) return@mapNotNull null
        val kind = if (raw.optString("kind") == "tv") TitleKind.TV else TitleKind.MOVIE
        val genres = raw.optJSONArray("genres")?.let { array ->
            array.toStringList()
        }.orEmpty()
        CatalogItem(
            tmdbId = id,
            kind = kind,
            title = raw.optString("title"),
            year = raw.optInt("year").takeIf { it > 0 }?.toString().orEmpty(),
            posterPath = raw.optString("poster").takeIf(String::isNotBlank),
            backdropPath = raw.optString("backdrop").takeIf(String::isNotBlank),
            genres = genres,
            // Саме `availabilityDate`: це та величина, за якою ряди Кінозалу вже
            // сортуються, і окремого поля під «дату появи українською» не треба.
            availabilityDate = date,
        )
    }
}
