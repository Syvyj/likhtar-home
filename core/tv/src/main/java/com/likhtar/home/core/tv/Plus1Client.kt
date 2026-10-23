package com.likhtar.home.core.tv

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** Проєкт 1+1 video: реаліті або розважальне шоу. */
data class Plus1Item(
    val id: String,
    val name: String,
    /**
     * Жанр від самого джерела — рівно два значення: «Реаліті» й «Розважальні шоу».
     *
     * На відміну від Телепорталу, тут таблиці немає й бути не може: 1+1 сам каже, що це.
     */
    val genre: String,
    val imagePath: String,
    /** Кадр 1155×470. Порожньо у 11 позицій із 79. */
    val imageBigPath: String = "",
    /** Широкий кадр 1920×830 зі сторінки проєкту. */
    val framePath: String = "",
    /** Дата останнього випуску, `2024-10-18`. Є у 68 позицій із 79. */
    val lastEpisode: String = "",
)

/** Сезон 1+1: id і назва. Випуски приходять окремим запитом, на відміну від Телепорталу. */
data class Plus1Season(
    val id: String,
    val title: String,
    /** Дата одного з випусків, `2025-09-14`: за нею сезони стають за часом. Порожньо — невідомо. */
    val date: String = "",
)

/** Серія з офіційного YouTube-плейлиста мовника. */
/** [season] — номер сезону, коли твір зведено з кількох плейлистів; 0 — один плейлист. */
/** [seconds] — тривалість, 0 — невідома: за нею видно, що YouTube закрився сам по кінці серії. */
data class PortalVideo(
    val id: String,
    val title: String,
    val image: String,
    val date: String,
    val playlist: String,
    val season: Int = 0,
    val seconds: Int = 0,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id&list=$playlist"
}

data class Plus1Episode(
    val id: String,
    val title: String,
    val date: String = "",
    val durationSeconds: Int = 0,
    val imageBigPath: String = "",
)

/** Сторінка проєкту: опис, картинка й сезони. */
data class Plus1Project(
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val seasons: List<Plus1Season> = emptyList(),
)

/**
 * Клієнт 1+1 video — через НАШ сервер, як і Телепортал.
 *
 * Чому окремий клієнт, а не параметр до Телепорталу: у них різні адреси сезонів і
 * випусків (там слаг проєкту й сезону, тут числові id), різні поля й різні причини
 * відмов. Один клієнт на два джерела перетворився б на розгалуження в кожному методі.
 *
 * Важливе про плеєр: посилання резолвиться щоразу наново й довго не живе — у токені
 * мітка часу. Тому не кешуємо його ніде, а питаємо перед самим запуском.
 *
 * Близько чверті випусків закриті геоблоком, і такі потоки сервер проводить через себе
 * (`/api/plus1/hls`). Для клієнта це звичайний HLS; знати різницю плеєру не потрібно.
 */
@Singleton
class Plus1Client @Inject constructor(
    private val server: LikhtarServer,
    private val http: TvHttp,
) {

    /** Той самий `stale`, що в Телепорталу: дані показуємо, але кеш тримаємо короткий. */
    @Volatile
    var catalogueStale: Boolean = false
        private set

    suspend fun catalogue(): List<Plus1Item> {
        val base = server.homeOnlyUrl() ?: return emptyList()
        val body = http.get("$base/api/plus1/catalog") ?: return emptyList()
        catalogueStale = runCatching { JSONObject(body).optBoolean("stale") }.getOrDefault(false)
        return parseCatalogue(body)
    }

    internal fun parseCatalogue(body: String): List<Plus1Item> = runCatching {
        val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
        (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = raw.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Plus1Item(
                id = id,
                name = name,
                genre = raw.optString("genre"),
                imagePath = raw.optString("image"),
                imageBigPath = raw.optString("image_big"),
                framePath = raw.optString("frame"),
                lastEpisode = raw.optString("last_episode"),
            )
        }
    }.onFailure { Log.w(TAG, "Каталог 1+1 не розібрався", it) }.getOrDefault(emptyList())

    /**
     * Офіційний YouTube-плейлист мовника для твору порталу (`teleportal:…`, `1plus1:…`).
     * Друге джерело відтворення без тунелю: коли Телепортал лежить, серіал однаково
     * вмикається. `null` — плейлиста не зіставили.
     */
    suspend fun youtubePlaylist(extKey: String): String? {
        val base = server.homeOnlyUrl() ?: return null
        val body = http.get("$base/api/portal-youtube?ext_key=${java.net.URLEncoder.encode(extKey, "UTF-8")}")
            ?: return null
        return runCatching { JSONObject(body).optString("playlist").takeIf { it.isNotBlank() } }.getOrNull()
    }

    /** Серії офіційного плейлиста мовника: id відео, назва, кадр, дата. */
    suspend fun youtubeVideos(extKey: String): List<PortalVideo> {
        val base = server.homeOnlyUrl() ?: return emptyList()
        val body = http.get("$base/api/portal-youtube?videos=1&ext_key=${java.net.URLEncoder.encode(extKey, "UTF-8")}")
            ?: return emptyList()
        return runCatching {
            val json = JSONObject(body)
            val playlist = json.optString("playlist")
            val array = json.optJSONArray("videos") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { i ->
                val v = array.optJSONObject(i) ?: return@mapNotNull null
                PortalVideo(
                    id = v.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    title = v.optString("title"),
                    image = v.optString("image"),
                    date = v.optString("date"),
                    playlist = v.optString("playlist").ifBlank { playlist },
                    season = v.optInt("season"),
                    seconds = v.optInt("seconds"),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun project(item: Plus1Item): Plus1Project? {
        val base = server.homeOnlyUrl() ?: return null
        val body = http.get("$base/api/plus1/seasons?id=${item.id}") ?: return null
        return parseProject(body)
    }

    internal fun parseProject(body: String): Plus1Project = runCatching {
        val json = JSONObject(body)
        val array = json.optJSONArray("results")
        Plus1Project(
            title = json.optString("title"),
            description = json.optString("description"),
            image = json.optString("image"),
            seasons = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val raw = array?.optJSONObject(index) ?: return@mapNotNull null
                val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Plus1Season(id = id, title = raw.optString("title").ifBlank { id }, date = raw.optString("date"))
            },
        )
    }.getOrDefault(Plus1Project())

    suspend fun episodes(season: Plus1Season): List<Plus1Episode> {
        val base = server.homeOnlyUrl() ?: return emptyList()
        val body = http.get("$base/api/plus1/episodes?id=${season.id}") ?: return emptyList()
        return parseEpisodes(body)
    }

    internal fun parseEpisodes(body: String): List<Plus1Episode> = runCatching {
        val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
        (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Plus1Episode(
                id = id,
                title = raw.optString("title").ifBlank { "Випуск" },
                date = raw.optString("date"),
                durationSeconds = raw.optInt("duration"),
                imageBigPath = raw.optString("image_big"),
            )
        }
    }.onFailure { Log.w(TAG, "Випуски 1+1 не розібралися", it) }.getOrDefault(emptyList())

    /**
     * Потік випуску. Не кешуємо: у токені мітка часу, і вчорашнє посилання вже мертве.
     *
     * Причини відмови ті самі, що в Телепорталу, і розрізняти їх так само важливо: `geo`
     * означає «не буде», `no_stream` — «запису немає».
     */
    suspend fun player(episodeId: String): TeleportalPlayback {
        val base = server.homeOnlyUrl() ?: return TeleportalPlayback(reason = TeleportalReason.SERVER)
        val body = http.get("$base/api/plus1/player?id=$episodeId")
            ?: return TeleportalPlayback(reason = TeleportalReason.SERVER)
        return parsePlayer(body)
    }

    internal fun parsePlayer(body: String): TeleportalPlayback = runCatching {
        val json = JSONObject(body)
        if (!json.optBoolean("ok")) {
            return@runCatching TeleportalPlayback(reason = TeleportalReason.of(json.optString("reason")))
        }
        val hls = json.optString("hls")
        if (hls.isBlank()) {
            TeleportalPlayback(reason = TeleportalReason.NO_STREAM)
        } else {
            TeleportalPlayback(url = hls, poster = json.optString("poster").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(TeleportalPlayback(reason = TeleportalReason.SERVER))

    private companion object {
        const val TAG = "LikhtarPlus1"
    }
}
