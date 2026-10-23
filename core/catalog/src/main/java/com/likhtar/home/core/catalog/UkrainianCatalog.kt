package com.likhtar.home.core.catalog

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Позиція українського каталогу сервера.
 *
 * [tmdbId] — тільки справжній id TMDB; там, де його немає, лишається сам [catalogId], і
 * ходити з ним у TMDB не можна. Саме тому це два різні поля, а не одне «id».
 */
data class UkrainianTitle(
    val catalogId: Int,
    val tmdbId: Int?,
    val kind: TitleKind,
    val title: String,
    val originalTitle: String,
    val year: String,
    val genres: List<String> = emptyList(),
    /** Скільки джерел сервер знає для цієї позиції. `0` — показувати нічим. */
    val sources: Int = 0,
)

/**
 * Український каталог сервера — те, чого в TMDB немає за побудовою.
 *
 * Тут лежать позиції, які прийшли з наших джерел (телефільми 1+1 і решта), і саме через
 * цю ручку вони потрапляють в українські ряди. Ряд при цьому не «з джерела»: він за
 * змістом, а джерел у нього два — TMDB discover і цей каталог.
 *
 * Заміряно 15.09.2026: ручка розуміє лише `limit` (решта параметрів ігнорується) і віддає
 * не більше ста позицій, уже впорядкованих від нового. Зображень у відповіді немає взагалі
 * — картку доводиться домальовувати з TMDB, і саме тому позиції без `tmdb_id` (3 зі 100)
 * у ряди не потрапляють: ані кадру, ані сторінки, яку можна відкрити, для них немає.
 */
@Singleton
class UkrainianCatalog @Inject constructor(
    private val config: CatalogConfig,
) {

    @Volatile
    private var cached: List<UkrainianTitle>? = null

    @Volatile
    private var cachedAt = 0L

    /**
     * Кеш із терміном життя, а не на весь процес.
     *
     * Ручка віддає «що з'явилось українською», тобто саме те, що змінюється щодня. Кеш
     * без терміну означав, що новинка чекає перезапуску лаунчера.
     */
    suspend fun titles(): List<UkrainianTitle> {
        val fresh = cached?.takeIf { System.currentTimeMillis() - cachedAt < CATALOG_CACHE_MS }
        if (fresh != null) return fresh
        return load().also {
            if (it.isNotEmpty()) {
                cached = it
                cachedAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun load(): List<UkrainianTitle> {
        val base = config.streamsUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/ukrainian?limit=$LIMIT", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Український каталог не приїхав", it) }
                .getOrNull()
        } ?: return emptyList()
        return runCatching { parseUkrainian(body) }
            .onFailure { Log.w(TAG, "Український каталог не розібрався", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "LikhtarUkrainian"
        const val TIMEOUT_MS = 6_000

        /** Стеля самої ручки — сто позицій; просити більше немає сенсу. */
        const val LIMIT = 100
    }
}

/**
 * Розбір `/api/ukrainian`.
 *
 * Окремо від репозиторію, бо тут живе межа, на якій легко помилитись: `tmdb_id` буває
 * відсутній, і підставляти замість нього `catalog_id` означало б піти в TMDB з чужим
 * номером і показати не той фільм.
 */
internal fun parseUkrainian(body: String): List<UkrainianTitle> {
    val array = JSONObject(body).optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val raw = array.optJSONObject(index) ?: return@mapNotNull null
        val catalogId = raw.optInt("catalog_id").takeIf { it > 0 } ?: return@mapNotNull null
        val title = raw.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val genres = raw.optJSONArray("genres")
        UkrainianTitle(
            catalogId = catalogId,
            tmdbId = raw.optInt("tmdb_id").takeIf { it > 0 },
            kind = if (raw.optString("kind") == "tv") TitleKind.TV else TitleKind.MOVIE,
            title = title,
            originalTitle = raw.optString("original_title").ifBlank { title },
            year = raw.optInt("year").takeIf { it > 0 }?.toString().orEmpty(),
            genres = (0 until (genres?.length() ?: 0)).mapNotNull { position ->
                genres?.optString(position)?.takeIf { it.isNotBlank() }
            },
            sources = raw.optInt("sources"),
        )
    }
}
