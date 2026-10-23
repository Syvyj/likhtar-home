package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.locale
import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Ключ TMDB і адреса Ліхтаря: із налаштувань, із відкатом на значення збірки. */
@Singleton
class CatalogConfig @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    /** Мова й країна контенту: вибір людини, інакше системні ([ContentLocale]). */
    suspend fun locale(): com.likhtar.home.core.data.ContentLocale = settingsRepository.settings.first().locale

    /**
     * Чи каталог для україномовного глядача. Ні — мітка UA, фільтр «лише з українською» й ряд
     * «Українське» поляку нічого не кажуть (наприклад, польський Google TV).
     */
    suspend fun ukrainianAudience(): Boolean = locale().language == "uk"

    /**
     * Ключ TMDB. У збірці для магазину ключа в APK немає: запити йдуть через проксі нашого
     * сервера ([tmdbApi]), і він підставляє свій — тоді тут заглушка, щоб клієнт не мовчав.
     */
    suspend fun tmdbKey(): String? {
        if (com.likhtar.home.core.model.Distribution.store) return streamsUrl()?.let { PROXY_KEY }
        return settingsRepository.settings.first().tmdbApiKey
            .ifBlank { BuildConfig.DEFAULT_TMDB_KEY }
            .takeIf { it.isNotBlank() }
    }

    /** Корінь TMDB API: напряму — або через проксі нашого сервера в збірці для магазину. */
    suspend fun tmdbApi(): String =
        if (com.likhtar.home.core.model.Distribution.store) "${streamsUrl()}/tmdb/api/3" else TMDB_API

    /**
     * Адреса нашого сервера — та сама для `/api/watch`, стрімів і карток Ліхтаря.
     *
     * Спершу те, що ввів користувач, і лише потім дефолт збірки. Зашита адреса живе
     * рівно до наступного лізингу DHCP: 03.09.2026 Mac переїхав з `.236` на `.234`, і
     * клієнт замовк цілком — а мусив лікуватися одним полем у Налаштуваннях, без APK.
     */
    private suspend fun serverUrl(fallback: String): String? =
        settingsRepository.settings.first().likhtarBaseUrl
            .ifBlank { fallback }
            .takeIf { it.isNotBlank() }
            ?.trimEnd('/')

    suspend fun resolverUrl(): String? = serverUrl(BuildConfig.DEFAULT_RESOLVER_URL)

    /**
     * Те, що є лише на домашньому сервері: прогрес, закладки, профілі, полиці, MEGOGO, запис
     * (POST). Публічний шлюз збірки з сайту цього не пропускає, тож там адреси немає зовсім —
     * інакше кожен розділ слав у шлюз запити на 404 (16 разів
     * «Продовжити» за один обхід), а політика приватності обіцяє, що туди нічого не йде.
     */
    suspend fun homeOnlyUrl(): String? = if (com.likhtar.home.core.model.Distribution.store) null else streamsUrl()

    /**
     * Країни для `/api/watch`. Порожній вибір — `auto`, тобто «вирішуй сам».
     *
     * Сортуємо, щоб ключ кеша не залежав від порядку перебору набору.
     */
    /**
     * Країни кнопок «Дивитися в»: задані вручну (VPN, кілька країн), інакше вибрана в
     * майстрі, інакше `auto` — сервер вирішує за своїм виходом у мережу.
     */
    suspend fun availabilityCountry(): String {
        val current = settingsRepository.settings.first()
        val manual = current.availabilityCountries.sorted().joinToString(",")
        val loc = current.locale
        return manual.ifBlank { if (loc.chosen) loc.country else "auto" }
    }

    /** Адреса власного сервера джерел. Порожня — кнопки «Дивитись» немає. */
    suspend fun streamsUrl(): String? = serverUrl(BuildConfig.DEFAULT_LIKHTAR_STREAMS)

    suspend fun likhtarUrl(): String? = serverUrl(BuildConfig.DEFAULT_LIKHTAR_URL)
}

/** Уся курація Ліхтаря, як вона лежить в асеті. */
data class LikhtarCuration(
    val rows: List<CatalogRow> = emptyList(),
    val moods: List<MoodTile> = emptyList(),
    val studios: List<CatalogStudio> = emptyList(),
    val chronologies: List<Chronology> = emptyList(),
)

/**
 * Курація Ліхтаря, запечена в APK: стрічка, сторінки сервісів, настрої й хронології.
 *
 * Курація живе в проєкті Ліхтаря (`studios.js`, `UKRAINIAN_FEED_CATEGORIES`,
 * `chronologies.json`), а сюди потрапляє через `tools/import_likhtar_rows.js`. Запечено, а не
 * тягнеться мережею, бо це визначення рядків, а не контент: вони змінюються релізом, а не
 * щогодини — і на відміну від карток Ліхтаря мусять працювати без його сервера.
 */
@Singleton
class LikhtarRows @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cached: LikhtarCuration? = null

    suspend fun load(): LikhtarCuration = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }

        val parsed = runCatching {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val json = JSONObject(text)

            val rows = json.optJSONArray("rows").orEmptyArray()
                .mapObjects { raw, index -> row(raw, "row-$index") }
                .filter { it.title.isNotBlank() && it.path.isNotBlank() }

            val moods = json.optJSONArray("moods").orEmptyArray().mapObjects { raw, _ ->
                MoodTile(
                    title = raw.optString("title"),
                    genres = raw.optJSONArray("genres").orEmptyArray().toStringList(),
                )
            }.filter { it.title.isNotBlank() }

            val studios = json.optJSONArray("studios").orEmptyArray().mapObjects { raw, _ ->
                val id = raw.optString("id")
                CatalogStudio(
                    id = id,
                    title = raw.optString("title"),
                    rows = raw.optJSONArray("rows").orEmptyArray()
                        .mapObjects { rowJson, index -> row(rowJson, "studio-$id-$index") }
                        .filter { it.title.isNotBlank() && it.path.isNotBlank() },
                    page = pageRows(raw),
                )
            }.filter { it.id.isNotBlank() && it.rows.isNotEmpty() }

            val chronologies = json.optJSONArray("chronologies").orEmptyArray().mapObjects { raw, _ ->
                Chronology(
                    key = raw.optString("key"),
                    title = raw.optString("title"),
                    entries = raw.optJSONArray("items").orEmptyArray().mapObjects { entry, _ ->
                        ChronologyEntry(title = entry.optString("title"), year = entry.optInt("year"))
                    }.filter { it.title.isNotBlank() },
                    logoPath = raw.optString("logo").takeIf(String::isNotBlank),
                    tvQuery = raw.optString("tv").takeIf(String::isNotBlank),
                    collections = raw.optJSONArray("collections")?.let { ids ->
                        (0 until ids.length()).map { ids.optInt(it) }.filter { it > 0 }
                    }.orEmpty(),
                    page = pageRows(raw),
                )
            }.filter { it.key.isNotBlank() && it.entries.isNotEmpty() }

            LikhtarCuration(rows = rows, moods = moods, studios = studios, chronologies = chronologies)
        }.onFailure { Log.w(TAG, "Не вдалося прочитати куровану добірку", it) }
            .getOrDefault(LikhtarCuration())

        cached = parsed
        parsed
    }

    private fun row(raw: JSONObject, id: String) = CatalogRow(
        id = id,
        title = raw.optString("title"),
        source = raw.optString("source"),
        path = raw.optString("path"),
        params = raw.optJSONObject("params").toStringMap(),
    )

    private companion object {
        const val TAG = "LikhtarCatalog"
        const val ASSET = "likhtar-rows.json"
    }
}

/**
 * Картки «Чи варто?» по HTTP: `<base>/cards/<id>.json` — сервер Ліхтаря віддає їх із бази,
 * а картки, якої ще немає, пише у фоні (з'являється з наступного відкриття).
 */
@Singleton
class LikhtarCards @Inject constructor(
    private val config: CatalogConfig,
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, LikhtarCard>()

    suspend fun card(item: CatalogItem): LikhtarCard {
        cache[item.likhtarKey]?.let { return it }
        val base = config.likhtarUrl() ?: return LikhtarCard()

        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                val body = httpGet("$base/cards/${item.likhtarKey}.json", TIMEOUT_MS)
                    ?: return@runCatching LikhtarCard()
                parse(JSONObject(body))
            }.onFailure { Log.w(TAG, "Картка Ліхтаря ${item.likhtarKey} недоступна") }
                .getOrDefault(LikhtarCard())
        }

        // «Картки ще немає» не кешуємо: сервер допише її у фоні, і з наступного відкриття
        // вона має з'явитись, а не чекати перезапуску лаунчера.
        if (parsed != LikhtarCard()) cache[item.likhtarKey] = parsed
        return parsed
    }

    private fun parse(json: JSONObject): LikhtarCard {
        val watch = json.optJSONObject("watch")
        return LikhtarCard(
            summary = json.optString("summary"),
            worth = watch?.optString("worth").orEmpty(),
            forWhom = watch?.optString("for").orEmpty(),
            notForWhom = watch?.optString("not_for").orEmpty(),
            moods = json.optJSONArray("mood").orEmptyArray().toStringList(),
            complexity = json.optJSONObject("complexity")?.optInt("score") ?: 0,
            violence = json.optJSONObject("violence")?.optInt("score") ?: 0,
            erotica = json.optJSONObject("erotica")?.optInt("score") ?: 0,
            childrenFriendly = json.optJSONObject("children")?.optBoolean("recommended") ?: false,
            complexityNote = json.optJSONObject("complexity")?.optString("description").orEmpty(),
            violenceNote = json.optJSONObject("violence")?.optString("description").orEmpty(),
            eroticaNote = json.optJSONObject("erotica")?.optString("description").orEmpty(),
            childrenNote = json.optJSONObject("children")?.optString("description").orEmpty(),
            endingExplained = json.optString("ending_explained"),
            easterEggs = json.optJSONArray("easter_eggs").orEmptyArray().toStringList(),
        )
    }

    private companion object {
        const val TAG = "LikhtarCatalog"
        const val TIMEOUT_MS = 6_000
    }
}

private fun JSONArray?.orEmptyArray(): JSONArray = this ?: JSONArray()

private fun <T> JSONArray.mapObjects(map: (JSONObject, Int) -> T): List<T> =
    (0 until length()).mapNotNull { index -> optJSONObject(index)?.let { map(it, index) } }

/** Непорожні рядки JSON-масиву. */
internal fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optString(it) }
}

/**
 * Ряди кураторської сторінки (всесвіт чи стрімінг). `m:1726` — фільм, `t:61889` — серіал;
 * `u:mcu` і `c:119` — картки сторінок (всесвіт, колекція TMDB), вони йдуть окремо.
 */
internal fun pageRows(raw: JSONObject): List<UniverseRow> =
    raw.optJSONArray("page").orEmptyArray().mapObjects { row, _ ->
        val refs = row.optJSONArray("items").orEmptyArray().let { list ->
            (0 until list.length()).map { list.optString(it) }
        }
        UniverseRow(
            title = row.optString("title"),
            layout = row.optString("layout"),
            items = refs.mapNotNull { ref ->
                val (k, id) = ref.split(":", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val kind = when (k) {
                    "t" -> TitleKind.TV
                    "m" -> TitleKind.MOVIE
                    else -> return@mapNotNull null
                }
                id.toIntOrNull()?.let { kind to it }
            },
            pages = refs.filter { it.startsWith("u:") || it.startsWith("c:") },
            serverIndex = row.optInt("index", -1),
        )
    }

private const val TMDB_API = "https://api.themoviedb.org/3"

/** Проксі сервера ключ перезаписує своїм; значення неважливе, аби не порожнє. */
private const val PROXY_KEY = "proxy"
