package com.likhtar.home.core.tv

import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.TvPortal
import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Портал Stalker / Ministra (обліковка провайдера з логіном і ключем). Входимо як приставка MAG:
 * `handshake` дає токен, `do_auth` — вхід, далі список каналів, програма й посилання на потік.
 *
 * Канал у ТБ Ефірі — адреса `stalker://p/<id>/video.m3u8`: справжню (з токеном, що живе недовго)
 * портал видає на кожне ввімкнення, тож її бере плеєр у момент відкриття. Хвіст після номера
 * лишається тим, що просили, — так працюють і архів Flussonic (`index-<час>-now.m3u8`), і буфер
 * (`mpegts`).
 */
@Singleton
class StalkerPortal @Inject constructor(
    private val settings: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val lock = Mutex()
    @Volatile private var session: Pair<String, String>? = null // адреса порталу → токен

    suspend fun account(): TvPortal? = TvPortal.decode(settings.settings.first().tvPortal)?.takeIf { it.valid }

    @Volatile private var cached: Triple<String, Long, List<ChannelLink>>? = null

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    @Volatile private var refreshing = false

    /**
     * Канали порталу з групами; порожньо — порталу немає або він не відповів. Список на дві тисячі
     * каналів міняється рідко — тримаємо пів години, а не просимо на кожне оновлення ТБ Ефіру.
     * Холодний старт бере збережений на диску список одразу, а свіжий тягне у фоні: інакше
     * ТБ Ефір чекав порталу ~7 с.
     */
    suspend fun channels(): List<ChannelLink> = withContext(Dispatchers.IO) {
        val portal = account() ?: return@withContext emptyList()
        cached?.takeIf { it.first == portal.encode() && System.currentTimeMillis() - it.second < CACHE_MS }?.let { return@withContext it.third }
        val saved = fromDisk(portal)
        if (saved.isNotEmpty()) {
            if (!refreshing) {
                refreshing = true
                background.launch { try { fetch(portal) } finally { refreshing = false } }
            }
            return@withContext saved
        }
        fetch(portal) ?: emptyList()
    }

    private fun disk() = java.io.File(context.cacheDir, "portal-channels.json")

    private fun fromDisk(portal: TvPortal): List<ChannelLink> = runCatching {
        val o = JSONObject(disk().readText())
        if (o.optString("p") != portal.url) emptyList() else {
            val g = o.getJSONObject("g").let { j -> j.keys().asSequence().associateWith { j.getString(it) } }
            parsePortalChannels(o.getJSONArray("d"), g, TvPortal.base(portal.url))
        }
    }.getOrDefault(emptyList())

    private suspend fun fetch(portal: TvPortal): List<ChannelLink>? = runCatching {
        // Портал часом відповідає порожнім `js` (навантаження, перелогін іншої приставки) — ще раз.
        val raw = call(portal, "type=itv&action=get_all_channels") as? JSONObject
            ?: run { kotlinx.coroutines.delay(2_000); call(portal, "type=itv&action=get_all_channels") as JSONObject }
        val genres = call(portal, "type=itv&action=get_genres").let { js ->
            (js as? JSONArray)?.let { a -> (0 until a.length()).associate { a.getJSONObject(it).let { g -> g.optString("id") to g.optString("title") } } }.orEmpty()
        }
        val data = raw.getJSONArray("data")
        parsePortalChannels(data, genres, TvPortal.base(portal.url)).also {
            if (it.isNotEmpty()) {
                cached = Triple(portal.encode(), System.currentTimeMillis(), it)
                runCatching { disk().writeText(JSONObject().put("p", portal.url).put("g", JSONObject(genres)).put("d", data).toString()) }
            }
        }
    }.onFailure { Log.w(TAG, "Портал не віддав канали (${it.javaClass.simpleName})") }.getOrNull()

    @Volatile private var failures = 0

    /**
     * Найближчі передачі каналів [ids] — для тих, кого немає в XMLTV. Порожній список у відповіді —
     * у порталу програми цього каналу немає (трансляції, радіо, кінозали); його теж повертаємо, щоб
     * не питати знову кожні 5 хвилин.
     */
    suspend fun shortEpg(ids: Collection<String>): Map<String, List<EpgProgramme>> = withContext(Dispatchers.IO) {
        val portal = account() ?: return@withContext emptyMap()
        val permits = Semaphore(EPG_PARALLEL)
        coroutineScope {
            ids.take(EPG_MAX).map { id ->
                async {
                    permits.withPermit {
                        runCatching {
                            val js = call(portal, "type=itv&action=get_short_epg&ch_id=${enc(id)}&size=$EPG_SIZE")
                            id to parseShortEpg(js as? JSONArray)
                        }
                            .onFailure { if (failures++ < 3) Log.w(TAG, "програма порталу для $id: ${it.javaClass.simpleName} ${it.message}") }
                            .getOrNull()
                    }
                }
            }.awaitAll().filterNotNull().also { all -> Log.i(TAG, "програма порталу: відповіли ${all.size} з ${ids.size}, з передачами ${all.count { it.second.isNotEmpty() }}") }
                .toMap()
        }
    }

    /** Справжня адреса потоку [link] (номер з `cmd` каналу) з токеном (живе недовго). */
    suspend fun link(link: String): String? = withContext(Dispatchers.IO) {
        val portal = account() ?: return@withContext null
        runCatching {
            val js = call(portal, "type=itv&action=create_link&cmd=${enc("ffrt http:///ch/$link")}&forced_storage=0&disable_ad=0") as JSONObject
            js.optString("cmd").substringAfter(' ').trim().takeIf { it.startsWith("http") }
        }.getOrNull()
    }

    private suspend fun call(portal: TvPortal, query: String): Any? {
        val base = TvPortal.base(portal.url)
        val token = lock.withLock {
            session?.takeIf { it.first == base }?.second ?: login(portal, base).also { session = base to it }
        }
        return runCatching { request(portal, base, query, token) }.getOrElse {
            // Токен міг злетіти (портал перезапустився, інша приставка зайшла) — один раз заново.
            val fresh = lock.withLock { login(portal, base).also { t -> session = base to t } }
            request(portal, base, query, fresh)
        }
    }

    private fun login(portal: TvPortal, base: String): String {
        val token = (request(portal, base, "type=stb&action=handshake&token=", null) as JSONObject).getString("token")
        val ok = request(portal, base, "type=stb&action=do_auth&login=${enc(portal.login)}&password=${enc(portal.key)}&device_id=&device_id2=", token)
        if (ok != true) error("портал не прийняв логін")
        // Профіль «приставки»: без нього портал нового пристрою віддає порожню програму.
        runCatching { request(portal, base, PROFILE, token) }
        return token
    }

    private fun request(portal: TvPortal, base: String, query: String, token: String?): Any? =
        http("$base/server/load.php?$query&JsHttpRequest=1-xml", TIMEOUT_MS, headers = headers(portal, base, token)) { c ->
            if (c.responseCode != 200) error("портал: HTTP ${c.responseCode}")
            JSONObject(c.text()).opt("js")
        }

    private companion object {
        const val TAG = "LikhtarPortal"
        const val TIMEOUT_MS = 20_000
        const val EPG_PARALLEL = 6
        const val PROFILE = "type=stb&action=get_profile&hd=1&ver=&num_banks=2&sn=&stb_type=MAG250&image_version=218&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0"
        const val CACHE_MS = 30 * 60 * 1000L
        const val EPG_SIZE = 8

        /** ponytail: програма порталу — запит на канал; більше цього за раз не просимо, решта лишається без програми. */
        const val EPG_MAX = 1500

        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    }
}

/** Заголовки, з якими портал приймає «приставку MAG». */
internal fun headers(portal: TvPortal, base: String, token: String?): Map<String, String> = buildMap {
    put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
    put("X-User-Agent", "Model: MAG250; Link: WiFi")
    put("Referer", "$base/c/")
    put("Cookie", "mac=${URLEncoder.encode(portal.mac, "UTF-8")}; stb_lang=en; timezone=Europe%2FKiev")
    token?.let { put("Authorization", "Bearer $it") }
}

/**
 * Адреса каналу порталу; справжню бере плеєр (див. [StalkerPortal]). Два номери: [id] каналу в
 * порталі (за ним програма) і [link] потоку з його `cmd` (`ffrt http:///ch/<link>` — за ним
 * `create_link`). Вони різні: 221 і 40803.
 */
fun portalUrl(id: String, link: String) = "stalker://p/$id-$link/video.m3u8"

/** Номер каналу порталу з його адреси; `null` — це не канал порталу. */
fun portalId(url: String): String? = url.takeIf { it.startsWith("stalker://p/") }?.removePrefix("stalker://p/")?.substringBefore('/')?.substringBefore('-')

/** Номер потоку каналу порталу (для `create_link`). */
fun portalLink(url: String): String? = url.takeIf { it.startsWith("stalker://p/") }?.removePrefix("stalker://p/")?.substringBefore('/')?.substringAfter('-', "")?.ifBlank { null }

internal fun parsePortalChannels(data: JSONArray, genres: Map<String, String>, base: String = ""): List<ChannelLink> =
    (0 until data.length()).mapNotNull { i ->
        val c = data.getJSONObject(i)
        val id = c.optString("id").ifBlank { return@mapNotNull null }
        val link = c.optString("cmd").substringAfter("/ch/", "").substringBefore(' ').trim().ifBlank { return@mapNotNull null }
        val url = portalUrl(id, link)
        // tv_archive_duration — у годинах; архів лише там, де портал його вмикає.
        val hours = c.optString("tv_archive_duration").toIntOrNull() ?: 0
        val archived = c.optString("tv_archive") == "1" || c.optString("enable_tv_archive") == "1"
        ChannelLink(
            name = c.optString("name"),
            packageName = "",
            url = url,
            category = genres[c.optString("tv_genre_id")].orEmpty(),
            epgId = c.optString("xmltv_id"),
            // Логотип — ім'я файла в теці порталу (misc/logos/<розмір>/).
            logo = c.optString("logo").let { l -> if (l.startsWith("http") || l.isBlank()) l else "$base/misc/logos/240/$l" },
            archive = if (archived && hours > 0) ChannelArchive.of("flussonic", (hours + 23) / 24, "", url) else null,
        )
    }

internal fun parseShortEpg(a: JSONArray?): List<EpgProgramme> {
    a ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val e = a.getJSONObject(i)
        val start = e.optLong("start_timestamp") * 1000
        val end = e.optLong("stop_timestamp") * 1000
        if (start <= 0 || end <= start) return@mapNotNull null
        EpgProgramme(e.optString("name").trimStart('⋗', ' '), start, end, e.optString("descr"))
    }.sortedBy { it.startMillis }
}
