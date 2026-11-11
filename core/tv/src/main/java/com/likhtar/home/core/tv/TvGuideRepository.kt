package com.likhtar.home.core.tv

import com.likhtar.home.core.data.http
import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
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
    private val playlist: PlaylistTvSource,
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
        // Своя адреса з налаштувань — першою; без неї — та, що провайдер поклав у плейлист.
        val url = settingsRepository.settings.first().tvEpgUrl.ifBlank { playlist.guideUrl() }
        if (url.isBlank() || links.isEmpty()) return@withContext emptyMap()
        val key = { l: ChannelLink -> l.epgId.ifBlank { l.url } }
        // Розібране тримаємо, доки файл той самий (він оновлюється раз на 12 годин): XMLTV
        // провайдера розбирається на приставці хвилину, а ТВ Ефір оновлюється кожні 5 хв.
        // Ключ — які саме канали лишили, а не лише скільки: парсер бере тільки їхні id, і заміна
        // прихованого каналу іншим за тієї ж кількості лишала новий без програми на пів години.
        val file = runCatching { cachedGuide(url) }.getOrElse {
            Log.w(TAG, "Телепрограма плейлиста не завантажилася", it)
            return@withContext emptyMap()
        }
        val memoKey = file.name + "|" + links.map { key(it) + "\u0000" + it.name }.sorted().hashCode()
        val memo = parsedGuide?.takeIf { it.first == memoKey }?.third
        val guide = memo
            ?: runCatching {
                val archived = links.filter { it.archive != null }
                val parser = XmltvParser(
                    keepChannelIds = links.map { it.epgId }.filter(String::isNotBlank).toSet(),
                    keepChannelNames = links.map { normalizeChannelName(it.name) }.toSet(),
                    archiveIds = archived.map { it.epgId }.filter(String::isNotBlank).toSet(),
                    archiveMillis = (archived.maxOfOrNull { it.archive!!.days } ?: 0) * DAY_MS,
                )
                file.inputStream().use { parser.parse(it) }
            }.getOrElse {
                Log.w(TAG, "Телепрограма плейлиста не завантажилася", it)
                return@withContext emptyMap()
            }.also { parsedGuide = Triple(memoKey, System.currentTimeMillis(), it) }
        val now = System.currentTimeMillis()
        // Кілька каналів з одним tvg-id (HD, SD, резерв) — одна програма на всіх. associateBy
        // лишав її лише останньому, решта стояли «Прямим ефіром».
        val byKey = links.groupBy(key)
        val matched = guide.programmesFor(links.map { key(it) to it.name }.distinctBy { it.first })
        matched.flatMap { (k, list) -> byKey[k].orEmpty().map { it to list } }.also {
            Log.i(TAG, "Програма плейлиста: ${it.size} з ${links.size} каналів")
        }.associate { (link, list) ->
            val live = list.firstOrNull { it.startMillis <= now && it.endMillis > now }
            link.url to ChannelSchedule(
                live = live?.let { NowOnAir(title = it.title, description = it.description.orEmpty(), image = null, startMillis = it.startMillis, endMillis = it.endMillis) },
                upcoming = list.filter { it.startMillis > now }.take(UPCOMING).map { EpgProgramme(it.title, it.startMillis, it.endMillis, it.description.orEmpty()) },
                past = link.archive?.let { a ->
                    val from = now - a.days * DAY_MS
                    list.filter { it.endMillis <= now && it.startMillis >= from }.asReversed()
                        .map { EpgProgramme(it.title, it.startMillis, it.endMillis, it.description.orEmpty()) }
                }.orEmpty(),
            )
        }.also { result ->
            // Щойно розібране — на диск, для наступного холодного старту (див. [lastSchedules]).
            if (memo == null && result.isNotEmpty()) runCatching { snapshotFile.writeText(encodeGuideSnapshot(url, result)) }
        }
    }

    /**
     * Програма з останнього розбору — миттєво, без XMLTV: показати, доки йде свіжий розбір.
     * Порожньо, якщо розбору ще не було або адреса програми відтоді змінилась.
     */
    suspend fun lastSchedules(links: List<ChannelLink>): Map<String, ChannelSchedule> = withContext(Dispatchers.IO) {
        val url = settingsRepository.settings.first().tvEpgUrl.ifBlank { playlist.guideUrl() }
        if (url.isBlank() || !snapshotFile.exists()) return@withContext emptyMap()
        val urls = links.map { it.url }.toSet()
        runCatching { decodeGuideSnapshot(snapshotFile.readText(), url, System.currentTimeMillis()) }
            .getOrDefault(emptyMap()).filterKeys { it in urls }
    }

    private val snapshotFile get() = File(context.cacheDir, "tv-guide-last.json")

    @Volatile private var parsedGuide: Triple<String, Long, XmltvGuide>? = null

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
        // Свіжий файл — раз на 12 годин; увесь цей час працюємо з тим самим.
        val day = System.currentTimeMillis() / GUIDE_REFRESH_MS
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        // «-2»: файли, складені до NO_HTTP_CACHE, могли прийти зі старого HTTP-кешу — перекачати.
        val file = File(context.cacheDir, "$GUIDE_PREFIX$hash-$day-2.xml")
        if (file.exists() && file.length() > 0) return file
        // Попередній файл цієї ж адреси: новий не скачається — програма лишається вчорашньою, а не жодною.
        val previous = context.cacheDir.listFiles { f -> f.name.startsWith("$GUIDE_PREFIX$hash-") && f.name.endsWith(".xml") && f.length() > 0 }
            ?.maxByOrNull { it.lastModified() }

        runCatching { http(url, TIMEOUT_MS, READ_TIMEOUT_MS, headers = mapOf("User-Agent" to USER_AGENT, "Accept-Encoding" to "gzip", "Cache-Control" to NO_HTTP_CACHE)) { connection ->
            // Через тимчасовий файл: обірване завантаження не має виглядати як кеш на цілий день.
            // gzip лишається gzip'ом — парсер сам дивиться на перші байти.
            val temporary = File.createTempFile(GUIDE_PREFIX, ".part", context.cacheDir)
            try {
                if (connection.responseCode != 200) error("XMLTV: HTTP ${connection.responseCode}")
                connection.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                // Сторінка-заглушка провайдера (вхід, капча, «404» з кодом 200) — не програма: інакше
                // вона лягала б у кеш на цілий день, і програми не було до завтра.
                if (!looksLikeXmltv(temporary)) error("XMLTV: відповідь — не програма")
                context.cacheDir.listFiles { f -> f.name.startsWith(GUIDE_PREFIX) && f.name.endsWith(".xml") }
                    ?.forEach { it.delete() }
                if (!temporary.renameTo(file)) error(context.getString(R.string.tv_guide_cache_failed))
            } finally {
                temporary.delete()
            }
        } }.onFailure { if (previous == null) throw it else Log.w(TAG, "Свіжа телепрограма не завантажилась — лишаємо попередню (${it.javaClass.simpleName})") }
        return if (file.exists()) file else previous!!
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
        const val GUIDE_REFRESH_MS = 12 * 60 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val TAG = "LikhtarTv"
        const val USER_AGENT = "LIKHTAR-TV-launcher/0.1 (Android TV)"
        const val TIMEOUT_MS = 15_000
        const val GUIDE_PREFIX = "xmltv-"
        const val UPCOMING = 12

        /** XMLTV провайдера буває на кількадесят мегабайтів — читання довше за з'єднання. */
        const val READ_TIMEOUT_MS = 60_000
    }
}

/** Канали групи: для автогрупи — за категорією, для власної — за збереженим списком. */
fun TvGroup.channelsFrom(channels: List<TvChannel>): List<TvChannel> = when {
    categoryId != null -> channels.filter { categoryId in it.categories }
    else -> channels.filter { it.id in channelIds }
}

/** gzip або XML з <tv> на початку; HTML-сторінка — ні. */
internal fun looksLikeXmltv(file: File): Boolean {
    val head = file.inputStream().use { input -> ByteArray(512).let { b -> b.copyOf(maxOf(0, input.read(b))) } }
    if (head.size >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()) return true
    val text = String(head, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
    return text.startsWith("<?xml") && "<html" !in text || text.startsWith("<tv") || text.startsWith("<!doctype tv")
}
