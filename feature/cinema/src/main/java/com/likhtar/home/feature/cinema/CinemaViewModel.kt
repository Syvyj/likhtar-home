package com.likhtar.home.feature.cinema

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.TitleMarksRepository
import com.likhtar.home.core.catalog.SectionBrand
import com.likhtar.home.core.catalog.SectionGenre
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.CatalogRow
import com.likhtar.home.core.catalog.UaAudioRepository
import com.likhtar.home.core.catalog.CatalogStudio
import com.likhtar.home.core.catalog.FreshUaRepository
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.openIn
import com.likhtar.home.core.catalog.SectionRow
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.WatchProgressRepository
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.catalog.WatchlistRepository
import com.likhtar.home.core.catalog.ResumeRows
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.likhtar.home.core.catalog.Candidate
import com.likhtar.home.core.catalog.WatchCollection
import com.likhtar.home.core.catalog.WatchMood
import com.likhtar.home.core.catalog.WatchTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CinemaUiState(
    val loading: Boolean = true,
    val configured: Boolean = true,
    val resume: List<ResumeCard> = emptyList(),
    val watchlist: List<CatalogItem> = emptyList(),
    /** Що є у своїй медіатеці (DLNA/SMB), уже зіставлене з TMDB. */
    val local: List<CatalogItem> = emptyList(),
    /** Файли медіатеки поза TMDB — окремим рядом, OK одразу в плеєр. */
    val localOther: List<com.likhtar.home.core.catalog.media.LocalFile> = emptyList(),
    /** Плитки сервісів — єдиний вхід у куровану частину каталогу. */
    val studios: List<CatalogStudio> = emptyList(),
    /** [CatalogItem.id] того, що профіль уже додивився. */
    val watched: Set<String> = emptySet(),
    /** Прибирати переглянуте з рядів, а не лише позначати галочкою. */
    val hideWatched: Boolean = false,
    /** Ряд «Аніме» — лише з перемикачем у налаштуваннях. */
    val showAnime: Boolean = false,
    /**
     * Ряди Кінозалу в тому порядку, у якому їх дав сервер.
     *
     * Порядок екрана більше не живе в enum на клієнті: після слайдера йдуть «Новинки
     * кіно та серіалів», «Українська стрічка» і по ряду на кожен стрімінг. Це той самий
     * порядок, що бачитиме будь-яка інша інсталяція.
     */
    val rows: List<SectionRow> = emptyList(),
    /** Ряд «Реаліті та шоу» усередині Кінозалу; «Відкрити всі» веде на повну сторінку Реаліті. */
    val realityItems: List<CatalogItem> = emptyList(),
    /** Свій порядок і сховані ряди з налаштувань (ключі SectionRow.key). */
    val rowOrder: List<String> = emptyList(),
    val hiddenRows: Set<String> = emptySet(),
    /** «Топ-10» випадкового сервісу (прототип); `null` — ще не приїхав або немає. */
    val top: SectionRow? = null,
    /** Плитки стрімінгів у кінці розділу: двері на сторінку сервісу. */
    val brands: List<SectionBrand> = emptyList(),
    /** «Жанри», як в Apple TV: ряд плиток між рядами. */
    val genres: List<SectionGenre> = emptyList(),
    /** Позиції, які тримаються на російській доріжці: мітка на картці інша. */
    val russianAudio: Set<Int> = emptySet(),
    /** Пул слайдера — серверний ряд «Слайдер». */
    val trendingPool: List<CatalogItem> = emptyList(),
    /**
     * Тайтли слайдера, у яких ми САМІ бачили українську доріжку.
     *
     * Множина, а не прапорець на тайтлі: відсутність id означає «не знаємо», і мітки тоді
     * просто немає. Питаємо пакетно, одним запитом на увесь пул.
     */
    val uaAudio: Set<Int> = emptySet(),
    /** Куди їде кадр на зміні слайда: вперед — новий заходить справа, назад — зліва. */
    val slideForward: Boolean = true,
    /**
     * Фокус пішов у ряди — верх згортається, ряди займають екран.
     *
     * Окремо від [CinemaViewModel.railItem], бо в рядах є картки, за якими немає тайтла TMDB: радник і
     * стрічки застосунків. Раніше режим визначався саме наявністю тайтла, і на цих картках
     * сторінка лишалась із розгорнутим героєм, а ряд тулився в залишку екрана.
     */
    val inRows: Boolean = false,
    /** Що показує превʼю ліворуч: сфокусована картка, підбір радника або слайд. */
    val focused: CatalogItem? = null,
    /** Поточний слайд, коли ніхто нічого не вибрав: новинки, топ, рекомендації. */
    val slide: CatalogItem? = null,
    /** На героєві — питання «Правда чи брехня» (слайд [CinemaViewModel.TRIVIA_AT]), а не тайтл. */
    val triviaOn: Boolean = false,
    val message: String? = null,
    /** Картка, на якій довге OK відкрило «Позначити», і її поточна позначка. */
)

/**
 * Кінозал.
 *
 * На екрані лише те, що про користувача: «Продовжити перегляд» і його власний список.
 * Курація Ліхтаря звідси прибрана — до неї веде єдиний рядок плиток сервісів, а самі
 * рядки живуть на їхніх сторінках.
 */
@HiltViewModel
class CinemaViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val catalog: CatalogRepository,
    private val watchProgress: WatchProgressRepository,
    private val watchlist: WatchlistRepository,
    private val prefs: com.likhtar.home.core.catalog.ProfilePrefsRepository,
    private val settings: SettingsRepository,
    private val uaAudio: UaAudioRepository,
    private val freshUa: FreshUaRepository,
    private val resumeRows: ResumeRows,
    private val apps: AppsRepository,
    private val marks: TitleMarksRepository,
    private val continuePlayback: com.likhtar.home.core.catalog.ContinuePlayback,
    private val media: com.likhtar.home.core.catalog.media.MediaLibrary,
    private val playback: com.likhtar.home.core.catalog.PlaybackController,
    private val realityRows: RealityRows,
    trivia: com.likhtar.home.core.catalog.TriviaRepository,
) : ViewModel() {

    /** Випадкове питання гри — другим слайдом героя; раунд дня живе в Ліхтарику. */
    val game = TriviaGame(trivia, viewModelScope, withRound = false)

    /** Картка «Продовжити» шукає джерело — PreparingScrim. */
    val preparing = continuePlayback.preparing

    private val state = MutableStateFlow(CinemaUiState())
    val uiState: StateFlow<CinemaUiState> = state.asStateFlow()

    private var slideIndex = 0
    private var triviaInPool = false

    // До init: той викликає restart() одразу.
    private val ticker = SlideTicker(
        viewModelScope,
        // На питанні слайдер стоїть: людина читає й думає, а не дивиться трейлер.
        paused = { state.value.let { it.inRows || it.triviaOn } },
    ) { step(+1, manual = false) }

    /** Файл поза TMDB — у плеєр із назвою файлу; прогрес не пишемо: прив'язати нема до чого. */
    fun playLocalFile(file: com.likhtar.home.core.catalog.media.LocalFile) {
        viewModelScope.launch {
            val url = media.playUrl(file) ?: return@launch
            playback.play(
                com.likhtar.home.core.catalog.PlayRequest(
                    uri = url,
                    mime = "video/*",
                    packageName = settings.settings.first().videoPlayerPackage,
                    resumeMs = 0L,
                    title = null,
                    source = com.likhtar.home.core.catalog.PlaybackController.LOCAL_SOURCE,
                    displayName = file.name.substringBeforeLast('.'),
                ),
            )
        }
    }

    init {
        game.load()
        viewModelScope.launch {
            // D2: ряд Реаліті тут перший і часто єдиний живий споживач знімка Реаліті — свіже
            // слухаємо самі, інакше ряд тиждень показував би знімок з диска.
            realityRows.fresh.collect { if (it == "reality") { realityRows.invalidate(); loadReality() } }
        }
        viewModelScope.launch {
            // Нове питання («Ще питання») — той самий слайд, лише з іншим кадром.
            game.state.collect { g ->
                val pool = state.value.trendingPool
                if (pool.size <= TRIVIA_AT || g.shown == null) return@collect
                val item = triviaItem(g.shown)
                state.update {
                    val p = it.trendingPool.toMutableList().also { list -> if (triviaInPool) list[TRIVIA_AT] = item else list.add(TRIVIA_AT, item) }
                    triviaInPool = true
                    it.copy(trendingPool = p, slide = if (it.triviaOn) item else it.slide)
                }
            }
        }
        viewModelScope.launch {
            media.state.collect { lib -> state.update { it.copy(local = lib.titles.map { t -> t.item }, localOther = lib.other) } }
        }
        refresh()
        loadRows()
        viewModelScope.launch {
            // Прапорець потоком: перемкнули в налаштуваннях — ряди перебудовуються без
            // повторного заходу в Кінозал.
            settings.settings.collect { current ->
                state.update {
                    it.copy(
                        hideWatched = current.hideWatched,
                        showAnime = current.showAnime,
                        rowOrder = current.cinemaRowOrder,
                        hiddenRows = current.hiddenCinemaRows,
                    )
                }
            }
        }
        viewModelScope.launch {
            // Дитячий режим міняє склад рядів (фільтр у CatalogRepository), профіль — «Продовжити»,
            // закладки й поради: перезбираємо одразу.
            settings.settings.map { it.kidsMode to it.activeProfileId }.distinctUntilChanged().drop(1).collect { loadRows() }
        }
        viewModelScope.launch {
            // D2: перший кадр був зі знімка — свіжі ряди приїхали, перемальовуємо.
            catalog.freshRows.collect { if (it == "cinema") loadRows() }
        }
        viewModelScope.launch {
            // Запит — до update: лямбда update повторюється на кожній гонці CAS, і мережа йшла б кілька разів.
            val watched = watchProgress.finishedKeys()
            state.update { it.copy(watched = watched) }
        }
        viewModelScope.launch {
            // Синхронізація перед підпискою: локальний кеш малюється одразу, а сервер
            // догоняє й може прибрати те, що зняли з іншої приставки.
            watchlist.sync()
            // Пам'ять озвучок і джерел — з профілю, щоб і тут вмикалась та сама студія.
            prefs.sync()
            watchlist.items.collect { items ->
                state.update { it.copy(watchlist = items) }
                // Кадр і лого доїжджають другим кроком: список малюється одразу.
                val art = runCatching { catalog.withArt(items) }.getOrNull() ?: return@collect
                state.update { current -> if (current.watchlist == items) current.copy(watchlist = art) else current }
            }
        }
    }

    /**
     * Перезбирання при поверненні в розділ, і лише якщо екран справді застояний.
     *
     * ViewModel живе стільки, скільки маршрут лежить у стеку навігації, а приставка не
     * гасне тижнями: без цього ряди лишались тими самими до перезапуску застосунку,
     * хоч би що з'явилось на сервері. Поріг той самий, що в кешів каталогу, тож захід
     * у розділ і вихід із нього мережі не коштує.
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - refreshedAt < REFRESH_AFTER_MS &&
            refreshedAt >= com.likhtar.home.core.catalog.TitleMarksRevision.changedAt
        ) return
        refreshedAt = System.currentTimeMillis()
        refresh()
        loadRows()
    }

    /** Коли востаннє перезбирали екран. */
    private var refreshedAt = System.currentTimeMillis()

    /** «Спробувати ще» з панелі «сервер не відповідає»: весь екран наново. */
    fun retry() {
        refreshedAt = System.currentTimeMillis()
        refresh()
        loadRows()
    }

    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(loading = true) }

            // «Продовжити» — паралельно: він питає й прогрес, і сервіси, і на холодному
            // старті весь Кінозал чекав його понад 10 с під ліхтарем. Ряд не
            // порожній і без нього — радник і «Закладки» стоять у ньому завжди.
            launch { loadResume() }
            val catalogState = catalog.curation()
            // Окремим кроком і після курації: ряди йдуть у TMDB і в наш каталог, тобто
            // коштують мережі, а решта екрана має з'явитись, не чекаючи їх.
            launch {
                val ukrainian = runCatching { catalog.ukrainianRows() }.getOrDefault(emptyList())
                // Другої фільтрації немає й тут: ручка `/api/ukrainian` віддає лише те,
                // що справді відкривається, і воно українське за походженням — мітку
                // ставимо всім, не перепитуючи.
                val ids = ukrainian.flatMap { row -> row.items.map { it.tmdbId } }.toSet()
                // Самі ряди екран не малює — потрібні лише мітки UA на картках.
                state.update { it.copy(uaAudio = it.uaAudio + ids) }
            }
            state.update {
                it.copy(
                    loading = false,
                    configured = catalogState.configured,
                    // «Пізнавальне» в Кінозалі більше не стоїть: документалки, природа й
                    // реаліті — не кіно, і його одинадцять рядів тепер наповнюють розділ
                    // «Реаліті» (див. RealityRows). Курація лишається в асеті недоторканою:
                    // її читає той самий файл, просто з іншого екрана.
                    studios = catalogState.studios.filterNot { it.id == EDUCATIONAL_STUDIO },
                    message = catalogState.message,
                )
            }
        }
    }

    /**
     * «Продовжити перегляд» — той самий ряд, що на Головній.
     *
     * Раніше сторінка будувала свій із системної таблиці WatchNext (те, що пишуть
     * сторонні застосунки): два ряди з однаковою назвою показували різне й вели на
     * різне. Тепер джерело одне — ResumeRows з core:catalog.
     */
    private suspend fun loadResume() {
        val resume = resumeRows.cinema()

        state.update { it.copy(resume = resume) }
    }

    /**
     * Пул слайдера — серверний ряд «Слайдер», і лише він.
     *
     * Досі пулів було два: клієнт сам питав TMDB (з логотипами й описами) і показував
     * перший слайд, а потім приходили серверні ряди й ПІДМІНЯЛИ пул — без логотипів. Перший
     * кадр лишався старим, тож до першого → слайдер був гарний, а далі гортався вже інший
     * список самим текстом. Тепер пул один, і логотипи йому довантажуються одразу.
     */
    private suspend fun setSlidePool(raw: List<CatalogItem>) {
        // Слайд без опису — це назва на кадрі, а для шапки розділу цього мало.
        // Щоразу нова видача: найсильніша новинка лишається першою, решту
        // тягнемо навмання з пулу сервера (він шле ~24 з нового й топу).
        val described = raw.filter { it.overview.isNotBlank() }.ifEmpty { raw }
        val picked = described.take(1) + described.drop(1).shuffled().take(SLIDES - 1)
        val pool = catalog.attachLogos(picked)
        slideIndex = 0
        val withTrivia = game.state.value.shown?.takeIf { pool.size > TRIVIA_AT }
            ?.let { pool.toMutableList().apply { add(TRIVIA_AT, triviaItem(it)) } } ?: pool
        triviaInPool = withTrivia.size > pool.size
        state.update { it.copy(trendingPool = withTrivia, slide = withTrivia.firstOrNull(), slideForward = true, triviaOn = false) }
        ticker.restart()
        val ua = runCatching { uaAudio.of(pool.map { it.tmdbId }) }.getOrDefault(emptyMap())
        state.update { current -> current.copy(uaAudio = current.uaAudio + ua.filterValues { it.ua }.keys) }
    }


    /** Позначка з ряду; ряди перезбираються тихо, без лоадера — картка просто зникає. */
    val rowMarks = RowMarks(marks, viewModelScope, watchlist) { item, ok ->
        state.update { it.copy(message = if (ok) context.getString(R.string.cinema_marked, item.title) else context.getString(R.string.cinema_server_no_response)) }
        // Фокус лишається в ряду сам (LikhtarRail переводить його на сусідню картку) —
        // ряди лише перезбираються, без стрибка на першу картку.
        if (ok) loadRows()
    }

    /**
     * Картка, на якій стоїть фокус у рядах, — лише щоб повернутися на неї зі сторінки тайтла.
     *
     * Звичайне поле, а не UiState: кожен рух D-pad писав картку в стан, і весь Кінозал разом
     * з усіма рядами перекомпоновувався на кожне натискання. Шапка в
     * режимі рядів схована, тож показувати цю картку нікому.
     */
    var railItem: CatalogItem? = null
        private set

    /** Фокус пішов у ряди. `inRows` уже `true` — стан не міняється, екран не чіпаємо. */
    fun onRailFocus(item: CatalogItem) {
        railItem = item
        state.update { it.copy(inRows = true) }
    }

    /** Фокус на картці ряду, за якою немає тайтла: радник. */
    fun onRowFocus() {
        railItem = null
        state.update { it.copy(inRows = true) }
    }

    /** Фокус на картці «Продовжити»: вона не з TMDB, повертатися на неї нема за чим. */
    fun onPreviewApp(@Suppress("UNUSED_PARAMETER") card: ResumeCard) {
        railItem = null
        state.update { it.copy(focused = null, inRows = true) }
    }

    /**
     * Повернення з колонок у слайдер: превʼю знову живе саме.
     *
     * Інакше воно лишалось із чужою карткою в руках, а кнопки «ДАЛІ» не було — сторінка
     * виглядала застряглою на тому, від чого користувач щойно пішов.
     */
    fun resetPreview() {
        railItem = null
        state.update {
            it.copy(focused = null, inRows = false)
        }
    }

    /** «ДАЛІ» в слайдері та автоматичне гортання — той самий крок. */
    fun nextSlide() = step(+1)

    fun prevSlide() = step(-1)

    private fun step(by: Int, manual: Boolean = true) {
        val pool = state.value.trendingPool
        if (pool.isEmpty()) return
        if (manual) ticker.restart(manual = true)
        slideIndex = (slideIndex + by + pool.size) % pool.size
        // Саме слайдер на питання не гортає: фокус там міняє кнопки, а читати треба з першого кадру.
        if (!manual && triviaInPool && slideIndex == TRIVIA_AT) slideIndex = (slideIndex + by + pool.size) % pool.size
        val next = pool[slideIndex]
        state.update {
            it.copy(
                slide = next,
                triviaOn = triviaInPool && slideIndex == TRIVIA_AT,
                focused = null,
                slideForward = by > 0,
            )
        }
    }


    /**
     * Ряди Кінозалу — одним запитом і в порядку сервера.
     *
     * Раніше клієнт збирав їх сам: чотири добірки з enum плюс окремі ручки «свіжого
     * українською». Тепер екран описаний на сервері, разом із підбірками стрімінгів,
     * тож клієнту лишається намалювати список і взяти готові мітки.
     */
    /** Картка ряду Реаліті — тією ж дорогою, що й у самому Реаліті ([RealityRows.open]). */
    fun openReality(card: CatalogItem, onShow: (String) -> Unit, onTitle: (CatalogItem) -> Unit) =
        realityRows.open(card, onShow, onTitle) { item ->
            viewModelScope.launch {
                if (!realityRows.launchElsewhere(item)) {
                    state.update { it.copy(message = context.getString(R.string.cinema_no_app_for, item.title)) }
                }
            }
        }

    /** Ряд Реаліті в Кінозалі: перший ряд розділу з картками (до 12). Порожня відповідь старий ряд не стирає. */
    private fun loadReality() {
        viewModelScope.launch {
            val shows = runCatching { realityRows.rows() }.getOrDefault(emptyList())
                .firstOrNull { it.items.isNotEmpty() }?.items?.take(12).orEmpty()
            if (shows.isNotEmpty()) state.update { it.copy(realityItems = shows) }
        }
    }

    private fun loadRows(after: (CinemaUiState) -> CinemaUiState = { it }) {
        viewModelScope.launch {
            val rows = runCatching { catalog.cinemaSection() }.getOrDefault(emptyList())
            if (rows.isEmpty()) return@launch
            val brands = runCatching { catalog.cinemaTiles() }.getOrDefault(emptyList())
            val genres = runCatching { catalog.cinemaGenres() }.getOrDefault(emptyList())
            // Топ — окремим запитом і не тримає решту екрана: приїде — стане на місце.
            if (state.value.top == null) {
                viewModelScope.launch {
                    val top = runCatching { catalog.cinemaTop() }.getOrNull() ?: return@launch
                    state.update { it.copy(top = top, uaAudio = it.uaAudio + top.ukrainian) }
                }
            }
            // Перший ряд — «Новинки прокату»: це пул слайдера, а не ряд постерів. Двічі
            // на екрані одне й те саме було б саме тим «городом», з якого почалась ця
            // перебудова.
            // Лише ряд, який сервер назвав слайдером: коли його немає (порожній пул), першим
            // ішов би «Нові серії» — і зникав із рядів, ставши шапкою.
            // За ключем, не за назвою: з &lang=en/pl сервер перекладає назви рядів.
            val slider = rows.firstOrNull()?.takeIf { it.key == com.likhtar.home.core.catalog.SLIDER_ROW_KEY }
            val hero = slider?.items.orEmpty()
            val rest = if (slider != null) rows.drop(1) else rows
            if (hero.isNotEmpty()) setSlidePool(hero)
            state.update { current ->
                current.copy(
                    rows = rest,
                    brands = brands,
                    genres = genres,
                    uaAudio = current.uaAudio + rows.flatMap { it.ukrainian },
                    russianAudio = rows.flatMap { it.russian }.toSet(),
                ).let(after)
            }
            // Ряд Реаліті — після рядів Кінозалу: збір Реаліті (сервер і портали) іде під тим самим
            // замком запитів і інакше тримав би перший кадр Кінозалу. Тут же він перезбирається з
            // рядами (свіжі, дитячий режим, профіль).
            loadReality()
            // Замір D2 і tools/tv_perf.sh: від створення вікна до перших рядів — раз за процес.
            if (!rowsLogged) {
                rowsLogged = true
                android.util.Log.i("LikhtarPerf", "Кінозал: ряди за ${com.likhtar.home.core.designsystem.LaunchClock.sinceWindow()} мс від створення вікна")
            }
            // Дитячий режим: вікові рейтинги — після показу рядів, і ряди уточнюються на місці.
            launch {
                // Герой теж: слайди — з першого ряду, і «Матриця» (R) крутилась у дитячому режимі.
                if (slider != null) {
                    runCatching { catalog.kidsChecked(listOf(slider)) }.getOrNull()?.firstOrNull()?.items
                        ?.takeIf { it.size != hero.size && it.isNotEmpty() }?.let { setSlidePool(it) }
                }
                val checked = runCatching { catalog.kidsChecked(state.value.rows) }.getOrNull() ?: return@launch
                val byKey = checked.associateBy { it.key }
                state.update { current -> current.copy(rows = current.rows.map { byKey[it.key] ?: it }) }
            }
            // «Бо сподобалось» — окремо, щоб TMDB не тримав решту екрана; стають після двох перших рядів.
            val liked = runCatching { catalog.likedRows() }.getOrDefault(emptyList())
            if (liked.isNotEmpty()) state.update { current ->
                val base = current.rows.filterNot { it.key.startsWith("liked:") }
                current.copy(rows = base.take(2) + liked + base.drop(2))
            }
            // Ряди за настроєм (C3) — так само окремо, після шести рядів: вечірній вибір, не новинки.
            val moods = runCatching { catalog.moodRows() }.getOrDefault(emptyList())
            if (moods.isNotEmpty()) state.update { current ->
                val keys = moods.map { it.key }.toSet()
                val base = current.rows.filterNot { it.key in keys || it.key.startsWith("mood_") || it.key.startsWith("day_") }
                current.copy(rows = base.take(MOOD_ROWS_AT) + moods + base.drop(MOOD_ROWS_AT))
            }
        }
    }


    /**
     * Картка чужого застосунку: вмикаємо там, де воно лежить.
     *
     * Своєї сторінки для неї немає — ми знаємо лише те, що сказав сервіс. Повідомлення
     * при невдачі обовʼязкове: мовчазне натискання, після якого нічого не сталося, — це
     * найгірший зі станів.
     */
    fun openInApp(card: ResumeCard) {
        if (card.launch?.uri != null && !card.openIn(apps)) {
            state.update { it.copy(message = context.getString(R.string.cinema_open_failed_megogo, card.title)) }
        }
    }

    companion object {
        /** Рядок заміру «ряди Кінозалу» — один на процес. */
        @Volatile private var rowsLogged = false
        /** Скільки рядів Кінозалу перед рядами за настроєм. */
        const val MOOD_ROWS_AT = 6
        /** Скільки екран лишається «свіжим» без повторного збирання. */
        private const val REFRESH_AFTER_MS = 30 * 60 * 1000L
        private const val SLIDES = 10
        /** Питання гри — другий слайд: перший лишається найсильнішій новинці. */
        const val TRIVIA_AT = 1

        /** Хвіст «S01E04» у назві зі стрічки: для пошуку в TMDB він тільки заважає. */
        /** Id курації «Пізнавальне» в `likhtar-rows.json`: тепер вона живить «Реаліті». */
        const val EDUCATIONAL_STUDIO = "educational_and_reality"


        /** Маршрут сторінки фільму будується з типу та id — інших даних для відкриття не треба. */
        fun titleRoute(item: CatalogItem): TitleRoute = TitleRoute(item.kind.name, item.tmdbId)

        fun kindOf(raw: String?): TitleKind =
            TitleKind.entries.firstOrNull { it.name == raw } ?: TitleKind.MOVIE
    }

    /** Картка ряду «Трейлери» — ролик у YouTube; `false` — не трейлер. */
    fun openTrailer(item: CatalogItem): Boolean = apps.openTrailer(item)

}

/** Слайд питання: кадр тайтла; опис — саме твердження (і слайд не дорівнює звичайному тайтлу). */
private fun triviaItem(s: com.likhtar.home.core.catalog.TriviaStatement) = s.item.copy(overview = s.text)
