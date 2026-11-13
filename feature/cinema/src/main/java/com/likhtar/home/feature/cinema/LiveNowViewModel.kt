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
import com.likhtar.home.core.tv.rolledTo
import com.likhtar.home.core.tv.ChannelLink
import com.likhtar.home.core.tv.ChannelSchedule
import com.likhtar.home.core.tv.NowOnAirAggregator
import com.likhtar.home.core.tv.NowOnAir
import com.likhtar.home.core.tv.TvGroupsRepository
import com.likhtar.home.core.tv.TvSource
import com.likhtar.home.core.tv.TvCatalogRepository
import com.likhtar.home.core.tv.TvCatalog
import com.likhtar.home.core.tv.CatalogChannel
import com.likhtar.home.core.tv.ChannelEdits
import com.likhtar.home.core.tv.channelLabel
import com.likhtar.home.core.tv.mergeChannels
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Адреса плейлиста, з якої зібрано [state]: інша в налаштуваннях — збережене застаріло. */
    @Volatile var playlist = ""
    /** Правки каналів, з якими зібрано [state]: змінились з телефона — збирати наново. */
    @Volatile var edits: ChannelEdits? = null
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
    /** Запасні адреси свого каналу (той самий канал з іншого джерела — плейлист, портал): за адресою. */
    val backups: Map<String, List<String>> = emptyMap(),
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
    private val playlistSource: com.likhtar.home.core.tv.PlaylistTvSource,
    private val editor: com.likhtar.home.core.tv.ChannelsPhoneLink,
    private val systemTv: com.likhtar.home.core.tv.SystemTvSource,
    private val appUpdate: com.likhtar.home.core.data.AppUpdate,
    private val reminders: com.likhtar.home.core.data.ProgrammeReminders,
    private val portal: com.likhtar.home.core.tv.StalkerPortal,
) : ViewModel() {
    /** Обліковка порталу для плеєра — береться перед запуском каналу (див. [open]). */
    @Volatile private var portalAccount: String = ""
    @Volatile private var backups: Map<String, List<String>> = emptyMap()

    /** Передачі з нагадуванням — ключі `адреса@початок`. */
    val reminderKeys: kotlinx.coroutines.flow.StateFlow<Set<String>> = reminders.reminders
        .map { list -> list.mapTo(HashSet()) { it.key } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleReminder(card: LiveCard, programme: com.likhtar.home.core.tv.EpgProgramme) {
        viewModelScope.launch {
            reminders.toggle(com.likhtar.home.core.data.ProgrammeReminder(card.link.url, channelLabel(card.link.name), programme.title, programme.startMillis))
        }
    }

    /** Редактор каналів з телефона: адреса для QR (`null` — немає домашньої мережі). */
    fun startEditor(): String? = editor.start()
    val editorStatus = editor.status

    private val _state = MutableStateFlow(LiveNowState())
    private var groupOrder: List<String> = emptyList()
    private var lead: List<String> = emptyList()
    private var userOrder: List<String> = emptyList()
    private var groupEdits = ChannelEdits()
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
        // «Мої канали» — одразу після повернення з плеєра, а не з наступним оновленням за 5 хв:
        // канал, на який перемкнулись у плеєрі, записується вже після виходу з нього.
        viewModelScope.launch {
            recent.channels.map { list -> list.map { it.url } }.distinctUntilChanged().collect { urls ->
                recentUrls = urls
                _state.value = _state.value.let { if (it.channels.isEmpty()) it else it.copy(mine = mineCards(it.channels, it.pinned, urls)) }
            }
        }
    }

    init {
        // Оновлюємо лише поки екран дивиться: `collectAsStateWithLifecycle` відписується на
        // onStop, і цикл стає разом з ним — у фоні ні мережі, ні Sweet.TV-віяла. Повернення
        // не смикає мережу, якщо з останнього оновлення не минуло 5 хв.
        viewModelScope.launch {
            _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
                if (!visible) return@collectLatest
                val playlistUrl = settings.settings.map { it.tvPlaylistUrl }.distinctUntilChanged()
                // Новий плейлист у налаштуваннях — одразу, а не через 5 хв циклу: інакше ще
                // п'ять хвилин гралися канали старого (MPEGTS → HLS).
                if (playlistUrl.first() == LiveNowMemory.playlist && groups.channelEdits.first() == LiveNowMemory.edits) {
                    delay(refreshedAt + REFRESH_MS - System.currentTimeMillis())
                }
                while (true) {
                    refresh()
                    refreshedAt = System.currentTimeMillis()
                    // Правки з телефона — теж одразу: людина дивиться на ТВ, поки тисне «Зберегти».
                    kotlinx.coroutines.withTimeoutOrNull(REFRESH_MS) {
                        kotlinx.coroutines.flow.merge(playlistUrl.drop(1), groups.channelEdits.distinctUntilChanged().drop(1)).first()
                    }
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

    /**
     * Розбір XMLTV плейлиста — один за раз. Наступне оновлення скасовує попередній: інакше
     * старий розбір (хвилина й більше) дописував програму до каналів старого плейлиста вже
     * після нового, а кеш розділу записувався з адресою нового.
     */
    // lazy: init вище запускає оновлення на Main.immediate раніше, ніж дійде до цього рядка.
    private val guideJob by lazy { com.likhtar.home.core.data.LatestJob(viewModelScope) }

    private suspend fun refresh() {
        guideJob.cancel()
        // Адреса плейлиста, з якого будуються саме ці канали, — для кешу розділу.
        val playlistAt = runCatching { settings.settings.first().tvPlaylistUrl }.getOrDefault("")
        // Час кожного кроку — у журнал: «ТВ Ефір довго відкривається» без нього не розібрати.
        var mark = System.currentTimeMillis()
        val took = { step: String -> val now = System.currentTimeMillis(); android.util.Log.i("LikhtarLive", "$step: ${now - mark} мс"); mark = now }
        val hidden = runCatching { groups.hiddenChannels.first() }.getOrDefault(emptySet())
        // Набори шоу (посилання на /video/) телепрограми не мають — їм тут нема що показати.
        // Плейлист провайдера — НА ДОДАЧУ до каналів застосунків, а не замість (        // розділ показує, що де є). Публічна збірка — без нашого сервера: лише плейлист і XMLTV.
        val store = com.likhtar.home.core.model.Distribution.store
        val playlistUrls = runCatching { playlistSource.channels() }.getOrDefault(emptyList()).map { it.url }.toSet()
        val playlist = store || playlistUrls.isNotEmpty()
        val ownAll = runCatching { tvSource.channels() }.getOrDefault(emptyList())
            .filter { it.url !in hidden && !it.url.contains("/video/") }
        took("свої канали")
        val fetched = if (store) null else runCatching { catalog.catalog() }.getOrNull()
        val tvAll = fetched ?: TvCatalog(emptyList(), emptyList())
        took("каталог каналів")
        // Канали лише тих застосунків, що стоять у людини (розділ будується з
        // того, що знаємо про неї, а не з таблиць під країну). Потік без пакета — грає плеєр.
        val packages = (ownAll.map { it.packageName } + tvAll.channels.map { it.link.packageName }).filter(String::isNotBlank).distinct()
        val installed = packages.filter(apps::isInstalled).toSet()
        val usable = { p: String -> p.isBlank() || p in installed }
        val own = ownAll.filter { usable(it.packageName) }
        val tv = tvAll.copy(channels = tvAll.channels.filter { usable(it.link.packageName) })
        val edits = runCatching { groups.channelEdits.first() }.getOrDefault(ChannelEdits())
        userOrder = edits.order
        val (merged, extra) = mergeChannels(own, tv, edits)
        val links = groupEdited(merged, edits)
        groupEdits = edits
        backups = com.likhtar.home.core.tv.channelBackups(own, tv, edits)
        android.util.Log.i("LikhtarLive", "свої ${ownAll.size} (доступні ${own.size}), каталог ${tvAll.channels.size} (доступні ${tv.channels.size}), сховані ${hidden.size}, правки: сховано ${edits.hidden.size}, склеєно ${edits.joins.size}")
        groupOrder = tv.groups
        lead = tv.lead
        val extraSchedules = extra.filter { it.link.url !in hidden }.mapNotNull { c -> c.schedule?.let { c.link.url to it } }.toMap()
        // Канали застосунків (MEGOGO / Sweet.TV) — швидко, тож одразу з програмою.
        // Канали порталу Stalker — з тією ж програмою, що й плейлист (XMLTV, далі — сам портал).
        val (fromPlaylist, fromApps) = own.partition { it.url in playlistUrls || com.likhtar.home.core.tv.portalId(it.url) != null }
        // Канали, які застосунки зареєстрували в Android TV, — з їхньою ж програмою, в обох збірках.
        val fast = (if (store) emptyMap() else runCatching { nowOnAir.byUrl(fromApps) }.getOrDefault(emptyMap())) + extraSchedules +
            systemTv.schedules(fromApps)
        took("телепрограма застосунків (${fromApps.size} каналів)")
        // Доки XMLTV розбирається, у каналів плейлиста лишається попередня програма: без неї
        // кожне оновлення (і повернення з плеєра) на хвилину стирало розклад.
        val previous = _state.value.schedules
        // Передача могла змінитись, доки стара програма лежала, — пересуваємо її на «зараз».
        // Холодний старт (попередньої немає) — з останнього розбору на диску.
        val kept = System.currentTimeMillis().let { at -> fromPlaylist.mapNotNull { l -> previous[l.url]?.rolledTo(at)?.let { l.url to it } }.toMap() }
            .ifEmpty { runCatching { guide.lastSchedules(fromPlaylist) }.getOrDefault(emptyMap()) }
        publish(links, kept + fast, fetched, playlist, own.isEmpty(), tvAll, playlistAt)
        LiveNowMemory.edits = edits
        if (fromPlaylist.isNotEmpty()) {
            // XMLTV провайдера — десятки мегабайтів, розбір на приставці хвилину й більше. Канали
            // показуємо одразу, а програму дописуємо, щойно розбереться: доти екран стояв на
            // ліхтарику півтори хвилини.
            guideJob.launch {
                val schedules = runCatching { guide.schedulesFor(fromPlaylist) }.getOrDefault(emptyMap())
                ensureActive()
                took("телепрограма плейлиста (${fromPlaylist.size} каналів)")
                if (schedules.isNotEmpty()) publish(links, fast + schedules, fetched, playlist, own.isEmpty(), tvAll, playlistAt)
            }
        }
    }

    private suspend fun publish(
        links: List<com.likhtar.home.core.tv.ChannelLink>,
        schedules: Map<String, ChannelSchedule>,
        fetched: TvCatalog?,
        playlist: Boolean,
        noOwn: Boolean,
        tvAll: TvCatalog,
        playlistAt: String,
    ) {
        // На зіставлення — лише те, що йде зараз: уся програма трьохсот каналів — це три
        // тисячі назв на кожне оновлення.
        // Ефіри без кінця (YouTube) не зіставляємо: новина з назвою серіалу дістала б чужий постер.
        val titles = schedules.values.mapNotNull { s -> s.live?.takeIf { it.endMillis != 0L }?.title }.distinct() // «Новини» на сотні каналів — одна назва: було 962 на кожне оновлення
        val mine = runCatching { recent.channels.first() }.getOrDefault(emptyList()).map { it.url }
        val matches = runCatching { matcher.match(titles) }.getOrDefault(emptyMap())
        val now = System.currentTimeMillis()
        val live = liveCards(links, schedules, matches, now)
        // Скільки каналів дійшло до екрана й скільки з них лишилось без програми — по застосунках.
        android.util.Log.i("LikhtarLive", "канали: ${links.size} → картки ${live.size}; " +
            links.groupingBy { it.packageName.ifBlank { "плейлист" } }.eachCount().entries.joinToString { (p, n) ->
                val shown = live.count { it.link.packageName.ifBlank { "плейлист" } == p }
                "${p.substringAfterLast('.')} $n/$shown"
            } + "; програм: ${schedules.size}")
        recentUrls = mine
        val pinned = runCatching { settings.settings.first().pinnedChannels }.getOrDefault(emptySet())
        val mineCards = mineCards(live, pinned, mine)
        val guide = guideChannels(live, groupOrder, lead, userOrder, groupEdits.groupOrder, groupEdits.groupNames.entries.associate { (o, n) -> n to o })
        val old = _state.value
        _state.value = old.copy(
            loading = false,
            mine = mineCards,
            pinned = pinned,
            schedules = schedules,
            backups = backups,
            message = when {
                guide.isNotEmpty() -> null
                // Каталог не приїхав, а своїх каналів немає — це збій мережі, не «нема джерел».
                fetched == null && !playlist && noOwn -> context.getString(R.string.cinema_live_no_epg)
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
            LiveNowMemory.playlist = playlistAt
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
            groups.hideChannelKey(card.link.key.ifBlank { com.likhtar.home.core.tv.channelKey(card.link.name) })
            _state.value = _state.value.let { s ->
                val channels = s.channels.filter { it.link.url != card.link.url }
                s.copy(mine = s.mine.filter { it.link.url != card.link.url }, channels = channels, groups = channels.map { liveGroup(it) }.distinct())
            }
            LiveNowMemory.state = null
        }
    }

    /** Довге OK на групі → «Вище»/«Нижче»: групи міняються місцями одразу, порядок — у правки. */
    fun moveGroup(group: String, delta: Int) {
        val order = _state.value.groups.toMutableList()
        val i = order.indexOf(group)
        val j = i + delta
        if (i < 0 || j !in order.indices) return
        java.util.Collections.swap(order, i, j)
        saveGroups { it.copy(groupOrder = order) }
        _state.value = _state.value.let { s ->
            s.copy(channels = s.channels.groupBy { liveGroup(it) }.let { by -> order.flatMap { by[it].orEmpty() } }, groups = order)
        }
    }

    /** «Приховати групу»: зникає з усім вмістом; «Показати сховані групи» на «Усі канали» повертає. */
    fun hideGroup(group: String) {
        saveGroups { it.copy(groupHidden = it.groupHidden + group) }
        _state.value = _state.value.let { s ->
            val channels = s.channels.filter { liveGroup(it) != group }
            s.copy(channels = channels, mine = s.mine.filter { liveGroup(it) != group }, groups = s.groups - group)
        }
    }

    fun renameGroup(group: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean == group) return
        saveGroups { it.renameGroup(group, clean) }
        val rename = { c: LiveCard -> if (liveGroup(c) == group) c.copy(link = c.link.copy(category = clean)) else c }
        _state.value = _state.value.let { s ->
            s.copy(channels = s.channels.map(rename), mine = s.mine.map(rename), groups = s.groups.map { if (it == group) clean else it }.distinct())
        }
    }

    /** Первісна назва перейменованої групи; `null` — група своєї назви не має. */
    fun originalGroup(group: String): String? = groupEdits.groupNames.entries.firstOrNull { it.value == group }?.key

    /** Скільки груп сховано — для «Показати сховані групи». */
    val hiddenGroups: Int get() = groupEdits.groupHidden.size

    fun showHiddenGroups() {
        saveGroups { it.copy(groupHidden = emptySet()) }
        viewModelScope.launch { refresh() }
    }

    private fun saveGroups(change: (ChannelEdits) -> ChannelEdits) {
        groupEdits = change(groupEdits)
        val edits = groupEdits
        LiveNowMemory.state = null
        viewModelScope.launch {
            // Від свіжих правок на диску: сторінка з телефона могла тим часом зберегти свої.
            val stored = runCatching { groups.channelEdits.first() }.getOrDefault(ChannelEdits())
            groups.setChannelEdits(stored.copy(groupOrder = edits.groupOrder, groupHidden = edits.groupHidden, groupNames = edits.groupNames))
        }
    }

    /**
     * Канали-потоки в порядку списку ТБ Ефіру — щоб плеєр перемикав їх ↑↓, не виходячи. Лише
     * ті, що грає плеєр (канали застосунків відкриваються самими застосунками).
     */
    private fun lineup(link: com.likhtar.home.core.tv.ChannelLink): com.likhtar.home.core.catalog.TvLineup? {
        val all = _state.value.channels.filter { it.link.packageName.isBlank() }.distinctBy { it.link.url }
        val at = all.indexOfFirst { it.link.url == link.url }
        if (at < 0 || all.size < 2) return null
        // Адреси й назви їдуть в extras, а Binder тягне ~1 МБ: на 20 тис. каналів запуск плеєра
        // падав. Вікно навколо поточного каналу — групи йдуть підряд, тож сусідні групи цілі.
        val from = (at - LINEUP_MAX / 2).coerceIn(0, (all.size - LINEUP_MAX).coerceAtLeast(0))
        val streams = all.subList(from, minOf(all.size, from + LINEUP_MAX))
        val index = at - from
        val now = System.currentTimeMillis()
        return com.likhtar.home.core.catalog.TvLineup(
            urls = streams.map { it.link.url },
            names = streams.map { channelLabel(it.link.name) },
            now = streams.map { c -> if (c.bare || c.endMillis < now) "" else c.raw },
            index = index,
            guide = runCatching { writeGuide(streams) }.getOrDefault(""),
            offset = from,
            portal = portalAccount,
            channels = streams.map { RecentChannel(url = it.link.url, name = it.link.name, image = it.link.logo, packageName = it.link.packageName) },
        )
    }

    /**
     * Програма для ТБ-режиму Just+: група, архів і передачі кожного каналу за індексом списку.
     * Файлом, бо на 1500 каналів із програмою extras не вистачає (межа Binder ~1 МБ).
     */
    private fun writeGuide(streams: List<LiveCard>): String {
        val schedules = _state.value.schedules
        val res = context.resources
        val channels = org.json.JSONArray()
        streams.forEach { c ->
            val s = schedules[c.link.url]
            val programmes = org.json.JSONArray()
            // [start, end, title, кадр, опис]; поточна передача — з кадром і описом картки (твір TMDB, якщо впізнали).
            val liveNow = s?.live?.let {
                com.likhtar.home.core.tv.EpgProgramme(
                    it.title, it.startMillis, it.endMillis,
                    description = c.work?.overview?.takeIf(String::isNotBlank) ?: c.description.ifBlank { it.description },
                    image = cardImage(c) ?: it.image,
                )
            }
            // Наперед — 12: «Вся програма» тримає 40, а файл плеєра на тисячу каналів не мусить рости.
            (s?.past.orEmpty().asReversed() + listOfNotNull(liveNow) + s?.upcoming.orEmpty().take(12)).forEach { p ->
                programmes.put(
                    org.json.JSONArray().put(p.startMillis).put(p.endMillis).put(p.title)
                        .put(p.image.orEmpty()).put(p.description.take(400)),
                )
            }
            channels.put(
                org.json.JSONObject()
                    .put("g", liveGroupTitle(res, liveGroup(c)))
                    .put("l", c.link.logo)
                    .put("p", programmes)
                    .apply {
                        _state.value.backups[c.link.url]?.let { put("b", org.json.JSONArray(it)) }
                        // Твір поточної передачі — для «Картка в Кінозалі» в плеєрі (лише впізнана передача).
                        c.work?.takeIf { it.tmdbId > 0 }?.let { w -> put("w", org.json.JSONObject().put("k", w.kind.name).put("i", w.tmdbId).put("s", c.startMillis)) }
                        c.link.archive?.let { a -> put("a", org.json.JSONObject().put("t", a.type).put("d", a.days).put("s", a.source)) }
                    },
            )
        }
        val dir = java.io.File(context.cacheDir, "tv").apply { mkdirs() }
        val file = java.io.File(dir, "guide.json")
        file.writeText(org.json.JSONObject().put("channels", channels).toString())
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    }

    fun open(card: LiveCard) {
        val link = card.link
        viewModelScope.launch {
            // Потік зі свого плейлиста — у вибраний плеєр (Налаштування → Перегляд), той самий
            // шлях, що й Кінозал; канал застосунку — посиланням у сам застосунок.
            val opened = if (link.packageName.isBlank()) {
                // «Ліхтар ТБ» — плеєр саме для каналів (↑↓, програма, архів): стоїть — канали йому.
                val tvPlayer = com.likhtar.home.core.data.AppUpdate.TV_PLAYER.takeIf(apps::isInstalled)
                // Плеєра немає зовсім (на Google TV з коробки його зазвичай нема) — не мовчимо.
                if (tvPlayer == null && apps.videoPlayers().isEmpty()) {
                    _state.value = _state.value.copy(needPlayer = true)
                    return@launch
                }
                // Інакше вибраний у Налаштуваннях, якщо він є на пристрої; немає — вибір системи.
                val chosen = tvPlayer ?: settings.settings.first().videoPlayerPackage.takeIf(apps::isInstalled).orEmpty()
                // Канал порталу: «Ліхтар ТБ» бере адресу в порталі сам; іншому плеєру — справжню зараз.
                val portalChannel = com.likhtar.home.core.tv.portalId(link.url)
                val uri = if (portalChannel != null && tvPlayer == null) com.likhtar.home.core.tv.portalLink(link.url)?.let { portal.link(it) } ?: link.url else link.url
                portalAccount = if (portalChannel != null || _state.value.channels.any { com.likhtar.home.core.tv.portalId(it.link.url) != null } ||
                    _state.value.backups.values.any { b -> b.any { com.likhtar.home.core.tv.portalId(it) != null } }) settings.settings.first().tvPortal else ""
                playback.play(
                    PlayRequest(
                        uri = uri,
                        mime = "video/*",
                        packageName = chosen,
                        resumeMs = 0,
                        title = null,
                        displayName = channelLabel(link.name),
                        tv = withContext(kotlinx.coroutines.Dispatchers.IO) { lineup(link) },
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

    /** «Встановити Ліхтар ТБ»: з нашого випуску на GitHub; не вийшло — плеєр з Google Play. */
    fun installTvPlayer() {
        viewModelScope.launch {
            if (!appUpdate.installTvPlayer()) openFree(FreeSource(PlayerOnPlay.first, PlayerOnPlay.second, installed = false))
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
        /** Скільки каналів віддаємо плеєру для ↑↓ (межа Binder для extras). */
        const val LINEUP_MAX = 1000
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
    // Програму беремо на цю хвилину, а не на ту, коли її завантажили: розклад живе до 5 хв (а
    // повернення в розділ — і довше), і канал, чия передача тим часом скінчилась, зникав зі
    // списку, хоча наступна вже йшла. У паузі ефіру — найближча наступна («о 03:40»).
    val rolled = schedules[link.url]?.rolledTo(now)
    val on = rolled?.live
        ?: rolled?.upcoming?.firstOrNull()?.let { NowOnAir(title = it.title, description = it.description, image = it.image, startMillis = it.startMillis, endMillis = it.endMillis) }
        // Потік свого плейлиста (без пакета) лишається й без програми — це канал людини.
        ?: return@mapNotNull if (link.packageName.isBlank()) LiveCard(link, "", 0, 0, channelFrame(link, now), null) else null
    if (on.endMillis != 0L && on.endMillis <= now) return@mapNotNull null
    // Без кадру з програми — кадр самого каналу (живе передача, а не порожня картка).
    LiveCard(link, on.title, on.startMillis, on.endMillis, on.image ?: channelFrame(link, now), matches[on.title], on.description)
}

/**
 * Кадр каналу на цю хвилину: Flussonic віддає знімок з DVR за адресою
 * `<тека потоку>/<unix>-preview.jpg?<той самий токен>`. Час — кратний 5 хв і на півтори хвилини
 * позаду, щоб знімок уже був, а картинки кешувались, доки картки не оновляться (раз на 5 хв).
 * Пробуємо для кожного потоку з адресою на манер Flussonic: плейлист позначає архів далеко не в
 * усіх каналів, де DVR (і кадри) є. Не дав кадру — картка показує знак каналу.
 */
internal fun channelFrame(link: ChannelLink, now: Long): String? {
    val url = link.url.takeIf { it.startsWith("http") && link.packageName.isBlank() } ?: return null
    val path = url.substringBefore('?')
    if (path.substringAfterLast('/') !in FlussonicNames) return null
    val query = url.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
    val at = (now / 1000 - 90) / 300 * 300
    return path.substringBeforeLast('/') + "/$at-preview.jpg" + query
}

private val FlussonicNames = setOf("video.m3u8", "index.m3u8", "mono.m3u8", "mpegts")

/** Канали без групи в каталогі. */
internal const val LiveRowOther = "Інші канали"

private const val UaGroup = "Українські"

/** Група каналу для сцени 2 — та сама, що дає тематичний ряд. */
internal fun liveGroup(card: LiveCard): String = card.link.category.ifBlank { LiveRowOther }

/**
 * Канали сцени 2: групи в порядку сервера (невідомі й «Інші» — наприкінці), усередині
 * українських — головні канали першими, далі як ідуть. Один канал — одна картка.
 */
internal fun guideChannels(
    live: List<LiveCard>,
    order: List<String>,
    lead: List<String>,
    userOrder: List<String> = emptyList(),
    userGroups: List<String> = emptyList(),
    /** Своя назва групи → первісна: порядок сервера й головні канали ведуться за первісною. */
    originals: Map<String, String> = emptyMap(),
): List<LiveCard> {
    val base = { c: LiveCard -> liveGroup(c).let { originals[it] ?: it } }
    val rank = { g: String -> if (g == LiveRowOther) Int.MAX_VALUE else order.indexOf(g).takeIf { it >= 0 } ?: (Int.MAX_VALUE - 1) }
    val auto = live.distinctBy { it.link.url }
        .sortedWith(compareBy<LiveCard>({ rank(base(it)) }, { if (base(it) == UaGroup) uaChannelRank(it.link.name, lead) else 0 }))
    // Порядок з редактора; нові канали — після впорядкованих. Групи лишаються суцільними:
    // група стоїть там, де її перший канал.
    val index = userOrder.withIndex().associate { (i, k) -> k to i }
    val byChannels = if (userOrder.isEmpty()) auto else auto.sortedBy { index[it.link.key] ?: Int.MAX_VALUE }.groupBy { liveGroup(it) }.values.flatten()
    if (userGroups.isEmpty()) return byChannels
    // Порядок груп, заданий на ТБ, — понад усе; групи, яких у ньому немає (нові), — після.
    val at = userGroups.withIndex().associate { (i, g) -> g to i }
    return byChannels.groupBy { liveGroup(it) }.entries.sortedBy { at[it.key] ?: Int.MAX_VALUE }.flatMap { it.value }
}

/** Правки груп людини: своя назва й сховані групи (з усім вмістом). */
internal fun groupEdited(links: List<ChannelLink>, edits: ChannelEdits): List<ChannelLink> =
    if (edits.groupNames.isEmpty() && edits.groupHidden.isEmpty()) links else links.mapNotNull { l ->
        val named = edits.groupNames[l.category.ifBlank { LiveRowOther }]
        if ((named ?: l.category.ifBlank { LiveRowOther }) in edits.groupHidden) null
        else if (named != null) l.copy(category = named) else l
    }

/** Нові правки груп: [group] перейменовано на [name] — назва, порядок і сховані йдуть за нею. */
internal fun ChannelEdits.renameGroup(group: String, name: String): ChannelEdits {
    val original = groupNames.entries.firstOrNull { it.value == group }?.key ?: group
    val names = (groupNames - original).let { if (name == original) it else it + (original to name) }
    return copy(
        groupNames = names,
        groupOrder = groupOrder.map { if (it == group) name else it },
        groupHidden = groupHidden.map { if (it == group) name else it }.toSet(),
    )
}

/** Головні канали ([lead] від сервера) — першими й саме так; решта — після них (сортування стабільне). */
internal fun uaChannelRank(name: String, lead: List<String>): Int {
    val n = name.lowercase().replace(" ", "")
    return lead.indexOfFirst { n.startsWith(it) }
        .takeIf { it >= 0 } ?: Int.MAX_VALUE
}

/**
 * Назва передачі для картки. У Sweet.TV частина передач зветься самим номером — «Сезон 7,
 * Випуск 3» на М.Шеф, — і картка казала лише «Сезон 7». Тоді спереду стає канал.
 */
internal fun programmeName(link: ChannelLink, name: String): String =
    if (NumberOnlyName.containsMatchIn(name)) "${channelLabel(link.name)} · $name" else name

private val NumberOnlyName = Regex("""^(сезон|випуск|серія|епізод|частина)\b""", RegexOption.IGNORE_CASE)

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
