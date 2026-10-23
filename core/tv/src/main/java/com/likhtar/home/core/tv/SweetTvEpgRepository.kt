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
        val current = json.optJSONObject("currentEpg") ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000

        // Розклад по днях зливаємо в один список: панелі потрібні лише наступні передачі.
        val upcoming = buildList {
            val days = json.optJSONObject("epgItems") ?: return@buildList
            days.keys().forEach { day ->
                val items = days.getJSONArray(day)
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    // Поріг — «зараз», а не кінець поточної передачі: коли `currentEpg`
                    // з майбутнього (заміряно), її `time_stop` теж із майбутнього,
                    // і фільтр по ньому викидав увесь справжній розпис.
                    if (item.optLong("time_start") >= nowSeconds) {
                        add(
                            EpgProgramme(
                                title = item.optString("name"),
                                startMillis = item.optLong("time_start") * 1000,
                                endMillis = item.optLong("time_stop") * 1000,
                                description = item.optString("text"),
                                // static.sweet.tv віддає http, а cleartext заборонений.
                                image = item.optString("epg_preview_url")
                                    .takeIf { it.isNotBlank() }
                                    ?.replace("http://", "https://"),
                            ),
                        )
                    }
                }
            }
        }.sortedBy { it.startMillis }.take(12)

        // Розпис віддаємо в будь-якому разі — він приходить окремо від `currentEpg` і
        // не залежить від того, чи провайдер угадав поточну передачу.
        val schedule = ChannelSchedule(live = null, upcoming = upcoming)

        // Інколи currentEpg застаріла (кеш на їхньому боці) — тоді картка збила б прогрес.
        if (current.optLong("time_stop") < nowSeconds - STALE_TOLERANCE_S) return schedule
        // …а інколи вона з майбутнього, і це не теоретично: заміряно о 00:14 —
        // для «Кус-кус HD» Sweet віддав `currentEpg` «Котлетка Серія 6» із
        // `time_start` за 106 хвилин уперед. Годинники приставки й Мака при цьому
        // збігались, тобто таймзона тут ні до чого: провайдер сам назвав «зараз» те,
        // що буде о 02:00. Захист був лише від минулого, і несиметричність вилізла в
        // сітці: блок ставав на своє правильне місце за часом, але превʼю писало
        // «ЗАРАЗ В ЕФІРІ» й «лишилось 1 год 56 хв» про передачу, яка не йде.
        if (current.optLong("time_start") > nowSeconds + STALE_TOLERANCE_S) return schedule

        return schedule.copy(
            live = NowOnAir(
                title = current.optString("name"),
                image = current.optString("epg_preview_url")
                    .takeIf { it.isNotBlank() }
                    // static.sweet.tv віддає http, а cleartext на приставці заборонений.
                    ?.replace("http://", "https://"),
                startMillis = current.optLong("time_start") * 1000,
                endMillis = current.optLong("time_stop") * 1000,
                nextTitle = upcoming.firstOrNull()?.title.orEmpty(),
                nextStartMillis = upcoming.firstOrNull()?.startMillis ?: 0L,
                upcoming = upcoming,
            ),
        )
    }

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
