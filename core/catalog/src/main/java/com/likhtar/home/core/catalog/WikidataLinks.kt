package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import com.likhtar.home.core.model.StreamingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запасне джерело прямих посилань (Nottilo 1.3.2): коли сервер не дав посилання на картку,
 * Wikidata за TMDB id знає id тайтлу в Netflix, Disney+, Prime Video й Apple TV — і кнопка
 * відкриває саму сторінку фільму, а не головну сервісу. Без сервера й без ключа: приставка
 * питає query.wikidata.org сама, іде лише TMDB id.
 *
 * Адреси — ті, що застосунки оголошують у манфестах (`pm query-activities` на приставці).
 * Max не беремо: у Wikidata старі id HBO Max, а застосунок Max за ними показує «Контент
 * недоступний» (docs/internal/app-deeplinks.md).
 */
@Singleton
class WikidataLinks @Inject constructor() {
    private val cache = ConcurrentHashMap<String, Map<StreamingService, String>>()

    /** Порожньо — Wikidata нічого не знає чи недоступна; помилка мережі в кеш не йде. */
    suspend fun links(item: CatalogItem): Map<StreamingService, String> {
        val key = "${item.kind}:${item.tmdbId}"
        cache[key]?.let { return it }
        val body = withContext(Dispatchers.IO) {
            runCatching {
                val url = "$ENDPOINT?format=json&query=" + URLEncoder.encode(wikidataSparql(item.tmdbId, item.kind), "UTF-8")
                http(url, TIMEOUT_MS, headers = mapOf("Accept" to "application/sparql-results+json", "User-Agent" to USER_AGENT)) { c ->
                    if (c.responseCode == HttpURLConnection.HTTP_OK) c.text() else null
                }
            }.getOrNull()
        } ?: return emptyMap()
        return parseWikidataLinks(body, item.kind).also { cache[key] = it }
    }

    private companion object {
        const val ENDPOINT = "https://query.wikidata.org/sparql"
        const val TIMEOUT_MS = 4_000
        const val USER_AGENT = "LikhtarHome/1.0 (Android TV launcher)"
    }
}

/** P4947/P4983 — TMDB фільм/серіал; P1874 Netflix; P7595/P7596 Disney+; P8055 Prime; P9586/P9751 Apple TV. */
internal fun wikidataSparql(tmdbId: Int, kind: TitleKind): String {
    val movie = kind == TitleKind.MOVIE
    return "SELECT ?netflix ?disney ?prime ?apple WHERE { ?f wdt:${if (movie) "P4947" else "P4983"} \"$tmdbId\" . " +
        "OPTIONAL { ?f wdt:P1874 ?netflix } " +
        "OPTIONAL { ?f wdt:${if (movie) "P7595" else "P7596"} ?disney } " +
        "OPTIONAL { ?f wdt:P8055 ?prime } " +
        "OPTIONAL { ?f wdt:${if (movie) "P9586" else "P9751"} ?apple } } LIMIT 1"
}

/** Відповідь SPARQL → сторінка тайтлу в кожному сервісі, який Wikidata знає. */
internal fun parseWikidataLinks(json: String, kind: TitleKind): Map<StreamingService, String> {
    val row = runCatching { JSONObject(json).getJSONObject("results").getJSONArray("bindings").optJSONObject(0) }.getOrNull()
        ?: return emptyMap()
    fun id(name: String) = row.optJSONObject(name)?.optString("value")?.takeIf { SAFE_ID.matches(it) }
    val movie = kind == TitleKind.MOVIE
    return buildMap {
        id("netflix")?.let { put(StreamingService.NETFLIX, "https://www.netflix.com/title/$it") }
        id("disney")?.let { put(StreamingService.DISNEY, "https://www.disneyplus.com/${if (movie) "movies" else "series"}/x/$it") }
        id("prime")?.let { put(StreamingService.PRIME, "https://app.primevideo.com/detail?${if (it.startsWith("amzn1.")) "gti" else "asin"}=$it") }
        id("apple")?.let { put(StreamingService.APPLE, "https://tv.apple.com/${if (movie) "movie" else "show"}/$it") }
    }
}

/** Лише символи id: чуже значення з Wikidata не дописує в адресу шлях чи параметри. */
private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,80}")
