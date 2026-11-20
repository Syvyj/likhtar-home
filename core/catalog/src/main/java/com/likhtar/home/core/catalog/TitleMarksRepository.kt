package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.SettingsRepository
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Позначка тайтла з кнопки «Позначити»: переглянуто, пропустити, не рекомендувати.
 *
 * [mark] — watched | skip | hide; [scope] — all | season | upto | episode | later;
 * [reason] — seen | genre | franchise | title; [rating] — like | ok | dislike.
 * [season]/[episode] — до якої серії закрито. [fromFranchise] — сховано разом із франшизою.
 */
data class TitleMark(
    val mark: String,
    val scope: String = "",
    val reason: String = "",
    val rating: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val fromFranchise: Boolean = false,
)

/**
 * Коли востаннє змінювалась позначка — ряди Кінозалу й Реаліті, зібрані раніше, застаріли:
 * переглянуте й сховане мають зникнути одразу, а не за пів години кешу.
 */
object TitleMarksRevision {
    @Volatile
    var changedAt: Long = 0L
}

/**
 * Позначки: у домашній збірці — на сервері (scanner `/marks`), свої в кожного профілю; у
 * публічній — на самій приставці ([local]): шлюз `/api/marks` не пропускає, і позначки
 * мовчки не зберігались.
 */
@Singleton
class TitleMarksRepository @Inject constructor(
    private val config: CatalogConfig,
    private val settings: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val local get() = context.getSharedPreferences("title_marks", android.content.Context.MODE_PRIVATE)

    private suspend fun localKey(item: CatalogItem) =
        "${settings.settings.first().activeProfileId}:${item.kind.tmdbPath}:${item.tmdbId}"

    private suspend fun droppedKey() = "dropped:${settings.settings.first().activeProfileId}"

    private suspend fun likedKey() = "liked:${settings.settings.first().activeProfileId}"

    /** Вподобане (like) — найсвіжіше першим; для рядів «Бо сподобалось» у Кінозалі ([updateLiked]). */
    suspend fun likedTitles(): List<CatalogItem> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = org.json.JSONArray(local.getString(likedKey(), "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CatalogItem(
                    tmdbId = o.getInt("id"),
                    kind = if (o.optString("kind") == TitleKind.TV.tmdbPath) TitleKind.TV else TitleKind.MOVIE,
                    title = o.optString("title"),
                    year = o.optString("year"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun noteLiked(item: CatalogItem, liked: Boolean) {
        val next = updateLiked(likedTitles(), item, liked)
        val arr = org.json.JSONArray()
        next.forEach { arr.put(org.json.JSONObject().put("id", it.tmdbId).put("kind", it.kind.tmdbPath).put("title", it.title).put("year", it.year)) }
        local.edit { putString(likedKey(), arr.toString()) }
    }

    /**
     * Кинуті серіали (позначка hide) — список на приставці. Сервер кинуте з прогресу прибирає, а
     * списку позначок не віддає; без цього «Кинуті» в Ліхтарику не було б звідки взяти. Пише сюди
     * кожна позначка, де б її не поставили (Ліхтарик, сторінка тайтла, ряди).
     */
    suspend fun droppedShows(): List<CatalogItem> = withContext(Dispatchers.IO) {
        local.getStringSet(droppedKey(), emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
            .map { CatalogItem(tmdbId = it, kind = TitleKind.TV, title = "", year = "") }
    }

    private suspend fun noteDropped(item: CatalogItem, dropped: Boolean) {
        if (item.kind != TitleKind.TV) return
        val key = droppedKey()
        val ids = local.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (dropped) ids += item.tmdbId.toString() else ids -= item.tmdbId.toString()
        local.edit { putStringSet(key, ids) }
    }

    suspend fun markOf(item: CatalogItem): TitleMark? = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl()
            ?: return@withContext local.getString(localKey(item), null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }
        runCatching {
            parse(JSONObject(httpGet("$base/api/marks?${query(item)}", TIMEOUT_MS) ?: return@runCatching null))
        }.getOrNull()
    }

    /** `null` — сервер відмовив або не відповів. */
    suspend fun setMark(
        item: CatalogItem,
        mark: String,
        scope: String = "",
        reason: String = "",
        rating: String = "",
        season: Int = 0,
        episode: Int = 0,
    ): TitleMark? = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: run {
            val json = JSONObject().put("mark", mark).put("scope", scope).put("reason", reason)
                .put("rating", rating).put("season", season).put("episode", episode)
            local.edit { putString(localKey(item), json.toString()) }
            noteDropped(item, mark == "hide")
            noteLiked(item, rating == "like")
            TitleMarksRevision.changedAt = System.currentTimeMillis()
            return@withContext parse(json)
        }
        runCatching {
            val payload = JSONObject()
                .put("profile_id", settings.settings.first().activeProfileId)
                .put("tmdb_id", item.tmdbId)
                .put("kind", item.kind.tmdbPath)
                .put("mark", mark)
                .put("scope", scope)
                .put("reason", reason)
                .put("rating", rating)
                .put("season", season)
                .put("episode", episode)
                .put("title", item.title)
                .put("year", item.year.toIntOrNull() ?: 0)
                .toString()
            val reply = httpPostJson("$base/api/marks", payload, SAVE_TIMEOUT_MS)
            if (!reply.ok) return@runCatching null
            noteDropped(item, mark == "hide")
            noteLiked(item, rating == "like")
            TitleMarksRevision.changedAt = System.currentTimeMillis()
            parse(JSONObject(reply.body.orEmpty()))
        }.getOrNull()
    }

    suspend fun clearMark(item: CatalogItem): Boolean = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: run {
            local.edit { remove(localKey(item)) }
            noteDropped(item, false)
            noteLiked(item, false)
            TitleMarksRevision.changedAt = System.currentTimeMillis()
            return@withContext true
        }
        val ok = runCatching { httpDelete("$base/api/marks?${query(item)}", TIMEOUT_MS) in 200..299 }.getOrDefault(false)
        if (ok) {
            noteDropped(item, false)
            noteLiked(item, false)
            TitleMarksRevision.changedAt = System.currentTimeMillis()
        }
        ok
    }

    private suspend fun query(item: CatalogItem): String {
        val profile = URLEncoder.encode(settings.settings.first().activeProfileId, "UTF-8")
        return "profile=$profile&tmdb_id=${item.tmdbId}&kind=${item.kind.tmdbPath}"
    }

    private fun parse(json: JSONObject): TitleMark? {
        val mark = json.optString("mark").takeIf { it.isNotBlank() } ?: return null
        return TitleMark(
            mark = mark,
            scope = json.optString("scope"),
            reason = json.optString("reason"),
            rating = json.optString("rating"),
            season = json.optInt("season"),
            episode = json.optInt("episode"),
            fromFranchise = json.optInt("parent") > 0,
        )
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
        // «Весь серіал» — сервер добирає з TMDB усі сезони, перш ніж відповісти.
        const val SAVE_TIMEOUT_MS = 30_000
    }
}
