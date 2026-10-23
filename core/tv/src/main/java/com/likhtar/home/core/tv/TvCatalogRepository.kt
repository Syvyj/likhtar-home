package com.likhtar.home.core.tv

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import com.likhtar.home.core.data.locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Канал із загального каталогу разом із його телепрограмою. */
data class CatalogChannel(val link: ChannelLink, val schedule: ChannelSchedule?)

/** Весь каталог ефіру: канали й порядок наших жанрів. */
/**
 * [groups] — порядок рядів; [lead] — головні українські канали (префікс назви без пробілів),
 * першими й саме так; [hero] — групи слайдера в порядку показу. Усе — від сервера.
 */
data class TvCatalog(
    val channels: List<CatalogChannel>,
    val groups: List<String>,
    val lead: List<String> = emptyList(),
    val hero: List<String> = emptyList(),
    /** Законні безкоштовні ТВ-застосунки — пропозиція, коли в людини немає жодного джерела. */
    val free: List<FreeTvApp> = emptyList(),
)

data class FreeTvApp(val name: String, val packageName: String)

/**
 * Усі канали Sweet.TV і MEGOGO з нашим жанром і програмою (`/api/tv-channels`).
 *
 * Збирає їх сервер: окремий запит програми на кожен із трьохсот каналів приставці не під
 * силу. Це доповнення до власних прив'язок, а не заміна: ті — особисті підписки з MEGOGO-id
 * для програми, і вони лишаються першими.
 */
@Singleton
class TvCatalogRepository @Inject constructor(
    private val server: LikhtarServer,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val settings: com.likhtar.home.core.data.SettingsRepository,
) {
    /**
     * Канали країни людини (майстер першого запуску): Польща — свій набір із програмою TVP і
     * XMLTV, решта — Sweet.TV і MEGOGO. Лишаються лише ті, чий застосунок стоїть на приставці:
     * каналом, якого нічим відкрити, сторінка лише дратувала б (емулятор з польським Google TV).
     */
    suspend fun catalog(): TvCatalog = withContext(Dispatchers.IO) {
        val base = server.baseUrl() ?: return@withContext TvCatalog(emptyList(), emptyList())
        // Країна — вибір людини, інакше системна; сервер знає PL, решті віддає типовий набір.
        // І мова: українською діаспорі за кордоном сервер дає українські канали, решті — свої або нічого.
        val locale = runCatching { settings.settings.first().locale }.getOrNull()
        val country = locale?.let { "?country=${it.country}&lang=${it.language}" }.orEmpty()
        runCatching {
            http("$base/api/tv-channels$country", TIMEOUT_MS) { parseTvCatalog(it.text()) }
        }.onFailure { Log.w(TAG, "Каталог каналів недоступний", it) }
            .getOrDefault(TvCatalog(emptyList(), emptyList()))
            .let { tv -> tv.copy(channels = tv.channels.filter { installed(it.link.packageName) }) }
    }

    /** Без кешу: застосунок могли щойно поставити, а перевірка — мікросекунди. */
    private fun installed(pkg: String): Boolean =
        pkg.isBlank() || runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private companion object {
        const val TAG = "LikhtarTvCatalog"
        const val TIMEOUT_MS = 20_000
    }
}

internal fun parseTvCatalog(body: String): TvCatalog {
    val root = JSONObject(body)
    fun strings(key: String) = root.optJSONArray(key)?.let { a -> (0 until a.length()).map(a::optString) }.orEmpty()
    val groups = strings("groups")
    val array = root.optJSONArray("channels") ?: return TvCatalog(emptyList(), groups)
    val channels = (0 until array.length()).mapNotNull { i ->
        val c = array.optJSONObject(i) ?: return@mapNotNull null
        val id = c.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
        val programmes = c.optJSONArray("schedule")?.let { s ->
            (0 until s.length()).mapNotNull { j ->
                val p = s.optJSONObject(j) ?: return@mapNotNull null
                EpgProgramme(
                    title = p.optString("title"),
                    startMillis = p.optLong("start") * 1000,
                    endMillis = p.optLong("end") * 1000,
                    description = p.optString("description"),
                    image = p.optString("image").takeIf(String::isNotBlank),
                )
            }
        }.orEmpty()
        val now = System.currentTimeMillis()
        // Кінець 0 — прямий ефір без кінця (YouTube): іде, доки сервер його віддає.
        val live = programmes.firstOrNull { it.startMillis <= now && (it.endMillis > now || it.endMillis == 0L) }
        CatalogChannel(
            link = ChannelLink(
                name = c.optString("title"),
                // Посилання й застосунок каже сервер: канали бувають і Sweet.TV, і MEGOGO.
                packageName = c.optString("package").ifBlank { com.likhtar.home.core.model.StreamingService.SWEET.packageName },
                url = c.optString("url").ifBlank { "sweettv://channel/$id" },
                category = c.optString("group"),
                logo = c.optString("icon"),
                mark = c.optString("mark"),
            ),
            schedule = if (programmes.isEmpty()) null else ChannelSchedule(
                live = live?.let {
                    NowOnAir(
                        title = it.title,
                        description = it.description,
                        image = it.image,
                        startMillis = it.startMillis,
                        endMillis = it.endMillis,
                    )
                },
                upcoming = programmes.filter { it.startMillis > now },
            ),
        )
    }
    val free = root.optJSONArray("free")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { FreeTvApp(it.optString("name"), it.optString("package")) } }
            .filter { it.name.isNotBlank() && it.packageName.isNotBlank() }
    }.orEmpty()
    return TvCatalog(channels, groups, lead = strings("lead"), hero = strings("hero"), free = free)
}
