package com.likhtar.home.core.tv

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Телепрограма для каналів Sweet.TV.
 *
 * Їхній основний API (`api.sweet.tv`, gRPC) вимагає токен пристрою, але веб-сторінка
 * «Телепрограма» ходить у публічний `webapi/v2/public/…` без жодної авторизації:
 * `epg/<дата>` віддає всі канали з id і slug, `tv-program/channel/<slug>` — поточну
 * передачу і розклад на три доби. Цим і користуємось для карток, які не збіглися
 * з каталогом MEGOGO (насамперед кіношні канали).
 */
@Singleton
class SweetTvEpgRepository @Inject constructor() {

    // slug стабільний, тож мапу id → slug досить оновлювати раз на добу.
    @Volatile private var slugs: Map<Long, String> = emptyMap()
    @Volatile private var slugsDay: String = ""

    // Сотня каналів — сотня запитів водночас: і Sweet.TV ріже, і приставка захлинається.
    private val requests = Semaphore(PARALLEL_REQUESTS)

    /** Ключ результату — sweet-id каналу (число з `sweettv://channel/<id>`). */
    suspend fun nowOnAir(channelIds: Collection<Long>): Map<Long, ChannelSchedule> =
        withContext(Dispatchers.IO) {
            val ids = channelIds.distinct()
            if (ids.isEmpty()) return@withContext emptyMap()
            val slugByIdRaw = runCatching { slugMap() }
                .onFailure { Log.w(TAG, "Sweet.TV не віддав список каналів", it) }
                .getOrNull() ?: return@withContext emptyMap()

            coroutineScope {
                ids.mapNotNull { id -> slugByIdRaw[id]?.let { slug -> id to slug } }
                    .map { (id, slug) ->
                        async {
                            runCatching { requests.withPermit { id to channelProgramme(slug) } }
                                .onFailure { Log.w(TAG, "Sweet.TV EPG впав для $slug", it) }
                                .getOrNull()
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .mapNotNull { (id, programme) -> programme?.let { id to it } }
                    .toMap()
            }
        }

    private fun slugMap(): Map<Long, String> {
        // Локальний форматер: SimpleDateFormat не потокобезпечний, а сюди заходять
        // паралельні корутини.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        if (slugsDay == today && slugs.isNotEmpty()) return slugs
        val json = JSONObject(get("$BASE/epg/$today"))
        val channels = json.getJSONArray("channels")
        val map = buildMap {
            for (i in 0 until channels.length()) {
                val channel = channels.getJSONObject(i)
                put(channel.getLong("id"), channel.getString("slug"))
            }
        }
        slugs = map
        slugsDay = today
        return map
    }

    private fun channelProgramme(slug: String): ChannelSchedule? {
        val json = JSONObject(get("$BASE/tv-program/channel/$slug"))
        // Розклад по днях зливаємо в один список.
        val items = buildList {
            val days = json.optJSONObject("epgItems") ?: return@buildList
            days.keys().forEach { day ->
                val list = days.getJSONArray(day)
                for (i in 0 until list.length()) add(list.getJSONObject(i).toProgramme())
            }
        }
        val current = json.optJSONObject("currentEpg")?.toProgramme()
        return sweetSchedule(items, current, System.currentTimeMillis())
    }

    private fun JSONObject.toProgramme() = EpgProgramme(
        title = optString("name"),
        startMillis = optLong("time_start") * 1000,
        endMillis = optLong("time_stop") * 1000,
        description = optString("text"),
        // static.sweet.tv віддає http, а cleartext на приставці заборонений.
        image = optString("epg_preview_url").takeIf { it.isNotBlank() }?.replace("http://", "https://"),
    )

    private fun get(url: String): String =
        http(url, TIMEOUT_MS, headers = mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")) { it.text() }

    private companion object {
        const val TAG = "SweetTvEpg"
        const val BASE = "https://sweet.tv/webapi/v2/public/ua-uk"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        const val TIMEOUT_MS = 10_000
        const val STALE_TOLERANCE_S = 30 * 60L
        const val PARALLEL_REQUESTS = 4
    }
}

/**
 * Що на каналі Sweet.TV о [now]: з його ж розкладу, а не з `currentEpg`.
 *
 * `currentEpg` у них кешується: то відстає (передача скінчилась 5–20 хв тому), то забігає
 * наперед (називає «поточною» ту, що почнеться за годину). Обидва випадки ховали канал із ТБ
 * Ефіру — уночі так зникала чверть каналів. Тому: спершу передача розкладу, що накриває «зараз»;
 * далі `currentEpg`, якщо вона справді йде; а в паузі між передачами — найближча наступна (картка
 * показує «о 03:40»), щоб канал лишався на місці. `null` — провайдер не дав нічого.
 */
internal fun sweetSchedule(items: List<EpgProgramme>, current: EpgProgramme?, now: Long): ChannelSchedule? {
    val sorted = items.sortedBy { it.startMillis }
    val upcoming = sorted.filter { it.startMillis > now }.take(SWEET_UPCOMING)
    val on = sorted.firstOrNull { it.startMillis <= now && it.endMillis > now }
        ?: current?.takeIf { it.startMillis <= now && it.endMillis > now }
        ?: upcoming.firstOrNull()
        ?: current?.takeIf { it.startMillis > now }
        ?: return null
    val after = upcoming.filter { it.startMillis >= on.endMillis || it.startMillis > on.startMillis }
    return ChannelSchedule(
        live = NowOnAir(
            title = on.title,
            description = on.description,
            image = on.image,
            startMillis = on.startMillis,
            endMillis = on.endMillis,
            nextTitle = after.firstOrNull()?.title.orEmpty(),
            nextStartMillis = after.firstOrNull()?.startMillis ?: 0L,
            upcoming = after,
        ),
        upcoming = after,
    )
}

private const val SWEET_UPCOMING = 12
