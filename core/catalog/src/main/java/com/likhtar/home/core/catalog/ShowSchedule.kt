package com.likhtar.home.core.catalog

import org.json.JSONObject

/** Серія за номером і датою виходу (ISO; дата країни походження, без години). */
data class EpisodeRef(val season: Int, val episode: Int, val name: String, val airDate: String)

/**
 * Розклад серіалу з одного запиту TMDB `/tv/{id}`: що вийшло останнім, що далі, скільки серій у
 * сезонах. [status] — як у TMDB (`Returning Series`, `Ended`, `Canceled`, …).
 */
data class ShowSchedule(
    val item: CatalogItem,
    val status: String,
    val last: EpisodeRef?,
    val next: EpisodeRef?,
    val seasonSizes: Map<Int, Int>,
)

internal fun parseShowSchedule(tmdbId: Int, body: String): ShowSchedule? = runCatching {
    val json = JSONObject(body)
    fun path(k: String) = json.optString(k).takeIf { it.isNotBlank() && it != "null" }
    fun episode(k: String): EpisodeRef? {
        val e = json.optJSONObject(k) ?: return null
        val date = e.optString("air_date").takeIf { it.length >= 10 } ?: return null
        return EpisodeRef(e.optInt("season_number"), e.optInt("episode_number"), e.optString("name"), date)
    }
    val seasons = json.optJSONArray("seasons")
    // Сезон 0 — спецвипуски: у лік серій сезону не входять.
    val sizes = (0 until (seasons?.length() ?: 0)).mapNotNull { seasons?.optJSONObject(it) }
        .filter { it.optInt("season_number") > 0 }
        .associate { it.optInt("season_number") to it.optInt("episode_count") }
    ShowSchedule(
        item = CatalogItem(
            tmdbId = tmdbId,
            kind = TitleKind.TV,
            title = json.optString("name"),
            originalTitle = json.optString("original_name"),
            year = json.optString("first_air_date").take(4),
            posterPath = path("poster_path"),
            backdropPath = path("backdrop_path"),
        ),
        status = json.optString("status"),
        last = episode("last_episode_to_air"),
        next = episode("next_episode_to_air"),
        seasonSizes = sizes,
    )
}.getOrNull()

/** `/movie/{id}/release_dates` → (тип, ISO-дата) для [region]: лише 3 (кіно) і 4 (онлайн). */
internal fun parseReleaseDates(body: String, region: String): List<Pair<Int, String>> = runCatching {
    val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
    val country = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        .firstOrNull { it.optString("iso_3166_1").equals(region, ignoreCase = true) }
        ?.optJSONArray("release_dates") ?: return@runCatching emptyList()
    (0 until country.length()).mapNotNull { country.optJSONObject(it) }
        .filter { it.optInt("type") == 3 || it.optInt("type") == 4 }
        .mapNotNull { r -> r.optString("release_date").takeIf { it.length >= 10 }?.let { r.optInt("type") to it.take(10) } }
        .distinct()
}.getOrDefault(emptyList())
