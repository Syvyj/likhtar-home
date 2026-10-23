package com.likhtar.home.core.catalog

import android.util.Log
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG_OFFICIAL = "LikhtarCatalog"

/** Готовий діплінк від сервера: у якому застосунку і за якою адресою відкривати фільм. */
data class ResolvedOption(
    val service: String,
    val label: String,
    /**
     * Кандидати в порядку переваги — беремо перший встановлений.
     *
     * Список, а не один пакет, бо назва застосунку сервісу залежить від пристрою. Приклад:
     * `rakuten.tv` оголошує `tv.wuaki.apptv` (`pm query-activities`), а
     * `tv.rakuten.android`, єдиний пакет у тодішній мапі сервера, на пристрої **не встановлений**
     * (`pm list packages`). Один пакет означав, що робоча кнопка тихо зникала.
     *
     * Заміряно саме «не встановлений тут», а не «не існує» — тому в списку лишаються **обидва**
     * кандидати. Викидати другий немає підстав: на іншій збірці він може бути тим, що працює.
     */
    val packages: List<String>,
    val deeplink: String,
    /** `FLATRATE`, `RENT`, `BUY`, `ADS`, `FREE`. Порожньо для megogo та sweet.tv. */
    val monetization: String = "",
    /** `official`, `own`, `torrent` — група від сервера. */
    val group: String = "official",
    /** Що на цьому сервісі українською: `uk` | `subs` | порожньо — не знаємо. */
    val audio: String = "",
    /**
     * Місце в списку від сервера. **Єдине джерело порядку** — клієнт нічого не пересортовує.
     *
     * Шкала живе на сервері: безкоштовне мовників, далі megogo й sweet.tv, далі міжнародна
     * підписка, далі оренда й купівля. Причина такого порядку:
     * `FLATRATE` у сервісі без сесії показує екран входу (Prime), а megogo — єдиний, у кого
     * приставка пам'ятає історію відтворення. Дублювати цю логіку тут означало б два місця
     * з одним правилом.
     */
    val order: Int = 0,
)

/**
 * Розбір `/api/watch` — одна ручка з усім, що можна відкрити, вже впорядкована сервером.
 *
 * До 01.09.2026 картка збирала це з двох ручок (`/api/official` + `/api/availability`), а до
 * того сама ходила в неофіційний JustWatch із зашитим `country = "UA"`. Тепер порядок і
 * країни — серверні, а клієнт лише вибирає, що з цього відкриється **на цьому** пристрої.
 *
 * Беремо `deeplink`, коли він є, і `url` інакше. Це не стиль, а замір: `megogo.net` не
 * claim'ить жоден застосунок, крім вбудованого браузера sweet.tv, тож веб-адреса megogo
 * виглядала б робочою й вела на сайт.
 *
 * Рядки груп `own` і `torrent` тут відкидаються: у них немає адреси для відтворення, лише
 * `ref` (сторінка сайту або хеш роздачі). Прямі потоки й далі готує [LikhtarStreams] — саме
 * він знає про прогрів роздачі й плейлисти серій.
 */
/**
 * Іменовані студії озвучки з тієї самої відповіді.
 *
 * Окремо від [parseWatch], бо там рядок без пакета застосунку відкидається — і правильно:
 * там ідеться про те, ЯКИМ застосунком відкрити. Студія ж має інший сенс: це не сервіс, а
 * авторство доріжки, і воно потрібне ще до того, як людина натисне «Озвучки».
 */
internal fun parseDubStudios(body: String): List<String> {
    val array = JSONObject(body).optJSONArray("results") ?: return emptyList()
    val out = LinkedHashSet<String>()
    for (index in 0 until array.length()) {
        val raw = array.optJSONObject(index) ?: continue
        if (raw.optString("group") != "dub") continue
        // Підпис торента має вигляд «DniproFilm · 1080p» — на кнопці потрібна лише студія.
        val label = raw.optString("label").substringBefore(" ·").trim()
        if (label.isNotBlank()) out += label
    }
    return out.toList()
}

internal fun parseWatch(body: String): List<ResolvedOption> {
    val array = JSONObject(body).optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val raw = array.optJSONObject(index) ?: return@mapNotNull null
        val target = raw.optString("deeplink").ifBlank { raw.optString("url") }
        // `packages` — кандидати від сервера; `package` читаємо як запасний, щоб клієнт не
        // залежав від того, чи вже викотили нову форму.
        val packages = raw.optJSONArray("packages")
            ?.let { list -> (0 until list.length()).map(list::optString) }
            ?: listOf(raw.optString("package"))
        val candidates = packages.filter { it.isNotBlank() }
        // Без пакета відкривати нічим (`player.pl`, `pilotwp`, `canalplus` застосунків на
        // приставці не мають), без адреси — нічого відкривати.
        if (target.isBlank() || candidates.isEmpty()) return@mapNotNull null
        val service = raw.optString("service")
        ResolvedOption(
            service = service,
            label = raw.optString("label").ifBlank { service },
            packages = candidates,
            deeplink = target,
            monetization = raw.optString("monetization"),
            group = raw.optString("group").ifBlank { "official" },
            order = raw.optInt("order", index),
            audio = raw.optString("audio"),
        )
    }.sortedBy { it.order }
}


/**
 * Клієнт до `/api/watch` — ручки «де це можна подивитися» на власному сервері.
 *
 * Навіщо він узагалі: OTT-застосунки приймають лише свої внутрішні id, а TMDB їх не віддає.
 * Уся «магія» — це сервер, який тримає відповідність «фільм → id у сервісі». Пошук по
 * каталогах вимагає українського IP і браузерного TLS-фінгерпринта, тому робить це сервер,
 * а не приставка.
 *
 * Одна ручка, а не чотири: сервер бачить обидві сторони (і офіційні сервіси, і наші
 * джерела), тому порядок між ними — його рішення. Клієнт відповідає лише на питання, на яке
 * сервер відповісти не може: що з цього відкриється **на цьому** пристрої.
 *
 * Порожня адреса означає «сервер не налаштований» — сторінка фільму просто лишається з
 * тими варіантами, які лаунчер знає сам (рядок TMDB).
 */
/**
 * Що сервер знає про тайтл, окремо від того, що знайшов.
 *
 * Без цих двох полів «джерел немає» не відрізнити від «ще не дивились»: у щойно доданого
 * тайтла й у перевіреного-без-знахідок `counts` однакові нулі. Сервер додав `known` і
 * `scanned` саме для цього 03.09.
 */
data class WatchScan(
    /** `false` — тайтла в каталозі сервера немає взагалі. */
    val known: Boolean,
    /** Unix-час обходу; нуль — тайтл у черзі, ще не обходили. */
    val scannedAt: Long,
    /**
     * Статус тайтла з TMDB: `Returning Series`, `Ended`, `Canceled`, `Released`…
     *
     * Порожньо — TMDB статусу не дав. Це «не знаємо», а не «немає»: підпису тоді просто
     * не малюємо, бо вигадувати тут нічого.
     */
    val status: String = "",
) {
    /** Чи можна довіряти нулям у counts. */
    val checked: Boolean get() = scannedAt > 0
}

@Singleton
class ResolverClient @Inject constructor(
    private val config: CatalogConfig,
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<ResolvedOption>>()
    private val scans = java.util.concurrent.ConcurrentHashMap<String, WatchScan>()

    /**
     * Усе, що можна відкрити, одним запитом.
     *
     * Країну задає користувач у налаштуваннях Кінозалу; `auto` — сервер визначає її за своїм
     * виходом у мережу. MEGOGO і SWEET.TV країною не керуються й приходять українськими
     * завжди — саме те, що потрібно приставці за VPN.
     */
    /**
     * Студії озвучки за тим самим ключем, що й [watch].
     *
     * Кладемо сюди, а не в список варіантів: студія — це не «чим відкрити», а «чия
     * доріжка», і кнопка для неї стоїть окремо від сервісів.
     */
    private val dubs = mutableMapOf<String, List<String>>()

    /** Які студії озвучили цей тайтл. Порожньо, доки не спитали [watch]. */
    fun dubStudiosOf(item: CatalogItem): List<String> = dubs[item.id].orEmpty()

    /**
     * Сервіси для позиції порталу за її ключем (`teleportal:stb/odna-rodina`): сервер сам
     * знаходить однойменний твір на MEGOGO, Sweet.TV, youtv — TMDB-id у порталу немає.
     */
    suspend fun watchByExtKey(extKey: String, serial: Boolean): List<ResolvedOption> {
        val base = config.resolverUrl() ?: return emptyList()
        val url = "$base/api/watch?ext_key=${java.net.URLEncoder.encode(extKey, "UTF-8")}" +
            "&kind=${if (serial) "tv" else "movie"}&country=${config.availabilityCountry()}&groups=official"
        return withContext(Dispatchers.IO) {
            runCatching { httpGet(url, TIMEOUT_MS)?.let(::parseWatch).orEmpty() }.getOrDefault(emptyList())
        }
    }

    suspend fun watch(item: CatalogItem): List<ResolvedOption> {
        val base = config.resolverUrl() ?: return emptyList()
        // Саме НУЛЬ: відʼємний id — це позиція нашого каталогу поза TMDB (портал, ютуб
        // мовника, MEGOGO, sweet.tv), і в неї посилання на сервіс якраз є. Доти ця
        // перевірка мовчки лишала такі картки без жодного способу ввімкнутись.
        if (item.tmdbId == 0) return emptyList()
        val country = config.availabilityCountry()
        // Країна в ключі кеша: змінили її в налаштуваннях — старі пропозиції самі перестають
        // підходити, окремого скидання не треба. Оригінальна назва теж у ключі: до деталей
        // вона порожня, після — ні, і це різні запити до сервера, а не один.
        val key = "${item.id}@$country@${item.originalTitle}"
        cache[key]?.let { return it }

        val url = buildString {
            append(base).append("/api/watch")
            append("?tmdb_id=").append(item.tmdbId)
            // `kind` обов'язковий: у TMDB id фільмів і серіалів — різні простори, і без нього
            // сервер шукав би фільм із номером серіала.
            append("&kind=").append(if (item.kind == TitleKind.TV) "tv" else "movie")
            append("&country=").append(country)
            // Збірка для магазину — лише офіційні сервіси: без сайтів і роздач.
            if (com.likhtar.home.core.model.Distribution.store) append("&groups=official")
            // Для тайтлів поза каталогом сервера це єдина назва, за якою він може спитати
            // JustWatch — і заразом те, що змушує його перевірити тайтл негайно, не чекаючи
            // на щоденний обхід. Для каталожних просто зайва.
            if (item.originalTitle.isNotBlank()) {
                append("&original=").append(encode(item.originalTitle))
            }
        }

        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet(url, TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Сервер «де подивитися» недоступний", it) }
                .getOrNull()
        }
        val parsed = body?.let { runCatching { parseWatch(it) }.getOrDefault(emptyList()) }.orEmpty()
            // Мітка «UA» на кнопці сервісу — лише україномовним: англійцю в США вона нічого не каже
            // (скріншоти для публікації).
            .let { list -> if (config.ukrainianAudience()) list else list.map { if (it.audio == "uk") it.copy(audio = "") else it } }
        body?.let { raw ->
            dubs[item.id] = runCatching { parseDubStudios(raw) }.getOrDefault(emptyList())
        }
        body?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                scans[key] = WatchScan(
                    known = json.optBoolean("known"),
                    scannedAt = json.optLong("scanned"),
                    status = json.optString("status"),
                )
            }
        }

        // `countries` і `counts` у лог: без них «порожньо» не відрізнити ні від «спитали не ту
        // країну», ні від «сервер знайшов, але клієнт відкинув через пакети».
        Log.i(TAG, "watch ${item.tmdbId}: ${diagnostics(body, country)} → " +
            parsed.joinToString { "${it.service}/${it.group}" }.ifBlank { "нічого не відкрити" })
        cache[key] = parsed
        return parsed
    }

    /** Стан обходу для тайтла; `null` — ще не питали сервер. */
    suspend fun scanOf(item: CatalogItem): WatchScan? =
        scans["${item.id}@${config.availabilityCountry()}@${item.originalTitle}"]

    /** Що сервер зрозумів і що він мав — рядком для логу. */
    private fun diagnostics(body: String?, asked: String): String {
        val json = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return "просили $asked, сервер не відповів"
        val understood = json.optJSONArray("countries")
            ?.let { list -> (0 until list.length()).joinToString(",", transform = list::optString) }
        val counts = json.optJSONObject("counts")
            ?.let { c -> "офіційних ${c.optInt("official")}, наших ${c.optInt("own")}, торентів ${c.optInt("torrent")}" }
        return "просили $asked, сервер зрозумів ${understood ?: "?"}; ${counts ?: "без counts"}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = TAG_OFFICIAL

        /** Сервер ходить по двох сайтах через проксі — на секунду-дві це нормально. */
        const val TIMEOUT_MS = 12_000
    }
}
