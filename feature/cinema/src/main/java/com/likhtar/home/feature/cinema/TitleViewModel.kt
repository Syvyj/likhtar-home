package com.likhtar.home.feature.cinema

import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.TitleMark
import com.likhtar.home.core.catalog.TitleMarksRepository
import com.likhtar.home.core.catalog.cleanStreamLabel
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.qualityLabel
import com.likhtar.home.core.catalog.PlayRequest
import com.likhtar.home.core.catalog.PlaybackController
import com.likhtar.home.core.catalog.UaAudioRepository
import com.likhtar.home.core.catalog.PlayingTitle
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.Episode
import com.likhtar.home.core.catalog.InstalledAppSearch
import com.likhtar.home.core.catalog.LikhtarStreams
import com.likhtar.home.core.catalog.TorrentStat
import com.likhtar.home.core.catalog.torrentRefOf
import com.likhtar.home.core.catalog.bestFirstKey
import com.likhtar.home.core.catalog.qualityOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.likhtar.home.core.catalog.StreamOption
import com.likhtar.home.core.catalog.ukrainianAudio
import com.likhtar.home.core.catalog.VoiceGroup
import com.likhtar.home.core.catalog.playerIsSilent
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TitleDetails
import com.likhtar.home.core.catalog.WatchOption
import com.likhtar.home.core.catalog.TitleVideo
import com.likhtar.home.core.catalog.WatchProgressRepository
import com.likhtar.home.core.catalog.WatchlistRepository
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.TitleActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * «Що було в N−1 сезоні» — першим рядком серій сезону N: перед сезоном згадують саме
 * попередній. Сезон, який людина саме дивиться, не переказуємо — це спойлер. 0 — рядка немає.
 */
/** Трейлер для ↑ з першої сцени: український, якщо є, інакше перший; переказів сезону не беремо. */
val TitleUiState.trailer: TitleVideo?
    get() = details?.videos?.filter { it.type == "Trailer" }?.let { list -> list.firstOrNull { it.language == "uk" } ?: list.firstOrNull() }

val TitleUiState.recapBefore: Int
    get() = (season - 1).takeIf { it > 0 && !(resumeStarted && resumeSeason == it) } ?: 0

/** Переказ сезону у вікні: поки [loading] — пишеться; [error] — чому не вийшло. */
data class RecapState(val season: Int, val loading: Boolean = true, val text: String = "", val error: String = "")

data class TitleUiState(
    val loading: Boolean = true,
    val recap: RecapState? = null,
    /** Відео-переказ сезону українською (перевірений сервером): сезон → ролик. */
    val recapVideos: Map<Int, TitleVideo> = emptyMap(),
    val details: TitleDetails? = null,
    val inWatchlist: Boolean = false,
    /**
     * Ми САМІ бачили українську доріжку — прочитані megogo/sweet.tv, наші сайтові джерела,
     * торенти, `audioLanguages` із JustWatch. `false` означає і «немає», і «не питали»:
     * мітка ставиться лише на певне «є», а не на здогад.
     */
    val uaAudio: Boolean = false,
    /**
     * Чим ця мітка підтверджена: сервіс, де ми прочитали доріжку, або названа озвучка.
     *
     * Порожньо — підстава є, але назвати її нічим (власне джерело, походження). Мітка має бути доказом, а не словом, — «є на MEGOGO» пояснює її сама.
     */
    val uaProof: List<String> = emptyList(),
    /** Українські озвучки одразу на відкритті — у порядку, в якому їх вмикає «Дивитись». */
    val voices: List<String> = emptyList(),
    /** «1080p», «4K · Dolby Vision» — найкраща якість наших джерел; порожньо — не знаємо. */
    val quality: String = "",
    val message: String? = null,
    val picker: PickerState? = null,
    /** Іде прогрів роздачі перед запуском плеєра — на екрані оверлей очікування. */
    val preparing: Boolean = false,
    /**
     * Що з встановлених застосунків відкриє саме цей матеріал. Приїжджає пізніше за
     * картку — питаємо провайдери вже після того, як сторінка намалювалась.
     */
    val appOptions: List<WatchOption> = emptyList(),
    /**
     * Що написати над лоадером, поки джерело готується.
     *
     * Різні шляхи чекають різного: прогрів торента — це «дані від пірів», а вибрана
     * озвучка — це «сервер перебирає її джерела». Однаковий підпис на обох брехав би
     * половину часу.
     */
    /** `null` — загальний підпис «підключаюсь до роздачі» (його ставить екран). */
    val preparingNote: Pair<String, String>? = null,
    /** Піри, швидкість і підвантаження роздачі, поки вона готується; `null` — не роздача. */
    val preparingStat: TorrentStat? = null,
    /** Сторінка серіалу: обраний сезон і його серії з TMDB. */
    val season: Int = 1,
    val episodes: List<Episode> = emptyList(),
    /** Частини франшизи — для однойменного таба; порожньо, поки не завантажені. */
    val franchise: List<CatalogItem> = emptyList(),
    /**
     * Серії показаного сезону, для яких уже є українське джерело.
     *
     * Окремо від [episodes], бо це відповіді з різних місць: TMDB каже, які серії існують,
     * агрегатор — які з них є чим дивитись. Порожньо означає «ще не питали», і це навмисно
     * не відрізняється від «нема»: мітка або зʼявляється, або ні.
     */
    val translated: Set<Int> = emptySet(),
    /** Остання незавершена серія: сервер не віддає секунди з зовнішнього плеєра. */
    val resumeSeason: Int = 0,
    val resumeEpisode: Int = 0,
    /**
     * Чи прийшла вже відповідь про продовження.
     *
     * Потрібен саме прапорець, а не нулі: нулі означають і «ще не знаємо», і «нічого не
     * дивились», а підпис головної дії в цих випадках різний. Без нього кнопка встигала б
     * сказати «Дивитись S01E01» і за півсекунди перекинутись на «Продовжити S02E04».
     */
    val resumeKnown: Boolean = false,
    /** Перегляд уже починався — для фільму це все, що потрібно для підпису «Продовжити». */
    val resumeStarted: Boolean = false,
    /** Порядок груп кнопок на картці — із налаштувань Кінозалу. */
    val actionsOrder: List<String> = TitleActions.DEFAULT,
    /**
     * Радар української: з якого часу чекаємо озвучку (unix-секунди), 0 — не чекаємо,
     * `null` — не питали (або українська вже є, і кнопки немає).
     */
    val uaWaitSince: Long? = null,
    /** Живий фон першої сцени — з налаштувань. */
    val liveStills: Boolean = true,
    /**
     * Дата виходу серії, яку мали б продовжити, якщо вона ще попереду. Порожньо — або вже
     * вийшла, або ми цього не знаємо.
     *
     * Тримаємо окремо від [episodes], бо показаний сезон користувач міг перемкнути вручну,
     * а питання «чи є що вмикати» стосується саме серії продовження.
     */
    val resumeAirDate: String = "",
    /** «Позначити»: позначка профілю на цьому тайтлі; `null` — немає або не питали. */
    val mark: TitleMark? = null,
    val markPanel: Boolean = false,
)

/**
 * Селектор джерела: одне питання — чим це грати.
 *
 * Сезон і серію він колись питав теж, бо кнопка «Дивитись» користувалася ним замість того,
 * щоб грати. Відколи вона грає сама, серія відома ще до відкриття селектора, а вибір серії
 * живе там, де йому й місце, — у «Переліку серій».
 */
data class PickerState(
    val loading: Boolean = false,
    /**
     * Озвучки, а не окремі джерела.
     *
     * Плоским списком це й було, і в ньому «Серія 1 · Джерело» стояла поруч із «Серія 1 ·
     * Джерело 2» — людина обирала балансер, хоч питання в неї інше: якою озвучкою дивитись.
     * Сервер зводить одну озвучку з різних сайтів в один пункт, і перебір усередині
     * вибраної озвучки робить теж він.
     */
    val voices: List<VoiceGroup> = emptyList(),
    val season: Int = 0,
    val episode: Int = 0,
    /**
     * Чому список порожній — словами сервера.
     *
     * Потрібне саме тут, а не в загальному `message`: людина запустила пошук у цій
     * модалці, і відповідь мусить з'явитись у ній же. Загальний напис на сторінці
     * матеріалу вона просто не бачить — його малює лише екран без деталей.
     */
    val note: String = "",
) {
}

/**
 * Сторінка фільму.
 *
 * Аргументи маршруту — лише тип і TMDB id: усе решта тягнеться з мережі. Це навмисно,
 * щоб на сторінку можна було потрапити звідки завгодно (каталог, пошук, згодом голос),
 * не тягнучи за собою цілу картку.
 */
@HiltViewModel
class TitleViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val catalog: CatalogRepository,
    private val watchlist: WatchlistRepository,
    private val streams: LikhtarStreams,
    private val progress: WatchProgressRepository,
    private val appSearch: InstalledAppSearch,
    private val apps: AppsRepository,
    private val settings: SettingsRepository,
    private val history: RecentHistoryRepository,
    private val prefs: com.likhtar.home.core.catalog.ProfilePrefsRepository,
    private val playback: PlaybackController,
    private val uaAudioRepository: UaAudioRepository,
    private val marks: TitleMarksRepository,
    private val media: com.likhtar.home.core.catalog.media.MediaLibrary,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeItem: CatalogItem? = savedStateHandle.toRoute<TitleRoute>().let { r ->
        r.id?.let { CatalogItem(tmdbId = it, kind = CinemaViewModel.kindOf(r.kind), title = "", year = "") }
    }

    /** Файли цієї назви у своїй медіатеці: є — на сторінці кнопка «З медіатеки». */
    val localFiles: StateFlow<List<com.likhtar.home.core.catalog.media.LocalFile>> = media.state
        .map { routeItem?.let(media::filesFor).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Файл із медіатеки — у плеєр за налаштуванням, із прогресом як у будь-якого джерела:
     * «Продовжити» й позначки серій працюють і для своїх файлів.
     */
    fun playLocal(file: com.likhtar.home.core.catalog.media.LocalFile) {
        viewModelScope.launch {
            val url = media.playUrl(file) ?: return@launch
            val item = state.value.details?.item ?: routeItem ?: return@launch
            val season = file.season.takeIf { item.kind == TitleKind.TV } ?: 0
            val episode = file.episode.takeIf { item.kind == TitleKind.TV } ?: 0
            val launched = playback.play(
                PlayRequest(
                    uri = url,
                    mime = "video/*",
                    packageName = settings.settings.first().videoPlayerPackage,
                    resumeMs = progress.positionOf(item.tmdbId, item.kind, season, episode),
                    title = PlayingTitle(item = item, season = season, episode = episode),
                    source = com.likhtar.home.core.catalog.PlaybackController.LOCAL_SOURCE,
                ),
            )
            if (!launched) state.update { it.copy(message = context.getString(R.string.cinema_player_open_failed)) }
        }
    }

    /** Коли стартував плеєр із фільмом — для порога «додивився». */
    private var movieStartedAt: Long? = null

    private val state = MutableStateFlow(TitleUiState())
    val uiState: StateFlow<TitleUiState> = state.asStateFlow()

    /** Відкрито з «Продовжити» серіалу — одразу сцена серій з фокусом на серії продовження. */
    var openOnEpisodes = false
        private set

    init {
        // Порядок кнопок читаємо потоком, а не раз: змінили в налаштуваннях — картка
        // перемалювалась, без повернення на неї.
        viewModelScope.launch {
            settings.settings.collect { saved ->
                state.update { it.copy(actionsOrder = TitleActions.visible(saved.titleActionsOrder), liveStills = saved.titleLiveStills) }
            }
        }
        val route = savedStateHandle.toRoute<TitleRoute>()
        openOnEpisodes = com.likhtar.home.core.catalog.OpenOnEpisodes.consume(route.id)
        val kind = CinemaViewModel.kindOf(route.kind)
        val id: Int? = route.id
        if (id == null) {
            state.value = TitleUiState(loading = false, message = context.getString(R.string.cinema_unknown_movie))
        } else {
            load(CatalogItem(tmdbId = id, kind = kind, title = "", year = ""))
        }
    }

    private fun loadUaAudio(item: CatalogItem) {
        viewModelScope.launch {
            val known = runCatching { uaAudioRepository.of(listOf(item.tmdbId)) }.getOrDefault(emptyMap())
            val flag = known[item.tmdbId]
            state.update {
                it.copy(
                    uaAudio = flag?.ua == true,
                    // Лише вид озвучки («Дубляж»), без сервісів: де дивитись, кажуть кнопки
                    // сервісів із власною міткою «UA», а в рядку міток назва «MEGOGO»
                    // читалась як ще одна властивість фільму.
                    uaProof = flag?.voices.orEmpty().take(1),
                    uaWaitSince = null,
                    // Та сама якість, що на картках: з наших живих джерел.
                    quality = flag?.let {
                        CatalogItem(tmdbId = 0, kind = item.kind, title = "", year = "", quality = it.quality, hdr = it.hdr)
                            .qualityLabel
                    }.orEmpty(),
                )
            }
            // Без української — кнопка радара; стан очікування питаємо лише тут.
            if (flag?.ua != true) {
                val since = watchlist.uaWaitSince(item)
                state.update { it.copy(uaWaitSince = since) }
            }
        }
    }

    /** Озвучки для шапки: швидкий запит до бази сервера, без опитування сайтів. */
    private fun loadVoices(item: CatalogItem) {
        viewModelScope.launch {
            val (season, episode) = if (item.kind == TitleKind.TV) impliedEpisode() else 0 to 0
            val groups = runCatching { streams.voices(item, season, episode) }.getOrDefault(emptyList())
            // Українські — поіменно; уся інша мова — одним словом «Інші» наприкінці
            // (LostFilm не має стояти першим і зватися озвучкою).
            val ours = groups.filter { it.lang != "other" }.map { it.short }.distinct().take(VOICES_SHOWN)
            val shown = if (groups.any { it.lang == "other" }) ours + context.getString(R.string.cinema_voices_other) else ours
            state.update { st -> st.copy(voices = shown) }
        }
    }

    /** «Спробувати ще» зі сторінки, що не відкрилась. */
    fun retry() {
        val item = routeItem ?: return
        state.value = TitleUiState(loading = true, actionsOrder = state.value.actionsOrder)
        load(item)
    }

    private fun load(item: CatalogItem) {
        viewModelScope.launch {
            // Збій мережі тут — не падіння застосунку, а сторінка «не відкрилась» із «Спробувати ще».
            val details = runCatching { catalog.details(item) }.getOrElse {
                state.value = TitleUiState(loading = false, message = context.getString(R.string.cinema_page_failed), actionsOrder = state.value.actionsOrder)
                return@launch
            }.let { d ->
                // TMDB без українського опису — беремо той, що був на картці ряду. Лише в
                // українському інтерфейсі: англійський опис для English — не брак, а мова.
                val ours = com.likhtar.home.core.catalog.PortalShowHints.overviewOf(d.item)
                val ukrainian = java.util.Locale.getDefault().language == "uk"
                if (ukrainian && ours != null && d.item.overview.none { it in 'а'..'я' }) d.copy(item = d.item.copy(overview = ours)) else d
            }
            state.value = TitleUiState(
                loading = false,
                details = details,
                inWatchlist = watchlist.contains(details.item),
                // Порядок кнопок прийшов потоком раніше за деталі — не втрачаємо його,
                // бо тут стан заміняється цілком, а не оновлюється.
                actionsOrder = state.value.actionsOrder,
                message = if (details.item.title.isBlank()) context.getString(R.string.cinema_tmdb_no_data) else null,
            )
            // Після заміни стану, а не до неї: тут він присвоюється цілком, і відповідь,
            // що встигла приїхати раніше, просто зникла б.
            loadUaAudio(item)
            loadVoices(details.item)
            launch {
                val mark = marks.markOf(details.item)
                state.update { it.copy(mark = mark) }
            }
            // Зовнішній плеєр не повертає нам таймкод, проте сервер знає серію, яку
            // треба продовжити. Цього достатньо для чесної мітки на картці.
            launch {
                val resume = progress.continueWatching().firstOrNull { it.id == details.item.id }
                state.update {
                    it.copy(
                        resumeSeason = resume?.season ?: it.resumeSeason,
                        resumeEpisode = resume?.nextEpisode ?: it.resumeEpisode,
                        resumeKnown = true,
                        resumeStarted = resume != null,
                    )
                }
                if (resume == null) return@launch
                // Сезон продовження показуємо одразу: тоді й спуск у серії відкриває його без
                // перемикання, і нижче є з чого взяти дату виходу.
                if (resume.season > 0) showSeason(resume.season)
                // Сервер рахує наступну серію за номером і не питає, чи вона вже вийшла.
                // Без цієї перевірки кнопка обіцяла б те, чого ще немає.
                val ahead = catalog.seasonEpisodes(details.item, resume.season)
                    .firstOrNull { it.number == resume.nextEpisode }
                    ?.airDate
                    ?.takeIf { it.isNotBlank() && it > today() }
                    .orEmpty()
                state.update { it.copy(resumeAirDate = ahead) }
            }
            // Відео-переказ минулого сезону — першим у «Трейлерах»: перед новим сезоном саме
            // його й шукають. Лише українською й перевірений сервером за назвою.
            val lastSeason = details.seasonNumbers.filter { it > 0 }.maxOrNull() ?: 0
            if (details.item.kind == TitleKind.TV && lastSeason > 1) launch {
                val recaps = runCatching { catalog.recapVideos(details.item.tmdbId, lastSeason - 1) }.getOrDefault(emptyList())
                if (recaps.isNotEmpty()) state.update { s ->
                    s.copy(
                        details = s.details?.let { d -> d.copy(videos = recaps + d.videos.filterNot { v -> recaps.any { it.key == v.key } }) },
                        recapVideos = s.recapVideos + (lastSeason - 1 to recaps.first()),
                    )
                }
            }
            // Український трейлер: у TMDB його часто немає — тоді шукає сервер. Знайдений стає
            // першим і в «Трейлерах», і для ↑ з першої сцени.
            if (details.videos.none { it.type == "Trailer" && it.language == "uk" }) launch {
                val ua = runCatching { catalog.uaTrailer(details.item.tmdbId, details.item.kind) }.getOrNull()
                if (ua != null) state.update { s ->
                    s.copy(details = s.details?.let { d -> d.copy(videos = listOf(ua) + d.videos.filterNot { it.key == ua.key }) })
                }
            }
            // Серіал одразу показує серії першого сезону — без окремого кліку.
            details.seasonNumbers.firstOrNull()?.let(::showSeason)
            // Опитування встановлених застосунків — окремою корутиною: картка не має
            // чекати на чужі ContentProvider'и, навіть із таймаутом.
            launch {
                val found = appSearch.find(details.item)
                if (found.isNotEmpty()) state.update { it.copy(appOptions = found) }
            }
        }
    }

    /**
     * Три способи відкрити, у порядку спадання точності: конкретна адреса → системний пошук
     * усередині застосунку → просто запуск. Той самий порядок, що й у `LaunchAction`.
     */
    fun open(option: WatchOption) {
        val action = option.launch ?: return
        val uri = action.uri
        val query = action.searchQuery
        val launched = when {
            uri != null -> apps.launchIntentUri(uri, action.packageName)
            query != null -> apps.searchInApp(action.packageName, query)
            else -> apps.launchPackage(action.packageName)
        }
        if (!launched) {
            state.update { it.copy(message = context.getString(R.string.cinema_open_failed_quoted, option.label)) }
        }
    }

    /**
     * «Ліхтарик» — одна кнопка на все: і «Почати», і «Продовжити», і OK на серії.
     *
     * Іде шляхом озвучок, а не списку потоків: список агрегатора знає лише роздачі, які
     * TorrServer уже має, а незнайому роздачу додає тільки `/api/play` з її ref. Озвучка — остання, якою людина вмикала; далі порядок сервера, тобто
     * від кращої якості. Інші озвучки й якості — уже в плеєрі (панель «Якість» Just+),
     * повний список — довгим натисканням.
     */
    fun watchBest() {
        val item = state.value.details?.item ?: return
        if (item.kind == TitleKind.TV && state.value.resumeAirDate.isNotBlank()) return
        val (season, episode) = if (item.kind == TitleKind.TV) impliedEpisode() else 0 to 0
        lantern(season, episode)
    }

    private fun lantern(season: Int, episode: Int) {
        val item = state.value.details?.item ?: return
        viewModelScope.launch {
            state.update { it.copy(preparing = true, message = null, preparingNote = context.getString(R.string.cinema_prep_best_option) to "") }
            val recent = history.voices(settings.settings.first().activeProfileId).first()
            // Чуже не вмикається само — те саме правило, що й для списку потоків.
            val all = runCatching { streams.voices(item, season, episode) }.getOrDefault(emptyList())
            // Українське першим; неігрове без нього вмикає й іншу мову.
            val groups = all.filter { it.lang.isBlank() || it.lang == "ukr" }
                .ifEmpty { if (catalog.isUnscripted(item)) all else emptyList() }
                .sortedBy { voiceRank(it.voice, recent) }
            val best = groups.firstOrNull()
            if (best == null) {
                state.update { it.copy(preparing = false) }
                if (item.kind == TitleKind.TV) playEpisode(season, episode) else playFromList()
                return@launch
            }
            state.update { it.copy(picker = PickerState(voices = groups, season = season, episode = episode)) }
            playVoice(best)
        }
    }

    /** Запасний шлях «Ліхтарика»: озвучок сервер не дав — найкращий потік зі списку. */
    private fun playFromList() {
        val item = state.value.details?.item ?: return
        viewModelScope.launch {
            state.update { it.copy(picker = PickerState(loading = true)) }
            val best = preferred(streams.streams(item, null)).firstOrNull()
            state.update { it.copy(picker = null) }
            if (best == null) {
                // Тайтла, якого сервер ще не бачив, раніше було нічим відкрити: `/api/watch`
                // відповідає лише з бази й на такому законно віддає `known:false`, а
                // сторінка впиралась у напис. Але «немає в каталозі» не означає «немає
                // джерел» — живий пошук за назвою в `/api/play` працює без tmdb_id.
                if (state.value.details?.scan?.known == false) {
                    searchByName()
                } else {
                    state.update { it.copy(message = noSourcesMessage()) }
                }
            } else if (!best.ukrainianAudio) {
                // Чужа доріжка не вмикається сама: людина натиснула «Дивитись», а не
                // «будь-що, що грає». Показуємо список, де мова кожного пункта підписана.
                showVoices(0, 0)
            } else {
                play(best)
            }
        }
    }

    /**
     * Пошук за назвою — останній шанс для тайтла, якого немає в каталозі сервера.
     *
     * Окремо від [watchBest], бо це інша обіцянка людині: не «вмикаю найкраще з відомого»,
     * а «шукаю зараз». Тому й напис під час очікування свій, і невдача тут не «джерел
     * немає», а те, що відповів сам сервер.
     */
    fun searchByName() {
        val item = state.value.details?.item ?: return
        state.update {
            it.copy(
                // Лишаємось у модалці, де людина натиснула: у неї вже є стан очікування
                // («Шукаємо джерела…»), і саме там вона чекає на відповідь. Закривати
                // модалку й показувати очікування поверх сторінки було помилкою: фокус
                // повертався на сторінку під оверлеєм, по картці можна було бігати, а
                // результату не було видно взагалі.
                // `title` у модалці обчислюваний, тож новий стан беремо як є: для фільму
                // це «Оберіть озвучку», а для серії — «S01E02 · озвучка».
                picker = (it.picker ?: PickerState()).copy(loading = true, note = ""),
                message = null,
            )
        }
        viewModelScope.launch {
            val (season, episode) = if (item.kind == TitleKind.TV) impliedEpisode() else 0 to 0
            // Межу «неігрове» називаємо самі: у пошуку за назвою id немає, а без неї в
            // реаліті приїхало б ігрове — те саме, від чого ми чистили ряди розділу.
            val unscripted = catalog.isUnscripted(item)
            val result = streams.playByName(item, season, episode, unscripted = unscripted)
            if (!result.ok) {
                // Відповідь показуємо в тій самій модалці, а не написом, якого не видно.
                state.update {
                    it.copy(
                        picker = it.picker?.copy(
                            loading = false,
                            note = result.reason.ifBlank { noSourcesMessage() },
                        ),
                    )
                }
                return@launch
            }
            // Знайдене не вмикаємо самі. Живий пошук збігається за назвою, і збіг буває
            // чужий — на документальному «Lion» приїхав арабський бойовик. Тому результат
            // стає таким самим пунктом вибору, як і решта озвучок: людина бачить, що саме
            // знайшлось, і вирішує сама. Позначаємо здогадом — це він і є.
            val ref = result.ref
            if (ref.isBlank()) {
                // Сервер не дав, чим повторити пошук — тоді лишається єдиний шлях, старий.
                state.update { it.copy(picker = null) }
                if (item.kind == TitleKind.TV && episode > 0) markEpisodeStarted(item, season, episode)
                play(
                    StreamOption(
                        label = result.name.ifBlank { context.getString(R.string.cinema_found_source) },
                        url = result.url,
                        season = season,
                        episode = episode,
                    ),
                    prepared = true,
                    ref = ref,
                    source = result.source,
                )
                return@launch
            }
            state.update {
                it.copy(
                    picker = (it.picker ?: PickerState()).copy(
                        loading = false,
                        season = season,
                        episode = episode,
                        note = context.getString(R.string.cinema_found_live_note),
                        voices = listOf(
                            VoiceGroup(
                                voice = result.name.ifBlank { context.getString(R.string.cinema_found_source) },
                                quality = result.quality,
                                hdr = "",
                                sources = 1,
                                refs = listOf(ref),
                                guess = true,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Чому нічого не вмикається — трьома різними відповідями, а не однією.
     *
     * `counts` із нулями сам по собі не означає «немає»: у тайтла, який сервер щойно взяв
     * у чергу, там такі самі нулі. Тому спираємось на `known` і `scanned`, які сервер
     * додав саме для цього.
     */
    private fun noSourcesMessage(): String {
        val scan = state.value.details?.scan
        return when {
            scan == null -> context.getString(R.string.cinema_no_sources)
            !scan.known -> context.getString(R.string.cinema_no_sources_unknown)
            !scan.checked -> context.getString(R.string.cinema_no_sources_unchecked)
            else -> context.getString(R.string.cinema_no_sources_checked)
        }
    }

    /** «Обрати» — відкриває селектор: фільм → джерела, серіал → сезони/серії. */
    /**
     * Сезон на сторінці серіалу: пігулки «Сезон N» + картки серій, як на Apple TV.
     *
     * Серії — з TMDB, а не з агрегатора: тут потрібні кадри, описи й дати, а агрегатор
     * знає лише номери. Потоки запитуються пізніше, коли натиснули конкретну серію.
     */
    /** «Переказ сезону» — текст пише Ліхтарик за описами серій; перший раз до хвилини. */
    fun openRecap(season: Int) {
        val item = state.value.details?.item ?: return
        if (season <= 0) return
        state.update { it.copy(recap = RecapState(season)) }
        viewModelScope.launch {
            val (text, error) = catalog.recap(item.tmdbId, season)
            state.update { s ->
                if (s.recap?.season != season) s else s.copy(recap = RecapState(season, loading = false, text = text, error = error.takeIf { text.isBlank() }.orEmpty()))
            }
        }
    }

    fun closeRecap() {
        state.update { it.copy(recap = null) }
    }

    fun showSeason(season: Int) {
        val item = state.value.details?.item ?: return
        viewModelScope.launch {
            state.update { it.copy(season = season, translated = emptySet()) }
            val episodes = catalog.seasonEpisodes(item, season)
            // Користувач міг клацнути далі, поки їхала відповідь — чужий сезон не показуємо.
            if (state.value.season == season) state.update { it.copy(episodes = episodes) }
        }
        // Відео-переказ попереднього сезону — у рядок «Що було…» над серіями.
        val before = state.value.copy(season = season).recapBefore
        if (item.kind == TitleKind.TV && before > 0 && before !in state.value.recapVideos) viewModelScope.launch {
            val video = runCatching { catalog.recapVideos(item.tmdbId, before) }.getOrDefault(emptyList()).firstOrNull()
            if (video != null) state.update { it.copy(recapVideos = it.recapVideos + (before to video)) }
        }
        // Мітки перекладу — окремою корутиною: агрегатор опитує чотири джерела, це секунди,
        // і перелік серій не має на них чекати. Заразом прогріває кеш потоків, тож натиснута
        // після цього серія стартує вже без паузи.
        viewModelScope.launch {
            val available = streams.streams(item, season)
                .filter { it.ukrainianAudio }
                .map { it.episode }
                .filter { it > 0 }
                .toSet()
            if (state.value.season == season) state.update { it.copy(translated = available) }
        }
    }

    /**
     * З якої серії стартує головна дія: продовжувана, інакше перша.
     *
     * Нульовий сезон пропускаємо навмисно — там спецвипуски, і пілота в ньому немає.
     * «Дивитись S00E01» замість першої серії читалося б як поломка.
     */
    fun impliedEpisode(): Pair<Int, Int> {
        val resumeSeason = state.value.resumeSeason
        val resumeEpisode = state.value.resumeEpisode
        if (resumeSeason > 0 && resumeEpisode > 0) return resumeSeason to resumeEpisode
        val first = state.value.details?.seasonNumbers?.firstOrNull { it > 0 } ?: 1
        return first to 1
    }

    /** OK на картці серії: одразу найкращий потік цієї серії, з тим самим прогрівом. */
    fun watchEpisode(episode: Episode) = lantern(state.value.season, episode.number)

    private fun playEpisode(season: Int, episode: Int) {
        val item = state.value.details?.item ?: return
        viewModelScope.launch {
            state.update { it.copy(preparing = true, message = null) }
            val found = preferred(
                streams.streams(item, season).filter { it.episode == episode || it.episode == 0 },
            )
            val best = found.firstOrNull()
            if (best == null) {
                state.update {
                    it.copy(preparing = false, message = context.getString(R.string.cinema_no_sources_episode, episode))
                }
                return@launch
            }
            state.update { it.copy(preparing = false) }
            // Те саме правило, що й для фільму: чуже — через список із підписаною мовою.
            if (!best.ukrainianAudio) {
                showVoices(season, episode)
                return@launch
            }
            markEpisodeStarted(item, season, episode)
            play(best, seasonPlaylist(item, season, episode))
        }
    }

    /**
     * Українська доріжка завжди має пріоритет; історія джерел працює лише всередині неї
     * або всередині fallback. Так автозапуск не може вибрати чужу мову лише тому, що її
     * хост відкривали останнім.
     */
    private suspend fun preferred(options: List<StreamOption>): List<StreamOption> {
        val profile = settings.settings.first().activeProfileId
        return prioritizePlaybackOptions(
            options = options,
            recentSources = history.sources(profile).first(),
            recentVoices = history.voices(profile).first(),
        )
    }

    fun openPicker(studio: String = "") {
        val item = state.value.details?.item ?: return
        val serial = item.kind == TitleKind.TV
        val (season, episode) = if (serial) impliedEpisode() else 0 to 0
        showVoices(season, episode, studio)
    }

    /** Список озвучок цієї серії — і з кнопки «Озвучки», і замість автозапуску чужої мови. */
    private fun showVoices(season: Int, episode: Int, studio: String = "") {
        val item = state.value.details?.item ?: return
        viewModelScope.launch {
            state.update { it.copy(preparing = false, picker = PickerState(loading = true)) }
            // Зверху — та озвучка, якою вмикали востаннє: у списку з двох десятків
            // рядків шукати свою студію щоразу заново — це те саме, що не мати вибору.
            val recent = history.voices(settings.settings.first().activeProfileId).first()
            val found = streams.voices(item, season, episode)
                .sortedBy { voiceRank(it.voice, recent) }
            // Кнопка студії показує ЛИШЕ її озвучку — саме по це її й натискають. Якщо
            // з цієї серії в неї нічого немає, лишаємо повний список: кнопка, яка веде
            // в порожнечу, гірша за кнопку, яка показала сусідні варіанти.
            val shown = if (studio.isBlank()) {
                found
            } else {
                found.filter { matchesStudio(it.voice, studio) }.ifEmpty { found }
            }
            state.update {
                it.copy(picker = PickerState(voices = shown, season = season, episode = episode))
            }
        }
    }

    /** Озвучки тієї серії, яку селектор і відкривав. */
    fun visibleVoices(): List<VoiceGroup> = state.value.picker?.voices.orEmpty()

    /**
     * Обрана озвучка: сервер сам перебирає її джерела й віддає готовий потік.
     *
     * Прогріву тут не робимо — `/api/play` уже дочекався даних, тому [play] з готовим
     * url обходиться без `prepare`.
     */
    fun playVoice(group: VoiceGroup) {
        val picker = state.value.picker ?: return
        val item = state.value.details?.item ?: return
        state.update {
            it.copy(
                picker = null,
                preparing = true,
                message = null,
                preparingNote = context.getString(R.string.cinema_prep_voice, group.voice) to
                    (group.names[group.refs.first()]?.let(::cleanStreamLabel) ?: context.getString(R.string.cinema_prep_voice_sources)),
                preparingStat = null,
            )
        }
        val watch = watchTorrent(group.refs.filter { torrentLike(it, group.names[it]) })
        viewModelScope.launch {
            // Запам'ятовуємо ДО запуску: людина вже зробила вибір, і навіть якщо роздача
            // не стартує, наступного разу цю студію треба показати першою.
            history.rememberVoice(group.voice, settings.settings.first().activeProfileId)
            prefs.remember("voice", group.voice)
            // Сервер перебирає роздачі по черзі — перша й увімкнеться. За рівної якості без
            // DV першою, поки DV на цій приставці не ввімкнений (bestFirstKey).
            val dvFirst = com.likhtar.home.core.catalog.DolbyVision.supported
            val refs = group.refs.sortedByDescending { bestFirstKey(group.names[it].orEmpty(), dvFirst) }
            val result = streams.playVoice(item, refs, picker.season, picker.episode)
            watch.cancel()
            state.update { it.copy(preparing = false, preparingStat = null) }
            if (!result.ok) {
                state.update {
                    it.copy(
                        // Причину віддає сервер, і вона змістовна: «роздача гріється» —
                        // це «спробуйте за хвилину», а не «джерело мертве».
                        message = result.reason.ifBlank { context.getString(R.string.cinema_voice_failed) },
                    )
                }
                return@launch
            }
            if (item.kind == TitleKind.TV && picker.episode > 0) {
                markEpisodeStarted(item, picker.season, picker.episode)
            }
            play(
                StreamOption(
                    label = result.name.ifBlank { group.label },
                    url = result.url,
                    season = picker.season,
                    episode = picker.episode,
                ),
                // Решта сезону тією ж роздачею: Vimu отримає m3u, Just+ — чергу серій.
                playlist = if (picker.episode > 0) seasonPlaylist(item, picker.season, picker.episode, result.ref) else null,
                prepared = true,
                ref = result.ref,
                source = result.source,
                subs = result.subs,
            )
        }
    }

    /**
     * Варіанти для перемикання в плеєрі: поточний першим, далі решта джерел серії.
     * Підпис починається з якості — Just+ сортує за числом у ньому, тож 4K стоїть угорі.
     */
    private suspend fun variantsFor(
        item: CatalogItem,
        season: Int,
        episode: Int,
        current: StreamOption,
        preloaded: List<StreamOption>? = null,
    ): List<Pair<String, String>> {
        val all = (preloaded ?: seasonStreams(item, season)).filter { episode == 0 || it.episode == episode }

        // Той самий підпис, що з «Продовжити»: чия озвучка й звідки (variantLabel).
        val label = { o: StreamOption -> com.likhtar.home.core.catalog.variantLabel(o.label) }
        // Роздачі, яких TorrServer ще не знає (4K-ремукс, що ніхто не вмикав), у списку
        // агрегатора відсутні — лише в озвучках (LikhtarStreams.coldTorrents).
        val known = all.mapNotNull { torrentRefOf(it.url) }.toSet() + listOfNotNull(torrentRefOf(current.url))
        val cold = if (episode == 0) streams.coldTorrents(item, known) else emptyList()
        val dvFirst = com.likhtar.home.core.catalog.DolbyVision.supported
        val rest = (cold + all.filter { it.url != current.url }).distinctBy { it.url }
            .sortedByDescending { bestFirstKey(it.label, dvFirst) }
        if (rest.isEmpty()) return emptyList()
        return (listOf(current) + rest).take(MAX_VARIANTS).map { label(it) to it.url }
    }

    /** Усі джерела сезону (для фільму — самого фільму) з агрегатора; сторінка їх уже питала. */
    private suspend fun seasonStreams(item: CatalogItem, season: Int): List<StreamOption> =
        runCatching { streams.streams(item, season.takeIf { it > 0 }) }.getOrDefault(emptyList())
            .filter { it.url.startsWith("http") }

    /** Плейлист «від цієї серії до кінця сезону» — тільки для серіалу. */
    private suspend fun seasonPlaylist(item: CatalogItem, season: Int, episode: Int, ref: String = ""): String? =
        if (item.kind != TitleKind.TV) null
        else streams.playlistUrl(item.title, item.originalTitle, item.year, season, episode, ref, tmdbId = item.tmdbId)

    /**
     * Попередню серію закриваємо саме тут: вихід із плеєра нам ніхто не повідомляє,
     * а запуск наступної — єдиний надійний сигнал, що попередню додивились.
     */
    private suspend fun markEpisodeStarted(item: CatalogItem, season: Int, episode: Int) {
        if (episode > 1) {
            progress.finished(item, season, episode - 1)
        } else if (season > 1) {
            // Перша серія нового сезону: «попередня» — остання серія минулого сезону,
            // її номер питаємо в TMDB.
            // ponytail: якщо TMDB не відповів, остання серія минулого сезону лишається
            // незакритою — закриється, коли сервер порахує продовження сам.
            val last = runCatching { catalog.seasonEpisodes(item, season - 1) }
                .getOrDefault(emptyList())
                .maxOfOrNull { it.number }
            if (last != null && last > 0) progress.finished(item, season - 1, last)
        }
        progress.started(item, season, episode)
        state.update { it.copy(resumeSeason = season, resumeEpisode = episode, resumeKnown = true) }
    }

    /**
     * Раз на секунду — стан роздач, поки сервер їх готує: людина бачить пірів, швидкість і
     * смугу підвантаження, а не лише ліхтар. Серед кількох варіантів показуємо той, що вже
     * підвантажується, інакше — з найбільшою кількістю пірів.
     */
    private fun watchTorrent(refs: List<String>): Job = viewModelScope.launch {
        if (refs.isEmpty()) return@launch
        while (isActive) {
            val best = refs.mapNotNull { streams.torrentStat(it) }
                .maxByOrNull { (it.bufferShare ?: 0f) * 10_000 + it.peers }
            if (best != null) state.update { it.copy(preparingStat = best) }
            delay(STAT_POLL_MS)
        }
    }

    fun closePicker() = state.update { it.copy(picker = null) }

    fun back() = closePicker()

    /**
     * Плеєр беремо з налаштувань, а не в системи.
     *
     * Порожнє налаштування — показуємо вибір щоразу. Раніше тут був простий `ACTION_VIEW`
     * без пакета «хай вирішує система»: система вирішила один раз, запам'ятала Just Player,
     * і на приставці вибору більше не пропонували.
     */
    /**
     * [playlist] — адреса `playlist.m3u` замість одиночного потоку: плеєр бачить хвіст
     * сезону й сам вмикає наступну серію. Ставимо її лише там, де джерело обирали МИ;
     * коли людина вибрала конкретний потік руками, плейлист її вибір би скасував —
     * доріжка в ньому одна на весь сезон і її обирає сервер.
     */
    fun play(
        stream: StreamOption,
        playlist: String? = null,
        prepared: Boolean = false,
        ref: String = "",
        source: String = "",
        subs: List<Pair<String, String>> = emptyList(),
    ) {
        viewModelScope.launch {
            // Спершу прогрів: TorrServer починає качати лише коли хтось читає файл, тому
            // відданий одразу торент-лінк перші секунди «висить» у плеєрі. Для HLS сервер
            // відповідає миттєво (kind:"direct"), тож розрізняти типи тут не треба.
            //
            // `prepared` — потік уже прийшов з `/api/play`, який сам дочекався даних:
            // гріти вдруге означало б чекати те, що вже готове.
            val ownTorrent = streams.ownTorrentServer() != null && torrentRefOf(stream.url) != null
            val readiness = if (ownTorrent) {
                // Свій TorrServer: сервер уже обрав роздачу, а гріти її треба там, звідки
                // гратиме плеєр. Цифри в «Готую» — теж звідти (torrentStat питає його).
                state.update {
                    it.copy(
                        preparing = true, message = null, preparingStat = null,
                        preparingNote = (it.preparingNote?.first ?: context.getString(R.string.cinema_prep_connecting)) to cleanStreamLabel(stream.label),
                    )
                }
                val watch = watchTorrent(listOfNotNull(torrentRefOf(stream.url)))
                val warmed = streams.warmOnOwn(stream.url)
                watch.cancel()
                state.update { s -> s.copy(preparing = false, preparingStat = null) }
                if (warmed) LikhtarStreams.Readiness.READY else LikhtarStreams.Readiness.UNKNOWN
            } else if (prepared) {
                LikhtarStreams.Readiness.READY
            } else {
                state.update {
                    it.copy(
                        preparing = true, message = null, preparingStat = null,
                        preparingNote = (it.preparingNote?.first ?: context.getString(R.string.cinema_prep_connecting)) to cleanStreamLabel(stream.label),
                    )
                }
                val watch = watchTorrent(listOfNotNull(torrentRefOf(stream.url)))
                streams.prepare(stream.url).also {
                    watch.cancel()
                    state.update { s -> s.copy(preparing = false, preparingStat = null) }
                }
            }

            if (readiness == LikhtarStreams.Readiness.DEAD) {
                // Плеєр на мертвій роздачі виглядає як застряглий лаунчер — не запускаємо.
                state.update {
                    it.copy(message = context.getString(R.string.cinema_torrent_dead))
                }
                return@launch
            }

            // Джерело запамʼятовуємо на старті, а не на успішному перегляді: до кінця фільму
            // може пройти година, а вибір зроблено вже зараз.
            history.rememberSource(stream.source, settings.settings.first().activeProfileId)
            prefs.remember("source", stream.source)

            val player = settings.settings.first().videoPlayerPackage
            val item = state.value.details?.item
            // Серіал грає конкретну серію, фільм — сам себе: у нього сезон і серія нульові,
            // і саме під цим ключем лежить його таймкод.
            val isSeries = item?.kind == TitleKind.TV
            val season = if (isSeries) state.value.resumeSeason else 0
            val episode = if (isSeries) state.value.resumeEpisode else 0
            // Продовжуємо з таймкоду цієї ж серії, якщо він у нас є.
            val resumeMs = item?.let {
                progress.positionOf(it.tmdbId, it.kind, season, episode)
            } ?: 0L
            // Черга серій для плеєрів без m3u: поточна серія — рівно тим потоком, що
            // перевірили, бо Just+ шукає стартову серед черги дослівним збігом адреси.
            val playlistQueue = playlist?.let { streams.playlistEntries(it) }.orEmpty().map { entry ->
                if (entry.episode == episode && episode > 0) entry.copy(url = stream.url) else entry
            }
            // Усі джерела цієї серії — у плеєр: Just+ перемикає озвучку й якість, не
            // виходячи з перегляду. Список агрегатора вже в кеші — сторінка його питала.
            val all = item?.let { seasonStreams(it, season) }.orEmpty()
            val variants = item?.let { variantsFor(it, season, episode, stream, all) }.orEmpty()
            // Наступні серії — з того самого джерела, що й стартова: плейлист сезону сервер
            // збирає з одного джерела (одного сайту), і після першої серії з іншого плеєр щоразу
            // перескакував на нього. Немає серії на цьому хості — як було.
            val queue = playlistQueue.map { e ->
                if (e.url == stream.url || stream.source.isBlank()) e
                else all.firstOrNull { it.episode == e.episode && it.source == stream.source }
                    ?.let { e.copy(url = it.url) } ?: e
            }
            // І для кожної наступної серії черги — інакше після переходу на другу серію
            // панель «Якість» у Just+ порожня: ні інших озвучок, ні інших роздач.
            val queueVariants = if (item != null && queue.size > 1) {
                queue.map { e ->
                    if (e.url == stream.url) variants
                    else variantsFor(item, season, e.episode, StreamOption(label = e.name, url = e.url, season = season, episode = e.episode), all)
                }
            } else {
                emptyList()
            }
            val launched = playback.play(
                PlayRequest(
                    uri = playlist ?: stream.url,
                    single = stream.url,
                    queue = queue,
                    variants = variants,
                    queueVariants = queueVariants,
                    mime = if (playlist != null) "audio/x-mpegurl" else "video/*",
                    packageName = player,
                    ref = ref,
                    source = source,
                    // Єдине, що відомо на ВСІХ трьох шляхах: «Дивитись» бере потік прямо
                    // з агрегатора, і `ref` там немає за побудовою, а якість є в підписі.
                    quality = stream.quality,
                    subs = subs,
                    resumeMs = resumeMs,
                    title = item?.let { PlayingTitle(item = it, season = season, episode = episode) },
                ),
            )
            if (!launched) {
                state.update { it.copy(message = context.getString(R.string.cinema_player_open_failed)) }
                return@launch
            }
            // Фільм позначаємо переглянутим не одразу: людина могла передумати за хвилину.
            // Мітку ставимо при поверненні в лаунчер, якщо плеєр працював досить довго.
            state.value.details?.item?.let { item ->
                if (item.kind == TitleKind.MOVIE) {
                    progress.started(item, season = 0, episode = 0)
                    movieStartedAt = System.currentTimeMillis()
                }
            }
            item?.let {
                watchPlayerReads(it, playlist ?: stream.url, player, ref, source, stream.quality)
            }
        }
    }


    /**
     * Чи взагалі відкрив плеєр той потік, який ми йому дали.
     *
     * Навіщо:  на 2160p-ремуксі, що ViMu не читає потік ЗОВСІМ, при цьому
     * звітує `state=PLAYING`, а `prepare` каже «ready». Людина бачить чорний екран зі
     * спінером і жодного слова. Ні плеєр, ні наша перевірка готовності про це не скажуть —
     * скаже лише сервер, який бачить список читачів TorrServer.
     *
     * Вердикт лише на `readers:0`. Живий читач без руху — законний стан плеєра, що набрав
     * буфер уперед, тому там перепитуємо, і рахуємо тільки повторний нуль.
     */
    private fun watchPlayerReads(
        item: CatalogItem,
        streamUrl: String,
        player: String,
        ref: String,
        source: String,
        quality: Int,
    ) {
        viewModelScope.launch {
            val first = streams.reading(streamUrl)
            android.util.Log.i(
                "LikhtarPlayback",
                "читання: reading=${first.reading} readers=${first.readers} " +
                    "moved=${first.moved} why=${first.why}",
            )
            // Слабкий сигнал перепитуємо один раз — правило живе в `playerIsSilent`.
            val second = if (first.doubtful) streams.reading(streamUrl) else null
            if (!playerIsSilent(first, second)) return@launch
            val verdict = second ?: first

            val label = player.ifBlank { context.getString(R.string.cinema_player) }
            // Припущення, а не діагноз: `readers:0` сигнал сильний, але сама проба на
            // пристрої поки не відпрацювала жодного разу. Поки так, людині кажемо, що
            // бачимо, і лишаємо їй судити — «не тягне» звучало б як вирок від імені
            // перевірки, якої ми ще не бачили в роботі.
            state.update {
                it.copy(
                    message = context.getString(R.string.cinema_player_silent, label),
                )
            }
            // `played_ms` тут нуль навмисно: подія саме в тому, що не зіграло нічого.
            // Без цих випадків сервер не має з чого впорядкувати плеєри за фактом.
            progress.reportAttempt(
                item = item,
                playedMs = 0,
                result = "no_readers player=${player.ifBlank { "system_choice" }} " +
                    "stream=${streamUrl.substringAfterLast('/').take(80)} ${verdict.why}",
                player = player,
                ref = ref,
                source = source,
                quality = quality,
            )
        }
    }

    /**
     * Повернулись у лаунчер після фільму.
     *
     * Порогом у пів години відсікаємо «запустив і передумав»: позначати переглянутим те,
     * що показували тридцять секунд, гірше, ніж не позначати нічого.
     */
    init {
        // Плеєр закрився й прогрес записано: серіал перечитує, з якої серії продовжувати, —
        // тоді й позначки серій на сторінці правдиві.
        viewModelScope.launch {
            playback.saved.collect {
                val item = state.value.details?.item ?: return@collect
                if (item.kind == TitleKind.TV) refreshResume(item)
            }
        }
    }

    fun onReturnedFromPlayer() {
        val startedAt = movieStartedAt ?: return
        movieStartedAt = null
        val item = state.value.details?.item ?: return
        if (System.currentTimeMillis() - startedAt < MOVIE_WATCHED_MS) return
        viewModelScope.launch { progress.finished(item, season = 0, episode = 0) }
    }

    /** Склад франшизи вантажимо окремо: у деталях TMDB лежить лише назва колекції. */
    fun loadFranchise(id: Int) {
        if (state.value.franchise.isNotEmpty()) return
        viewModelScope.launch {
            val parts = runCatching { catalog.franchiseParts(id) }.getOrDefault(emptyList())
            state.update { it.copy(franchise = parts) }
        }
    }

    /** Ролик відкриваємо в YouTube: власного програвача для нього ми не тримаємо. */
    /** Повідомлення показано (Notice) — прибрати, щоб те саме могло прийти знову. */
    fun clearMessage() = state.update { it.copy(message = null) }

    fun openVideo(video: TitleVideo) {
        if (!apps.launchIntentUri(video.watchUrl)) {
            state.update { it.copy(message = context.getString(R.string.cinema_no_app_for_video)) }
        }
    }

    fun openMarkPanel() = state.update { it.copy(markPanel = true) }

    fun closeMarkPanel() = state.update { it.copy(markPanel = false) }

    /** Позначка з панелі «Позначити»; прогрес і ряди сервер перебудовує сам. */
    fun applyMark(mark: String, scope: String = "", reason: String = "", rating: String = "", season: Int = 0, episode: Int = 0) {
        val item = state.value.details?.item ?: return
        state.update { it.copy(markPanel = false) }
        viewModelScope.launch {
            val saved = marks.setMark(item, mark, scope, reason, rating, season, episode)
            if (saved == null) {
                state.update { it.copy(message = context.getString(R.string.cinema_server_no_response)) }
                return@launch
            }
            state.update { it.copy(mark = saved, message = markMessage(context, saved)) }
            refreshResume(item)
        }
    }

    fun clearMark() {
        val item = state.value.details?.item ?: return
        state.update { it.copy(markPanel = false) }
        viewModelScope.launch {
            if (!marks.clearMark(item)) {
                state.update { it.copy(message = context.getString(R.string.cinema_server_no_response)) }
                return@launch
            }
            state.update { it.copy(mark = null, message = context.getString(R.string.cinema_mark_cleared)) }
            refreshResume(item)
        }
    }

    /** Після позначки головна кнопка має казати правду: «Продовжити» вже з іншої серії. */
    private suspend fun refreshResume(item: CatalogItem) {
        val resume = runCatching { progress.continueWatching() }.getOrNull()?.firstOrNull { it.id == item.id }
        state.update {
            it.copy(
                resumeSeason = resume?.season ?: 0,
                resumeEpisode = resume?.nextEpisode ?: 0,
                resumeStarted = resume != null,
                resumeAirDate = "",
            )
        }
    }

    /** «Чекаю українську»: увімкнути або зняти очікування. */
    fun toggleUaWait() {
        val item = state.value.details?.item ?: return
        val on = (state.value.uaWaitSince ?: 0L) <= 0L
        viewModelScope.launch {
            if (!watchlist.setUaWait(item, on)) {
                state.update { it.copy(message = context.getString(R.string.cinema_server_no_response)) }
                return@launch
            }
            state.update {
                it.copy(
                    uaWaitSince = if (on) System.currentTimeMillis() / 1000 else 0L,
                    inWatchlist = it.inWatchlist || on,
                    message = if (on) context.getString(R.string.cinema_ua_wait_on) else context.getString(R.string.cinema_ua_wait_off),
                )
            }
        }
    }

    fun toggleWatchlist() {
        val item = state.value.details?.item ?: return
        viewModelScope.launch {
            val added = watchlist.toggle(item)
            state.update {
                it.copy(
                    inWatchlist = added,
                    message = if (added) context.getString(R.string.cinema_watchlist_added) else context.getString(R.string.cinema_watchlist_removed),
                )
            }
        }
    }



    /** Сьогодні в тому ж вигляді, що й `airDate` з TMDB: ISO-рядки порівнюються як текст. */
    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private companion object {
        /** Скільки озвучок у шапці: далі — меню вибору. */
        const val VOICES_SHOWN = 5

        /** Пів години в плеєрі — вважаємо, що фільм таки дивились. */
        const val MOVIE_WATCHED_MS = 30 * 60 * 1000L
    }
}

/** Чиста частина порядку джерел — перевіряємо її без Hilt, мережі й плеєра. */
internal fun prioritizePlaybackOptions(
    options: List<StreamOption>,
    recentSources: List<String>,
    recentVoices: List<String> = emptyList(),
): List<StreamOption> = options.sortedWith(
    compareByDescending<StreamOption> { it.ukrainianAudio }
        // Озвучка — раніше за джерело, і це свідомо: хто звик до однієї студії, тому
        // байдуже, з торента вона прийде чи з балансера. Зворотне впорядкування —
        // «той самий сайт, але чужий голос» — і було тим, від чого людина щоразу лізла
        // в список руками.
        .thenBy { option -> voiceRank(option.label, recentVoices) }
        .thenBy { option ->
            recentSources.indexOf(option.source).takeIf { it >= 0 } ?: recentSources.size
        },
)

/**
 * Наскільки свіжа ця озвучка в пам'яті: 0 — вмикали останньою.
 *
 * Структурного поля озвучки в пункті немає — сервер віддає готовий підпис («🇺🇦 1080p ·
 * Дубляж · Торент»), і студія в ньому лише текстом. Тому шукаємо входження, і саме тим
 * порівнянням, що терпить різні написання назви.
 */
internal fun voiceRank(label: String, recentVoices: List<String>): Int {
    val hit = recentVoices.indexOfFirst { matchesStudio(label, it) }
    return if (hit >= 0) hit else recentVoices.size
}

/**
 * Роздавачі пишуть назву студії як заманеться: у базі поруч лежать «DniproFilm»,
 * «Dniprofilm» і «DniproFlm». Порівнюємо без голосних і без розділових — пропущена
 * літера більше не ховає доріжку, заради якої кнопку й натиснули.
 */
internal fun matchesStudio(voice: String, studio: String): Boolean {
    fun key(s: String) = s.lowercase().filter { it in 'a'..'z' || it in 'а'..'я' }
        .filter { it !in "aeiouyаеєиіїоуюя" }
    val k = key(studio)
    return k.isNotEmpty() && key(voice).contains(k)
}

private const val STAT_POLL_MS = 1_000L

/** Скільки варіантів класти в панель якості плеєра. */
private const val MAX_VARIANTS = 12

/** Роздача за ref (infohash) чи за підписом пункта («… · Торент»). */
private fun torrentLike(ref: String, name: String?): Boolean =
    Regex("[0-9a-fA-F]{40}").matches(ref) || name?.contains("Торент") == true

private fun markMessage(context: android.content.Context, m: TitleMark): String = context.getString(
    when {
        m.mark == "watched" && m.rating == "like" -> R.string.cinema_mark_msg_liked
        m.mark == "watched" && m.rating == "dislike" -> R.string.cinema_mark_msg_disliked
        m.mark == "watched" -> R.string.cinema_mark_msg_watched
        m.mark == "skip" && m.scope == "later" -> R.string.cinema_mark_postponed_month
        m.mark == "skip" && m.scope == "season" -> R.string.cinema_mark_msg_season_skipped
        m.mark == "skip" -> R.string.cinema_mark_episode_skipped
        m.reason == "seen" -> R.string.cinema_mark_msg_seen
        m.reason == "genre" -> R.string.cinema_mark_msg_genre
        m.reason == "franchise" -> R.string.cinema_mark_msg_franchise
        else -> R.string.cinema_mark_msg_hidden
    },
)
