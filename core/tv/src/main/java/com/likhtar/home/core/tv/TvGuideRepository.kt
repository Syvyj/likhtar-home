package com.likhtar.home.core.tv

import com.likhtar.home.core.data.http
import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/** Стан розділу «ТВ Ефір»: що показувати і чи все для цього є. */
data class TvGuideState(
    val loading: Boolean = false,
    val channels: List<TvChannel> = emptyList(),
    val groups: List<TvGroup> = emptyList(),
    /** Передачі за id каналу, відсортовані за часом початку. */
    val programmes: Map<String, List<TvProgramme>> = emptyMap(),
    val guideConfigured: Boolean = false,
    /** Скільки каналів користувач приховав — щоб було що повертати в меню. */
    val hiddenCount: Int = 0,
    /** id каналу → id застосунку, яким його відкривати. */
    val channelApps: Map<String, String> = emptyMap(),
    val message: String? = null,
)

/**
 * Канали, групи й телепрограма розділу «ТВ Ефір».
 *
 * Джерела навмисно різні й незалежні: список каналів — каталог iptv-org за обраними країнами
 * (він безкоштовний і покриває весь світ), телепрограма — XMLTV за URL із налаштувань.
 * Готового безкоштовного XMLTV на всі країни не існує: для частини країн є публічні файли,
 * для решти (зокрема України) телепрограму дає провайдер користувача. Тому розділ корисний
 * і без XMLTV — з каналами й групами, — а програма додається, коли джерело вкажуть.
 */
@Singleton
class TvGuideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: IptvOrgCatalog,
    private val settingsRepository: SettingsRepository,
    private val groupsRepository: TvGroupsRepository,
) {
    private val reload = MutableStateFlow(0)

    // Один завантажувач на всіх, хто дивиться: без shareIn кожен колектор качав і парсив би
    // свій XMLTV.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<TvGuideState> = combine(
        settingsRepository.settings.map { Config(it.tvCountries, it.tvEpgUrl) }.distinctUntilChanged(),
        groupsRepository.groups,
        groupsRepository.channelPrefs,
        reload,
    ) { config, customGroups, prefs, _ -> Input(config, customGroups, prefs) }
        .flatMapLatest { load(it) }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    suspend fun countries(): List<TvCountry> = catalog.countries()

    fun refresh() {
        reload.value += 1
    }

    private fun load(input: Input): Flow<TvGuideState> = flow {
        val config = input.config
        val prefs = input.prefs
        val configured = config.epgUrl.isNotBlank()

        if (config.countries.isEmpty()) {
            emit(TvGuideState(guideConfigured = configured))
            return@flow
        }

        emit(TvGuideState(loading = true, guideConfigured = configured))

        val all = catalog.channels(config.countries)
        val channels = all.filterNot { it.id in prefs.hidden }
        val groups = automaticGroups(channels) + input.customGroups
        val base = TvGuideState(
            channels = channels,
            groups = groups,
            guideConfigured = configured,
            hiddenCount = all.size - channels.size,
            channelApps = prefs.apps,
        )
        emit(base.copy(loading = configured))

        if (!configured || channels.isEmpty()) return@flow

        val guide = fetchGuide(config.epgUrl, channels)
        emit(base.copy(programmes = guide.first, message = guide.second))
    }

    /**
     * Програма для каналів ТВ Ефіру зі свого плейлиста — з XMLTV провайдера (Налаштування →
     * Телебачення). Ключ — адреса каналу; зіставлення за `tvg-id`, далі за назвою (`programmesFor`).
     * Без адреси XMLTV чи без збігів — порожньо: канали лишаються «прямим ефіром».
     */
    suspend fun schedulesFor(links: List<ChannelLink>): Map<String, ChannelSchedule> = withContext(Dispatchers.IO) {
        val url = settingsRepository.settings.first().tvEpgUrl
        if (url.isBlank() || links.isEmpty()) return@withContext emptyMap()
        val key = { l: ChannelLink -> l.epgId.ifBlank { l.url } }
        val guide = runCatching {
            val parser = XmltvParser(
                keepChannelIds = links.map { it.epgId }.filter(String::isNotBlank).toSet(),
                keepChannelNames = links.map { normalizeChannelName(it.name) }.toSet(),
            )
            cachedGuide(url).inputStream().use { parser.parse(it) }
        }.getOrElse {
            Log.w(TAG, "Телепрограма плейлиста не завантажилася", it)
            return@withContext emptyMap()
        }
        val now = System.currentTimeMillis()
        val byKey = links.associateBy(key)
        guide.programmesFor(links.map { key(it) to it.name }).mapNotNull { (k, list) ->
            val link = byKey[k] ?: return@mapNotNull null
            val live = list.firstOrNull { it.startMillis <= now && it.endMillis > now }
            link.url to ChannelSchedule(
                live = live?.let { NowOnAir(title = it.title, description = it.description.orEmpty(), image = null, startMillis = it.startMillis, endMillis = it.endMillis) },
                upcoming = list.filter { it.startMillis > now }.take(UPCOMING).map { EpgProgramme(it.title, it.startMillis, it.endMillis, it.description.orEmpty()) },
            )
        }.toMap()
    }

    /** Завантаження XMLTV. Саме зіставлення — у `programmesFor`, і воно накрите тестом. */
    private suspend fun fetchGuide(
        url: String,
        channels: List<TvChannel>,
    ): Pair<Map<String, List<TvProgramme>>, String?> = withContext(Dispatchers.IO) {
        val guide = runCatching {
            // Фільтр — уже в парсері: тримаємо в пам'яті лише передачі наших каналів, а не
            // весь файл провайдера на десятки тисяч.
            val parser = XmltvParser(
                keepChannelIds = channels.map { it.id }.toSet(),
                keepChannelNames = channels.map { normalizeChannelName(it.name) }.toSet(),
            )
            cachedGuide(url).inputStream().use { parser.parse(it) }
        }.getOrElse { error ->
            Log.w(TAG, "Телепрограма не завантажилася", error)
            return@withContext emptyMap<String, List<TvProgramme>>() to
                context.getString(
                    R.string.tv_guide_failed,
                    error.message ?: context.getString(R.string.tv_no_connection),
                )
        }

        val programmes = guide.programmesFor(channels.map { it.id to it.name })

        val message = if (programmes.isEmpty()) {
            context.getString(R.string.tv_guide_no_match)
        } else {
            null
        }
        programmes to message
    }

    /**
     * Сирий XMLTV із кешу за (адреса, день), інакше — завантажений заново.
     *
     * Програма змінюється раз на добу, а приховати канал чи змінити групу — лише інший фільтр
     * над тим самим файлом: перекачувати через це десятки мегабайтів не треба. Файли інших
     * адрес і днів прибираємо, щоб кеш не ріс.
     */
    private fun cachedGuide(url: String): File {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val file = File(context.cacheDir, "$GUIDE_PREFIX$hash-$day.xml")
        if (file.exists() && file.length() > 0) return file

        http(url, TIMEOUT_MS, READ_TIMEOUT_MS, headers = mapOf("User-Agent" to USER_AGENT, "Accept-Encoding" to "gzip")) { connection ->
            // Через тимчасовий файл: обірване завантаження не має виглядати як кеш на цілий день.
            // gzip лишається gzip'ом — парсер сам дивиться на перші байти.
            val temporary = File.createTempFile(GUIDE_PREFIX, ".part", context.cacheDir)
            try {
                connection.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                context.cacheDir.listFiles { f -> f.name.startsWith(GUIDE_PREFIX) && f.name.endsWith(".xml") }
                    ?.forEach { it.delete() }
                if (!temporary.renameTo(file)) error(context.getString(R.string.tv_guide_cache_failed))
            } finally {
                temporary.delete()
            }
        }
        return file
    }

    /** Автогрупи будуються з категорій каталогу і показуються лише якщо в них є канали. */
    private fun automaticGroups(channels: List<TvChannel>): List<TvGroup> =
        TV_CATEGORY_TITLES.mapNotNull { (categoryId, titleRes) ->
            val count = channels.count { categoryId in it.categories }
            if (count == 0) null else TvGroup("auto-$categoryId", context.getString(titleRes), categoryId = categoryId)
        }

    private data class Config(val countries: Set<String>, val epgUrl: String)

    private data class Input(
        val config: Config,
        val customGroups: List<TvGroup>,
        val prefs: TvChannelPrefs,
    )

    private companion object {
        const val TAG = "LikhtarTv"
        const val USER_AGENT = "LIKHTAR-TV-launcher/0.1 (Android TV)"
        const val TIMEOUT_MS = 15_000
        const val GUIDE_PREFIX = "xmltv-"
        const val UPCOMING = 5

        /** XMLTV провайдера буває на кількадесят мегабайтів — читання довше за з'єднання. */
        const val READ_TIMEOUT_MS = 60_000
    }
}

/** Канали групи: для автогрупи — за категорією, для власної — за збереженим списком. */
fun TvGroup.channelsFrom(channels: List<TvChannel>): List<TvChannel> = when {
    categoryId != null -> channels.filter { categoryId in it.categories }
    else -> channels.filter { it.id in channelIds }
}
