package com.likhtar.home.core.catalog

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Що сервер вичитав із рядка телепрограми: чиста назва, сезон і серія, твір, якщо знає. */
data class EpgMatch(
    val name: String,
    val season: Int = 0,
    val episode: Int = 0,
    /** Твір TMDB у форматі рядів Кінозалу; `null` — не знаємо або збіг неоднозначний. */
    val item: CatalogItem? = null,
)

/**
 * `POST /api/epg-match`: назви передач → твори. Телепрограму збирає приставка, сервер лише
 * зіставляє. Кеш — на весь час життя процесу: «Аліта» завтра та сама «Аліта».
 *
 * Невідповіді не кешуємо: сервер за запит шукає в TMDB лише дюжину нових назв, решта
 * доїжджає наступними оновленнями.
 */
@Singleton
class EpgMatchRepository @Inject constructor(
    private val config: CatalogConfig,
) {
    private val cache = ConcurrentHashMap<String, EpgMatch>()

    suspend fun match(titles: Collection<String>): Map<String, EpgMatch> {
        val missing = titles.filter { it.isNotBlank() && cache[it]?.item == null }.distinct()
        val base = config.homeOnlyUrl()
        if (missing.isNotEmpty() && base != null) {
            withContext(Dispatchers.IO) {
                runCatching { request(base, missing) }
                    .onFailure { Log.w(TAG, "epg-match не відповів", it) }
                    .getOrNull()
            }?.let(cache::putAll)
        }
        return titles.mapNotNull { title -> cache[title]?.let { title to it } }.toMap()
    }

    private fun request(base: String, titles: List<String>): Map<String, EpgMatch> {
        val reply = httpPostJson("$base/api/epg-match", JSONObject().put("titles", JSONArray(titles)).toString(), TIMEOUT_MS)
        return reply.body?.let(::parseEpgMatch) ?: emptyMap()
    }

    private companion object {
        const val TAG = "LikhtarEpgMatch"
        const val TIMEOUT_MS = 25_000
    }
}

internal fun parseEpgMatch(body: String): Map<String, EpgMatch> {
    val results = JSONObject(body).optJSONObject("results") ?: return emptyMap()
    return results.keys().asSequence().mapNotNull { raw ->
        val entry = results.optJSONObject(raw) ?: return@mapNotNull null
        raw to EpgMatch(
            name = entry.optString("name"),
            season = entry.optInt("season"),
            episode = entry.optInt("episode"),
            item = entry.optJSONObject("item")?.let { card ->
                parseCard(card).takeIf { it.tmdbId > 0 }
            },
        )
    }.toMap()
}
