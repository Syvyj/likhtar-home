package com.likhtar.home.core.tv

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Що йде на каналі зараз: назва передачі, кадр і межі показу. */
data class NowOnAir(
    val title: String,
    val description: String = "",
    val image: String?,
    val startMillis: Long,
    val endMillis: Long,
    /** Наступна передача з того ж EPG-вікна; порожня, якщо провайдер її не віддав. */
    val nextTitle: String = "",
    val nextStartMillis: Long = 0L,
    /** Наступні записи з того самого вікна EPG для бічної панелі каналу. */
    val upcoming: List<EpgProgramme> = emptyList(),
    /**
     * Розпис віддано з кешу, бо MEGOGO був недоступний.
     *
     * Годинної давнини розпис кращий за порожній екран, але «зараз в ефірі» для такого
     * каналу може бути неправдою — на екрані це має бути видно, а не замовчано.
     */
    val stale: Boolean = false,
) {
}

/**
 * Телепрограма каналу: що йде зараз і що буде далі.
 *
 * Роздільно навмисно. Раніше розпис висів на «зараз в ефірі»: провайдер не знав поточної
 * передачі — і зникали ЗАОДНО всі майбутні, хоч вони прийшли й були правильні. У рядах це
 * було майже непомітно, а в сітці «канали × час» дало порожній рядок там, де насправді є
 * розпис на три години вперед (кадр о 00:16).
 */
data class ChannelSchedule(
    /** `null` — провайдер не знає, що йде цієї хвилини, або назвав майбутнє. */
    val live: NowOnAir?,
    val upcoming: List<EpgProgramme> = emptyList(),
)

data class EpgProgramme(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String = "",
    /** Кадр передачі. Саме він робить сітку схожою на референс — без нього голий текст. */
    val image: String? = null,
)

/**
 * Розбір відповіді `/api/epg`.
 *
 * Функція верхнього рівня, бо в ній два правила, які легко зламати мовчки: час приходить
 * у СЕКУНДАХ, і позначка `stale` мусить ГАСНУТИ, коли приїхав свіжий розпис. Друге не
 * менш важливе за перше: позначка, яка не гасне, гірша за відсутню — вона привчає її
 * ігнорувати. Тут воно виходить саме собою, бо мапа збирається наново на кожну відповідь,
 * і тест стереже саме це.
 */
internal fun parseEpg(body: String): Map<String, ChannelSchedule> {
    val json = JSONObject(body)
    // Канали, для яких сервер віддав останнє відоме: MEGOGO був недоступний. Розпис
    // годинної давнини кращий за порожній екран, але «зараз в ефірі» для них може
    // бути неправдою — тому позначаємо, а не мовчимо.
    val stale = json.optJSONArray("stale")?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty().toSet()
    val results = json.optJSONObject("results") ?: return emptyMap()
    return results.keys().asSequence().mapNotNull { id ->
        val channel = results.optJSONObject(id) ?: return@mapNotNull null
        val live = channel.optJSONObject("live")?.let { programme ->
            NowOnAir(
                title = programme.optString("title"),
                description = programme.optString("description"),
                image = programme.optString("image").takeIf { it.isNotBlank() },
                startMillis = programme.optLong("start") * 1000,
                endMillis = programme.optLong("end") * 1000,
                stale = id in stale,
            )
        }
        val upcoming = channel.optJSONArray("upcoming")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val programme = array.optJSONObject(index) ?: return@mapNotNull null
                EpgProgramme(
                    title = programme.optString("title").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null,
                    // Секунди, не мілісекунди — у контракті це виділено окремо, бо
                    // помилка на 1000× тиха: сітка просто порожня.
                    startMillis = programme.optLong("start") * 1000,
                    endMillis = programme.optLong("end") * 1000,
                    description = programme.optString("description"),
                    image = programme.optString("image").takeIf { it.isNotBlank() },
                )
            }
        }.orEmpty()
        id to ChannelSchedule(
            live = live?.copy(
                nextTitle = upcoming.firstOrNull()?.title.orEmpty(),
                nextStartMillis = upcoming.firstOrNull()?.startMillis ?: 0L,
                upcoming = upcoming,
            ),
            upcoming = upcoming,
        )
    }.toMap()
}

/**
 * Звідки брати адресу нашого сервера.
 *
 * Не тільки для телепрограми: через ту саму адресу ходить і Телепортал, і все, що сервер
 * віддає замість прямого походу назовні.
 *
 * Шов, а не пряма залежність: адреса живе в `:core:catalog` разом із флейворами
 * `personal`/`play`, а `:core:tv` флейворів не має. Потягнути каталог сюди означало б
 * рознести вимір флейворів ще на чотири модулі фіч — у цьому репозиторії таке вже
 * коштувало болю. Тому інтерфейс тут, реалізація в `:app`, який і так має обидва.
 */
interface LikhtarServer {
    suspend fun baseUrl(): String?

    /** Ручки лише домашнього сервера (портали Телепорталу й 1+1): у збірці з сайту шлюз їх не пропускає. */
    suspend fun homeOnlyUrl(): String? = if (com.likhtar.home.core.model.Distribution.store) null else baseUrl()
}

/**
 * Телепрограма через наш сервер (`/api/epg`).
 *
 * У MEGOGO лаунчер більше не ходить, і не тому, що так зручніше: заміряно, що
 * їхній ендпойнт віддає порожньо з польського виходу й повний розпис з українського.
 * VPN на кожній приставці цього не лікує — приставка в чужій мережі отримає той самий
 * нуль, — тож геозапит робить сервер, у якого український вихід уже є (там само живуть
 * megogo-каталог і JustWatch).
 *
 * Один запит на всі 76 id: кеш у сервера спільний, розбивати на пачки не треба.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val server: LikhtarServer,
) {

    /** Ключ результату — той самий `epg`-id, що лежить у `channel-links.json`. */
    suspend fun schedules(channelIds: Collection<String>): Map<String, ChannelSchedule> =
        withContext(Dispatchers.IO) {
            val ids = channelIds.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) return@withContext emptyMap()
            val base = server.baseUrl() ?: return@withContext emptyMap()
            val url = "$base/api/epg?ids=${ids.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }}&ahead=$AHEAD"
            runCatching { parseEpg(get(url)) }
                .onFailure { Log.w(TAG, "Телепрограма з сервера недоступна", it) }
                .getOrDefault(emptyMap())
        }

    private fun get(url: String): String = http(url, TIMEOUT_MS, headers = mapOf("User-Agent" to USER_AGENT)) { it.text() }

    private companion object {
        const val TAG = "LikhtarTv"
        const val USER_AGENT = "LIKHTAR-TV-launcher/0.1 (Android TV)"
        const val TIMEOUT_MS = 15_000

        /** Скільки майбутніх передач на канал просимо в сервера. */
        const val AHEAD = 12
    }
}
