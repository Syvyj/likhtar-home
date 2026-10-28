package com.likhtar.home.feature.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.data.AppStateRepository
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.RecentChannel
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.smarthome.SmartDevice
import com.likhtar.home.core.smarthome.SmartHomeRepository
import com.likhtar.home.core.data.LikhtarSettings
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.WeatherRepository
import com.likhtar.home.core.model.AppCategory
import com.likhtar.home.core.model.LauncherApp
import com.likhtar.home.core.catalog.ContinueItem
import com.likhtar.home.core.catalog.ContinuePlayback
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.openIn
import com.likhtar.home.core.catalog.ResumeRows
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.WeatherSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Плитка нижньої навігації Головної. */
data class HomeTile(
    val section: LikhtarSection,
    val accent: Long,
    val label: String = section.title,
    /** Локалізована назва замість [label]; за замовчуванням — назва розділу. */
    @StringRes val labelRes: Int? = section.titleRes,
)



data class HomeUiState(
    val time: String = "",
    val date: String = "",
    val weather: WeatherSnapshot? = null,
    val showWeather: Boolean = true,
    val tiles: List<HomeTile> = emptyList(),
    val pinnedApps: List<LauncherApp> = emptyList(),
    /** Контекстний ряд «Ігор»: останні запущені ігри першими. */
    val games: List<LauncherApp> = emptyList(),
    val resume: List<ResumeCard> = emptyList(),
    /** Ряд над «Реаліті»: випуски, на яких зупинились. */
    val realityResume: List<ResumeCard> = emptyList(),
    /** Контекстний ряд ТБ Ефіру: закріплені першими, далі — історія. */
    val recentChannels: List<RecentChannel> = emptyList(),
    /** Які з показаних каналів закріплені: у ряду вони позначені. */
    val pinnedChannels: Set<String> = emptySet(),
    /** Чи показувати картку «Ліхтарик» у ряду Головної. */
    val showTorch: Boolean = true,
    /** Скільки карток показує «Продовжити перегляд». */
    /** Контекстний ряд Пошуку: запити, з яких щось відкривали. */
    val recentSearches: List<String> = emptyList(),
    /** Контекстний ряд Смарт дому: компактний зріз того, що зараз удома. */
    val smartDevices: List<SmartDevice> = emptyList(),
    /** Іде вибір джерела для «Продовжити» — на екрані оверлей очікування. */
    val preparing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weatherRepository: WeatherRepository,
    private val appsRepository: AppsRepository,
    private val appStateRepository: AppStateRepository,
    private val continuePlayback: ContinuePlayback,
    private val recentHistory: RecentHistoryRepository,
    private val smartHomeRepository: SmartHomeRepository,
    private val resumeRows: ResumeRows,
    private val watchlist: com.likhtar.home.core.catalog.WatchlistRepository,
    private val appUpdate: com.likhtar.home.core.data.AppUpdate,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Новіша версія з GitHub — пігулкою над LIKHTAR HOME; `null` — оновлювати нічого. */
    val update = appUpdate.available
    val updateProgress = appUpdate.progress
    val updateError = appUpdate.error
    val updateAwaitingPermission = appUpdate.awaitingPermission

    fun installUpdate(release: com.likhtar.home.core.data.AppRelease) {
        viewModelScope.launch { appUpdate.install(release) }
    }

    fun clearUpdateError() = appUpdate.clearError()

    /** Радар української: тайтл, на який чекали, уже з озвучкою — перша картка «Продовжити». */
    private val uaArrival = MutableStateFlow<ResumeCard?>(null)

    /** Картку радара відкрили — сервер більше її не пропонує. */
    fun arrivalSeen(card: ResumeCard) {
        val item = card.openTitle ?: return
        uaArrival.value = null
        viewModelScope.launch { watchlist.markUaArrivalSeen(item) }
    }

    /**
     * Картка чужого застосунку в ряду «Продовжити»: вмикаємо там, де воно лежить.
     *
     * Повертає `false`, коли застосунку немає, — екран тоді скаже про це, а не змовчить.
     */
    fun openInApp(card: ResumeCard): Boolean = card.openIn(appsRepository)

    /**
     * Годинник: звіряємось раз на 10 с, а віддаємо лише нову хвилину — на екрані хвилини, і
     * кожна зайва емісія перебудовувала весь стан Головної.
     */
    private val clock = flow {
        var minute = -1L
        while (true) {
            val now = Date()
            if (now.time / 60_000 != minute) {
                minute = now.time / 60_000
                emit(now)
            }
            delay(CLOCK_INTERVAL_MS)
        }
    }

    /** Недодивлене з «Нашого»: сховище спільне з розділом, тому дані ті самі. */

    /** «Продовжити» Кінозалу — з [ResumeRows], як на кожному екрані. */
    private val continueWatching = MutableStateFlow<List<ResumeCard>>(emptyList())

    private val preparing = MutableStateFlow(false)

    private val apps = combine(appsRepository.apps, appStateRepository.states) { all, states ->
        val pinned = all.filter { it.id in states.pinnedToHome }
        // Закріплені йдуть першими, але не перетворюють вертикальний перемикач на одну
        // кнопку: доповнюємо його іншими встановленими відеозастосунками до п'яти.
        val video = all.filter { it.id !in states.pinnedToHome && it.isVideo }
            .sortedBy { if (it.packageName in com.likhtar.home.core.model.StreamingService.YOUTUBE.packages) 0 else 1 }
        val remaining = all.filter { app ->
            app.id !in states.pinnedToHome && video.none { it.id == app.id }
        }
        (pinned + video + remaining)
            .distinctBy { it.id }
            // Стеля, а не остаточна кількість: скільки саме показати, вирішує ряд за
            // налаштуванням, і йому може знадобитися сім.
            .take(APPS_MAX)
    }

    /** Ігри для ряду над плиткою «Ігри»: категорія з урахуванням ручної зміни, без прихованих. */
    private val games = combine(appsRepository.apps, appStateRepository.states) { all, states ->
        all.filter { it.id !in states.hidden && (states.stateOf(it.id).category ?: it.category) == AppCategory.GAMES }
    }

    /**
     * Перечитати «Продовжити». Викликається з екрана на `ON_RESUME`.
     *
     * Тут був цикл на п'ять хвилин, і він робив дві зайві речі. По-перше, тягнув EPG на
     * шість каналів — результат клався в `onAir`, який на Головній ніхто не читав
     * (`resumeCards` бере лише «Наше» й недодивлене), тобто мережа на старті лаунчера
     * працювала в порожнечу. По-друге, п'ять хвилин — це рівно та затримка, з якою ряд
     * оновлювався після повернення з YouTube чи MEGOGO. `ON_RESUME` дає обидва
     * запити тоді, коли вони справді потрібні, і жодного, поки телевізор стоїть.
     */
    init {
        viewModelScope.launch { appUpdate.check() }
        // ON_RESUME приходить раніше, ніж прогрес після плеєра доходить до сервера: ряд
        // лишався на старій серії. Перечитуємо, щойно запис закінчено.
        viewModelScope.launch { continuePlayback.saved.collect { refreshResume() } }
    }

    fun refreshResume() {
        viewModelScope.launch {
            continueWatching.value = resumeRows.cinema(CONTINUE_MAX)
            showsWatching.value = resumeRows.reality().take(CONTINUE_MAX)
            uaArrival.value = runCatching { watchlist.uaArrived() }.getOrNull()?.let { arrival ->
                ResumeCard(
                    title = context.getString(R.string.home_ua_arrived_title, arrival.item.title),
                    subtitle = if (arrival.waitingSince > 0) {
                        context.getString(R.string.home_ua_arrived_waiting_since, arrivalDate().format(Date(arrival.waitingSince * 1000)))
                    } else {
                        context.getString(R.string.home_ua_arrived_waited)
                    },
                    section = LikhtarSection.CINEMA,
                    imageUrl = TmdbImages.backdrop(arrival.item.backdropPath) ?: TmdbImages.poster(arrival.item.posterPath),
                    openTitle = arrival.item,
                )
            }
        }
    }

    private data class HomeCoreState(
        val now: Date,
        val weather: WeatherSnapshot?,
        val showWeather: Boolean,
        val smartHomeEnabled: Boolean,
        val dockApps: List<LauncherApp>,
        val games: List<LauncherApp>,
        /** Налаштування контекстних рядів — цілим блоком, щоб не плодити поля. */
        val settings: LikhtarSettings,
    )

    private val homeCoreState = combine(
        clock,
        // Погода — необов'язкова: без мережі годинник і док мусять з'явитися одразу, а не
        // чекати першої відповіді Open-Meteo (combine чекає на кожен потік).
        weatherRepository.weather()
            .map<WeatherSnapshot, WeatherSnapshot?> { it }
            .onStart { emit(null) },
        settingsRepository.settings,
        apps,
        games,
    ) { now, weatherSnapshot, settings, dockApps, games ->
        HomeCoreState(
            now = now,
            weather = weatherSnapshot,
            showWeather = settings.showWeather,
            smartHomeEnabled = settings.smartHomeEnabled,
            dockApps = dockApps,
            games = games,
            settings = settings,
        )
    }

    private data class ResumeSources(
        val watching: List<ResumeCard>,
        val preparing: Boolean,
        val arrival: ResumeCard? = null,
        val shows: List<ResumeCard> = emptyList(),
    )

    private val showsWatching = MutableStateFlow<List<ResumeCard>>(emptyList())

    private val resumeSourcesFlow = combine(
        continueWatching,
        preparing,
        uaArrival,
        showsWatching,
    ) { watching, busy, arrival, shows ->
        ResumeSources(watching, busy, arrival, shows)
    }

    private data class ContextSources(
        val channels: List<RecentChannel>,
        val searches: List<String>,
        val devices: List<SmartDevice>,
        /** Ідентифікатори застосунків у порядку останніх запусків. */
        val recentApps: List<String>,
    )

    /**
     * Наповнення контекстних рядів доку.
     *
     * Історії локальні й дешеві, тому їх можна тримати підписаними весь час, поки
     * Головна на екрані. Смарт дім бере вже наявний потік стану — власного запиту не
     * робимо: ряд показує рівно те, що й розділ.
     */
    private val contextSources = combine(
        recentHistory.channels,
        recentHistory.searches,
        smartHomeRepository.state,
        recentHistory.apps,
    ) { channels, searches, smartHome, recentApps ->
        ContextSources(
            channels = channels,
            searches = searches.take(CONTEXT_PILLS),
            // «Дім зараз» — спершу застереження (фільтр, збій друку), далі друк і те, що
            // увімкнене, потім решта.
            devices = smartHome.devices.sortedBy {
                when {
                    it.warning -> 0
                    it.isOn -> 1
                    else -> 2
                }
            },
            recentApps = recentApps,
        )
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            homeCoreState,
            resumeSourcesFlow,
            contextSources,
        ) { core: HomeCoreState, sources: ResumeSources, context: ContextSources ->
            HomeUiState(
                time = com.likhtar.home.core.designsystem.clockTime(core.now.time),
                date = DATE_FORMAT.format(core.now).replaceFirstChar { it.uppercase() },
                weather = core.weather,
                showWeather = core.showWeather,
                tiles = tiles()
                    .filter { it.section != LikhtarSection.SMART_HOME || core.smartHomeEnabled }
                    .filter { it.section != LikhtarSection.GAMES || core.settings.gamesEnabled }
                    .filter { it.section.route !in core.settings.hiddenDockSections },
                pinnedApps = appsRow(core.dockApps, context.recentApps, core.settings),
                games = appsRow(
                    core.games,
                    context.recentApps,
                    core.settings.copy(homeAppsPinned = false),
                ),
                resume = listOfNotNull(sources.arrival) + sources.watching,
                realityResume = sources.shows,
                recentChannels = channelRow(context.channels, core.settings),
                pinnedChannels = core.settings.pinnedChannels,
                recentSearches = context.searches,
                smartDevices = context.devices.take(CONTEXT_CARDS),
                showTorch = core.settings.homeShowTorch,
                preparing = sources.preparing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )


    /** Канал із контекстного ряду вмикається тим самим шляхом, що й у ТВ Ефірі. */
    fun openChannel(channel: RecentChannel) {
        appsRepository.openLink(channel.url, channel.packageName)
    }

    fun launch(app: LauncherApp): Boolean {
        viewModelScope.launch { recentHistory.rememberApp(app.id) }
        return appsRepository.launch(app)
    }

    /**
     * Ряд каналів: закріплені завжди першими й ніколи не витісняються історією.
     *
     * Закріпити можна лише те, що вже вмикали, — і це навмисно: список каналів живе в
     * ТВ Ефірі, а не в Головній, і дублювати його сюди означало б тягнути весь каталог
     * заради чотирьох карток.
     */
    private fun channelRow(history: List<RecentChannel>, settings: LikhtarSettings): List<RecentChannel> {
        val pinned = history.filter { it.url in settings.pinnedChannels }
        val rest = history.filterNot { it.url in settings.pinnedChannels }
        return (pinned + rest).take(settings.homeChannelCount)
    }

    /**
     * Ряд застосунків: або закріплені на Головній, або останні запущені.
     *
     * `dockApps` уже впорядковані «закріплені → відео → решта», тож для режиму
     * закріплених достатньо взяти їх як є. Для режиму «останні» переставляємо той самий
     * список у порядку історії запусків.
     */
    private fun appsRow(
        dockApps: List<LauncherApp>,
        recentIds: List<String>,
        settings: LikhtarSettings,
    ): List<LauncherApp> {
        val limit = settings.homeAppsCount
        if (settings.homeAppsPinned) return dockApps.take(limit)
        val byId = dockApps.associateBy { it.id }
        val recent = recentIds.mapNotNull(byId::get)
        return (recent + dockApps.filterNot { it.id in recentIds }).distinctBy { it.id }.take(limit)
    }

    /**
     * Плитки — це лише назви розділів. Підписи прибрано свідомо: половина з них була
     * вигадана («Вечір · 3 сцени активні»), а решта дублювала те, що видно всередині розділу.
     */
    private fun tiles(): List<HomeTile> = buildList {
        add(HomeTile(LikhtarSection.HOME, ACCENT_YELLOW, label = "LIKHTAR HOME", labelRes = null))
        // Кінозал свого акценту не має: його хлопавка біла й на фокусі темніє.
        add(HomeTile(LikhtarSection.CINEMA, ACCENT_GRAY))
        add(HomeTile(LikhtarSection.LIVE_TV, ACCENT_CYAN, labelRes = R.string.home_tile_live_tv))
        add(HomeTile(LikhtarSection.REALITY, ACCENT_PINK))
        add(HomeTile(LikhtarSection.SMART_HOME, ACCENT_MINT))
        // Лупа в доці відкриває розділ одразу на полі пошуку, тож і зветься «Пошук»: «Ліхтарик»
        // уже є в ряду над LIKHTAR HOME, і дві однакові кнопки плутали.
        add(HomeTile(LikhtarSection.SEARCH, ACCENT_VIOLET, labelRes = R.string.home_tile_search))
        add(HomeTile(LikhtarSection.GAMES, ACCENT_PINK))
        add(HomeTile(LikhtarSection.APPS, ACCENT_BLUE))
    }

    private companion object {
        const val CLOCK_INTERVAL_MS = 10_000L
        const val STOP_TIMEOUT_MS = 5_000L
        /** Стеля запиту недодивленого: ряд може просити щонайбільше сім карток. */
        const val CONTINUE_MAX = 7

        /** Те саме для застосунків. */
        const val APPS_MAX = 7

        /** Скільки карток вміщає контекстний ряд, не стискаючись. */
        const val CONTEXT_CARDS = 4

        /** Пігулки запитів вужчі за картки, тому їх у ряд стає більше. */
        const val CONTEXT_PILLS = 5
        const val ACCENT_CYAN = 0xFF22D3EE
        const val ACCENT_PINK = 0xFFF472B6
        const val ACCENT_YELLOW = 0xFFFACC15
        const val ACCENT_GRAY = 0xFF9AA6AC
        const val ACCENT_MINT = 0xFF3DDCA8
        const val ACCENT_VIOLET = 0xFFA78BFA
        const val ACCENT_BLUE = 0xFF60A5FA

        // SimpleDateFormat замість java.time: minSdk 23 без core library desugaring. Годинник —
        // clockTime() з дизайн-системи: статичний формат тримав пояс із запуску процесу.

        /**
         * Повний день тижня: «Середа, 26 серпня».
         *
         * Скорочений («Ср») був потрібен, поки дата тулилася в один рядок із містом під
         * вордмарком. Тепер вона стоїть окремим рядком під годинником, і місця вистачає.
         */
        // Щоразу новий: мову інтерфейсу можна змінити, не перезапускаючи процес.
        val DATE_FORMAT get() = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    }
}

/** «ви чекали з 3 вересня». */
private fun arrivalDate() = SimpleDateFormat("d MMMM", Locale.getDefault())
