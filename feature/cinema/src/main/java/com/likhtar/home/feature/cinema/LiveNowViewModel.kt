package com.likhtar.home.feature.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.EpgMatch
import com.likhtar.home.core.catalog.PlayRequest
import com.likhtar.home.core.catalog.EpgMatchRepository
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.RecentChannel
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.tv.ChannelLink
import com.likhtar.home.core.tv.ChannelSchedule
import com.likhtar.home.core.tv.NowOnAirAggregator
import com.likhtar.home.core.tv.TvGroupsRepository
import com.likhtar.home.core.tv.TvSource
import com.likhtar.home.core.tv.TvCatalogRepository
import com.likhtar.home.core.tv.TvCatalog
import com.likhtar.home.core.tv.CatalogChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Одна передача на картці: канал, час і — якщо знаємо — твір TMDB. */
data class LiveCard(
    val link: ChannelLink,
    val raw: String,
    val startMillis: Long,
    val endMillis: Long,
    val image: String?,
    val match: EpgMatch?,
    /** Опис із телепрограми — для слайда, коли твору ми не знаємо. */
    val description: String = "",
) {
    val key: String get() = "${link.url}@$startMillis"

    /** Ефір без годин: канал свого плейлиста без програми або прямий ефір YouTube. */
    val bare: Boolean get() = endMillis == 0L
    val work: CatalogItem? get() = match?.item

    /** Що малює картка: твір, якщо знаємо, інакше кадр і чиста назва з телепрограми. */
    val item: CatalogItem
        // Якість і HDR — про наші роздачі, а не про ефір: «4K · Dolby» на каналі, що йде
        // в 720p, — неправда.
        get() = work?.let { it.copy(backdropPath = it.backdropPath ?: image, quality = 0, hdr = "") }
            ?: CatalogItem(
                tmdbId = 0,
                kind = TitleKind.TV,
                title = programmeName(link, match?.name?.takeIf(String::isNotBlank) ?: raw.trim()),
                year = "",
                backdropPath = image,
                overview = description,
            )
}

/** Останній результат ТВ Ефіру на час життя процесу: розділ відкривається одразу з нього. */
private object LiveNowMemory {
    @Volatile var state: LiveNowState? = null
    @Volatile var at = 0L
}

data class LiveNowState(
    val loading: Boolean = true,
    /** «Мої канали» — обрані, далі нещодавно відкриті; єдиний ряд сцени 1. */
    val mine: List<LiveCard> = emptyList(),
    /** Обрані канали (адреси) — ті самі, що закріплені для Головної. */
    val pinned: Set<String> = emptySet(),
    val message: String? = null,
    /**
     * Жодного джерела каналів: ні застосунку провайдера, ні свого плейлиста. Не збій —
     * екран пропонує плейлист і [free] (встановлені — «відкрити», ні — «встановити»).
     */
    val noSources: Boolean = false,
    val free: List<FreeSource> = emptyList(),
    /** Канал плейлиста, а відеоплеєра на пристрої немає — пропонуємо встановити. */
    val needPlayer: Boolean = false,
    /** Сцена 2 (список і прев'ю): що йде зараз на кожному каналі — по одній картці на канал. */
    val channels: List<LiveCard> = emptyList(),
    /** Групи сцени 2 у порядку сервера; «Інші канали» — наприкінці. */
    val groups: List<String> = emptyList(),
    /** Розклад кожного каналу (зараз і далі) — смуга вечора під банером (прототип). */
    val schedules: Map<String, ChannelSchedule> = emptyMap(),
)

/**
 * «ТВ Ефір 2»: ефір у форматі Кінозалу — не «що на кожному каналі», а «що варто ввімкнути».
 *
 * Джерела ті самі, що в сітці ТВ Ефіру (канали, MEGOGO + Sweet.TV); нове лише зіставлення
 * назв передач із TMDB на сервері (`/api/epg-match`). Стара сітка лишається окремою вкладкою,
 * доки ця не доведе, що вистачає.
 */
@HiltViewModel
class LiveNowViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val tvSource: TvSource,
    private val nowOnAir: NowOnAirAggregator,
    private val catalog: TvCatalogRepository,
    private val matcher: EpgMatchRepository,
    private val groups: TvGroupsRepository,
    private val recent: RecentHistoryRepository,
    private val apps: AppsRepository,
    private val settings: com.likhtar.home.core.data.SettingsRepository,
    private val playback: com.likhtar.home.core.catalog.PlaybackController,
    private val guide: com.likhtar.home.core.tv.TvGuideRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveNowState())
    private var groupOrder: List<String> = emptyList()
    private var lead: List<String> = emptyList()
    val state: StateFlow<LiveNowState> = _state

    /** Коли востаннє оновлено до кінця; 0 — ще ні разу. */
    private var refreshedAt = 0L

    init {
        // Повернення в розділ — одразу з останнього результату: екран щоразу збирав усе
        // наново й 1–3 с стояв на ліхтарику. Свіже — без мережі, старе — оновить цикл.
        LiveNowMemory.state?.let { saved ->
            _state.value = saved
            refreshedAt = LiveNowMemory.at
        }
    }

    init {
        // Оновлюємо лише поки екран дивиться: `collectAsStateWithLifecycle` відписується на
        // onStop, і цикл стає разом з ним — у фоні ні мережі, ні Sweet.TV-віяла. Повернення
        // не смикає мережу, якщо з останнього оновлення не минуло 5 хв.
        viewModelScope.launch {
            _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
                if (!visible) return@collectLatest
                delay(refreshedAt + REFRESH_MS - System.currentTimeMillis())
                while (true) {
                    refresh()
                    refreshedAt = System.currentTimeMillis()
                    delay(REFRESH_MS)
                }
            }
        }
    }

    /** «Спробувати ще» з панелі, коли телепрограма не приїхала: не чекати п'ять хвилин циклу. */
    fun retry() {
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            refresh()
            refreshedAt = System.currentTimeMillis()
        }
    }

    private suspend fun refresh() {
        // Час кожного кроку — у журнал: «ТВ Ефір довго відкривається» без нього не розібрати.
        var mark = System.currentTimeMillis()
        val took = { step: String -> val now = System.currentTimeMillis(); android.util.Log.i("LikhtarLive", "$step: ${now - mark} мс"); mark = now }
        val hidden = runCatching { groups.hiddenChannels.first() }.getOrDefault(emptySet())
        // Набори шоу (посилання на /video/) телепрограми не мають — їм тут нема що показати.
        // Свій плейлист (M3U від провайдера) — тоді лише він: каталоги застосунків не додаємо.
        // Публічна збірка — без нашого сервера: лише плейлист і XMLTV людини, як у режимі плейлиста.
        val playlist = com.likhtar.home.core.model.Distribution.store ||
            runCatching { settings.settings.first().tvPlaylistUrl.isNotBlank() }.getOrDefault(false)
        val ownAll = runCatching { tvSource.channels() }.getOrDefault(emptyList())
            .filter { it.url !in hidden && !it.url.contains("/video/") }
        took("свої канали")
        val fetched = if (playlist) null else runCatching { catalog.catalog() }.getOrNull()
        val tvAll = fetched ?: TvCatalog(emptyList(), emptyList())
        took("каталог каналів")
        // Канали лише тих застосунків, що стоять у людини (розділ будується з
        // того, що знаємо про неї, а не з таблиць під країну). Потік без пакета — грає плеєр.
        val packages = (ownAll.map { it.packageName } + tvAll.channels.map { it.link.packageName }).filter(String::isNotBlank).distinct()
        val installed = packages.filter(apps::isInstalled).toSet()
        val usable = { p: String -> p.isBlank() || p in installed }
        val own = ownAll.filter { usable(it.packageName) }
        val tv = tvAll.copy(channels = tvAll.channels.filter { usable(it.link.packageName) })
        val (links, extra) = mergeChannels(own, tv)
        // Свій плейлист — програма з XMLTV провайдера; інакше — MEGOGO / Sweet.TV.
        val schedules = runCatching { if (playlist) guide.schedulesFor(own) else nowOnAir.byUrl(own) }.getOrDefault(emptyMap()) +
            extra.filter { it.link.url !in hidden }.mapNotNull { c -> c.schedule?.let { c.link.url to it } }
        took("телепрограма (${own.size} каналів)")
        groupOrder = tv.groups
        lead = tv.lead
        // На зіставлення — лише те, що йде зараз: уся програма трьохсот каналів — це три
        // тисячі назв на кожне оновлення.
        // Ефіри без кінця (YouTube) не зіставляємо: новина з назвою серіалу дістала б чужий постер.
        val titles = schedules.values.mapNotNull { s -> s.live?.takeIf { it.endMillis != 0L }?.title }.distinct() // «Новини» на сотні каналів — одна назва: було 962 на кожне оновлення
        val mine = runCatching { recent.channels.first() }.getOrDefault(emptyList()).map { it.url }
        val matches = runCatching { matcher.match(titles) }.getOrDefault(emptyMap())
        took("зіставлення (${titles.size} назв)")
        val now = System.currentTimeMillis()
        val live = liveCards(links, schedules, matches, now)
        recentUrls = mine
        val pinned = runCatching { settings.settings.first().pinnedChannels }.getOrDefault(emptySet())
        val mineCards = mineCards(live, pinned, mine)
        val guide = guideChannels(live, groupOrder, lead)
        val old = _state.value
        _state.value = old.copy(
            loading = false,
            mine = mineCards,
            pinned = pinned,
            schedules = schedules,
            message = when {
                guide.isNotEmpty() -> null
                // Каталог не приїхав, а своїх каналів немає — це збій мережі, не «нема джерел».
                fetched == null && !playlist && own.isEmpty() -> context.getString(R.string.cinema_live_no_epg)
                else -> null
            },
            noSources = guide.isEmpty() && (fetched != null || playlist),
            free = tvAll.free.map { FreeSource(it.name, it.packageName, apps.isInstalled(it.packageName)) },
            channels = guide,
            groups = guide.map { liveGroup(it) }.distinct(),
        )
        if (guide.isNotEmpty()) {
            LiveNowMemory.state = _state.value
            LiveNowMemory.at = System.currentTimeMillis()
        }
    }

    private var recentUrls: List<String> = emptyList()

    /** Довге OK → «В обрані» / «Прибрати з обраних»: ряд перебудовується одразу, без мережі. */
    fun toggleFavourite(card: LiveCard) {
        viewModelScope.launch {
            settings.toggleFav(card.link.url)
            val pinned = runCatching { settings.settings.first().pinnedChannels }.getOrDefault(emptySet())
            _state.value = _state.value.let { it.copy(pinned = pinned, mine = mineCards(it.channels, pinned, recentUrls)) }
        }
    }

    /** Довге OK → «Приховати»: канал зникає з обох сцен; повертає «Налаштування → Телебачення». */
    fun hide(card: LiveCard) {
        viewModelScope.launch {
            groups.hideChannel(card.link.url)
            _state.value = _state.value.let { s ->
                val channels = s.channels.filter { it.link.url != card.link.url }
                s.copy(mine = s.mine.filter { it.link.url != card.link.url }, channels = channels, groups = channels.map { liveGroup(it) }.distinct())
            }
            LiveNowMemory.state = null
        }
    }

    fun open(card: LiveCard) {
        val link = card.link
        viewModelScope.launch {
            // Потік зі свого плейлиста — у вибраний плеєр (Налаштування → Перегляд), той самий
            // шлях, що й Кінозал; канал застосунку — посиланням у сам застосунок.
            val opened = if (link.packageName.isBlank()) {
                // Плеєра немає зовсім (на Google TV з коробки його зазвичай нема) — не мовчимо.
                if (apps.videoPlayers().isEmpty()) {
                    _state.value = _state.value.copy(needPlayer = true)
                    return@launch
                }
                // Вибраний у Налаштуваннях, якщо він є на пристрої; інакше — вибір системи.
                val chosen = settings.settings.first().videoPlayerPackage.takeIf(apps::isInstalled).orEmpty()
                playback.play(
                    PlayRequest(
                        uri = link.url,
                        mime = "video/*",
                        packageName = chosen,
                        resumeMs = 0,
                        title = null,
                        displayName = channelLabel(link.name),
                    ),
                )
            } else {
                apps.openLink(link.url, link.packageName)
            }
            // У «Мої канали» — лише те, що справді відкрилось.
            if (opened) {
                recent.rememberChannel(RecentChannel(url = link.url, name = link.name, image = link.logo, packageName = link.packageName))
            } else {
                _state.value = _state.value.copy(message = context.getString(R.string.cinema_live_no_app, link.name))
            }
        }
    }

    fun dismissNeedPlayer() {
        _state.value = _state.value.copy(needPlayer = false)
    }

    /** Безкоштовне джерело з порожнього стану: встановлене — відкрити, ні — сторінка в Google Play. */
    fun openFree(source: FreeSource) {
        if (source.installed) {
            apps.launchPackage(source.packageName)
        } else {
            apps.openLink("market://details?id=${source.packageName}", "com.android.vending")
        }
    }

    private companion object {
        const val REFRESH_MS = 5 * 60 * 1000L
    }
}

/** «Мої канали»: спершу обрані (у порядку каналів), далі нещодавні, разом не більше [RECENT_LIMIT] нещодавніх. */
internal fun mineCards(live: List<LiveCard>, pinned: Set<String>, recentUrls: List<String>, day: Long = System.currentTimeMillis() / 86_400_000L): List<LiveCard> {
    val byUrl = live.associateBy { it.link.url }
    val fav = live.filter { it.link.url in pinned }.distinctBy { it.link.url }
    val mine = fav + recentUrls.filter { it !in pinned }.mapNotNull(byUrl::get).take(RECENT_LIMIT)
    // Перший старт — не нулі: 10 випадкових каналів, однакових упродовж дня (не стрибають
    // з кожним оновленням), доки людина не відкриє щось сама.
    return mine.ifEmpty { live.distinctBy { it.link.url }.shuffled(kotlin.random.Random(day)).take(RECENT_LIMIT) }
}

/** Безкоштовний ТВ-застосунок для порожнього стану. */
data class FreeSource(val name: String, val packageName: String, val installed: Boolean)

/** Що йде зараз на кожному каналі. */
internal fun liveCards(
    links: List<ChannelLink>,
    schedules: Map<String, ChannelSchedule>,
    matches: Map<String, EpgMatch>,
    now: Long,
): List<LiveCard> = links.mapNotNull { link ->
    // Потік свого плейлиста (без пакета) лишається й без програми — це канал людини.
    val on = schedules[link.url]?.live
        ?: return@mapNotNull if (link.packageName.isBlank()) LiveCard(link, "", 0, 0, null, null) else null
    if (on.endMillis != 0L && on.endMillis <= now) return@mapNotNull null
    LiveCard(link, on.title, on.startMillis, on.endMillis, on.image, matches[on.title], on.description)
}

/** Канали без групи в каталогі. */
internal const val LiveRowOther = "Інші канали"

private const val UaGroup = "Українські"

/** Група каналу для сцени 2 — та сама, що дає тематичний ряд. */
internal fun liveGroup(card: LiveCard): String = card.link.category.ifBlank { LiveRowOther }

/**
 * Канали сцени 2: групи в порядку сервера (невідомі й «Інші» — наприкінці), усередині
 * українських — головні канали першими, далі як ідуть. Один канал — одна картка.
 */
internal fun guideChannels(live: List<LiveCard>, order: List<String>, lead: List<String>): List<LiveCard> {
    val rank = { g: String -> if (g == LiveRowOther) Int.MAX_VALUE else order.indexOf(g).takeIf { it >= 0 } ?: (Int.MAX_VALUE - 1) }
    return live.distinctBy { it.link.url }
        .sortedWith(compareBy<LiveCard>({ rank(liveGroup(it)) }, { if (liveGroup(it) == UaGroup) uaChannelRank(it.link.name, lead) else 0 }))
}

/** Головні канали ([lead] від сервера) — першими й саме так; решта — після них (сортування стабільне). */
internal fun uaChannelRank(name: String, lead: List<String>): Int {
    val n = name.lowercase().replace(" ", "")
    return lead.indexOfFirst { n.startsWith(it) }
        .takeIf { it >= 0 } ?: Int.MAX_VALUE
}

/**
 * Власні канали + каталог Sweet.TV. Свої йдуть першими (це підписки, і в них своя програма
 * з MEGOGO), але жанр беруть із каталогу — за назвою каналу, — щоб «Discovery» з MEGOGO
 * стояв у тому ж ряду, що й «Viasat Nature» зі Sweet.TV. Канал каталогу, який уже є серед
 * своїх, не дублюємо.
 */
internal fun mergeChannels(own: List<ChannelLink>, tv: TvCatalog): Pair<List<ChannelLink>, List<CatalogChannel>> {
    val byName = tv.channels.associateBy { channelKey(it.link.name) }
    val mine = own.map { link ->
        val c = byName[channelKey(link.name)]?.link
        link.copy(category = c?.category ?: ownCategory(link), mark = link.mark.ifBlank { c?.mark.orEmpty() })
    }
    val ownUrls = own.map { it.url }.toSet()
    // Свій канал заступає однойменний із каталогу, але лише якщо сам не гірший: свій
    // «Nat Geo Wild» без HD поступається «Nat Geo Wild HD» зі Sweet.TV (з двох версій каналу лише HD).
    val ownHD = own.groupBy { channelKey(it.name) }.mapValues { (_, l) -> l.any { isHD(it.name) } }
    val extra = tv.channels.filter { c ->
        val key = channelKey(c.link.name)
        c.link.url !in ownUrls && (key !in ownHD || (ownHD[key] == false && isHD(c.link.name)))
    }
    val all = mine + extra.map { it.link }
    val hdKeys = all.filter { isHD(it.name) }.map { channelKey(it.name) }.toSet()
    val kept = all.filter { isHD(it.name) || channelKey(it.name) !in hdKeys }.distinctBy { channelKey(it.name) }
    val keptUrls = kept.map { it.url }.toSet()
    return kept to extra.filter { it.link.url in keptUrls }
}

private fun isHD(name: String) = HDSuffix.containsMatchIn(name)

private val HDSuffix = Regex("""\s(HD|FHD|UHD|4K)$""", RegexOption.IGNORE_CASE)

/** Ключ для зіставлення каналів за назвою: без «HD», регістру й розділових знаків. */
internal fun channelKey(name: String): String =
    channelLabel(name).lowercase().filter { it.isLetterOrDigit() }

/** Жанр свого каналу, якого немає в каталозі: наші рядки з файлу → жанри каталогу. */
private fun ownCategory(link: ChannelLink): String = when (link.category) {
    "Українське ТБ" -> "Українські"
    "Кулінарія" -> "Подорожі й кухня"
    "Пізнавальні та тревел" ->
        if (TravelName.containsMatchIn(link.name)) "Подорожі й кухня" else "Природа, наука й історія"
    else -> link.category
}

private val TravelName = Regex("travel|орел|utravel|мандри|подорож|пригоди", RegexOption.IGNORE_CASE)

/**
 * Назва передачі для картки. У Sweet.TV частина передач зветься самим номером — «Сезон 7,
 * Випуск 3» на М.Шеф, — і картка казала лише «Сезон 7». Тоді спереду стає канал.
 */
internal fun programmeName(link: ChannelLink, name: String): String =
    if (NumberOnlyName.containsMatchIn(name)) "${channelLabel(link.name)} · $name" else name

private val NumberOnlyName = Regex("""^(сезон|випуск|серія|епізод|частина)\b""", RegexOption.IGNORE_CASE)

/** Назва каналу без «HD»: на картці поруч із часом вона лише займає місце. */
internal fun channelLabel(name: String): String =
    name.replace(Regex("""\s+(HD|FHD|UHD|4K)$""", RegexOption.IGNORE_CASE), "").trim()

/** «лишилось 40 хв» / «лишилось 1 год 5 хв». */
internal fun leftLabel(res: android.content.res.Resources, endMillis: Long, now: Long): String {
    val minutes = ((endMillis - now) / 60_000L).toInt()
    if (minutes <= 0) return res.getString(R.string.cinema_live_ending)
    val hours = minutes / 60
    return when {
        hours == 0 -> res.getString(R.string.cinema_live_left_minutes, minutes)
        minutes % 60 == 0 -> res.getString(R.string.cinema_live_left_hours, hours)
        else -> res.getString(R.string.cinema_live_left_hours_minutes, hours, minutes % 60)
    }
}

private const val RECENT_LIMIT = 10
