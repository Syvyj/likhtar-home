package com.likhtar.home.feature.cinema

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
    /**
     * Одна українська стрічка: нові фільми й серіали з TMDB та нашого каталогу.
     * Стоїть після загальних «Нові фільми» і «Нові серіали», а не на початку екрана.
     */
    val ukrainianRows: List<CatalogRow> = emptyList(),
    /** [CatalogItem.id] того, що профіль уже додивився. */
    val watched: Set<String> = emptySet(),
    /** Прибирати переглянуте з рядів, а не лише позначати галочкою. */
    val hideWatched: Boolean = false,
    /** Ряд «Аніме» — лише з перемикачем у налаштуваннях. */
    val showAnime: Boolean = false,
    /** Відповіді на два питання сторінки. */
    val time: WatchTime = WatchTime.NINETY,
    val mood: WatchMood = WatchMood.LIGHT,
    /** П'ять кандидатів на вечір: перший головний, решта — «інші кандидати». */
    val candidates: List<Candidate> = emptyList(),
    val pickingCandidates: Boolean = true,
    /**
     * Ряди Кінозалу в тому порядку, у якому їх дав сервер.
     *
     * Порядок екрана більше не живе в enum на клієнті: після слайдера йдуть «Новинки
     * кіно та серіалів», «Українська стрічка» і по ряду на кожен стрімінг. Це той самий
     * порядок, що бачитиме будь-яка інша інсталяція.
     */
    val rows: List<SectionRow> = emptyList(),
    /** «Топ-10» випадкового сервісу (прототип); `null` — ще не приїхав або немає. */
    val top: SectionRow? = null,
    /** Плитки стрімінгів у кінці розділу: двері на сторінку сервісу. */
    val brands: List<SectionBrand> = emptyList(),
    /** «Жанри», як в Apple TV: ряд плиток між рядами. */
    val genres: List<SectionGenre> = emptyList(),
    /** Позиції, які тримаються на російській доріжці: мітка на картці інша. */
    val russianAudio: Set<Int> = emptySet(),
    /** Типові добірки під кандидатами: новинки, топи, серіали, фільми. */
    val collections: Map<WatchCollection, List<CatalogItem>> = emptyMap(),
    /** Пул слайдера — серверний ряд «Слайдер». */
    val trendingPool: List<CatalogItem> = emptyList(),
    /**
     * «Незабаром українською»: дата появи ще не настала.
     *
     * Окремий ряд, а не хвіст «Нового»: вмикати тут поки нічого, і ставити ці картки
     * поруч із тим, що вже грає, означало б обіцяти кнопку, якої немає.
     */
    val soon: List<CatalogItem> = emptyList(),
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
    val advisorPick: Candidate? = null,
    /** Поточний слайд, коли ніхто нічого не вибрав: новинки, топ, рекомендації. */
    val slide: CatalogItem? = null,
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
) : ViewModel() {

    /** Картка «Продовжити» шукає джерело — PreparingScrim. */
    val preparing = continuePlayback.preparing

    private val state = MutableStateFlow(CinemaUiState())
    val uiState: StateFlow<CinemaUiState> = state.asStateFlow()

    private var candidatesJob: Job? = null
    private var candidateRound = 0
    private var advisorIndex = -1
    private var slideIndex = 0

    // До init: той викликає restart() одразу.
    private val ticker = SlideTicker(
        viewModelScope,
        paused = { state.value.let { it.inRows || it.advisorPick != null } },
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
        viewModelScope.launch {
            media.state.collect { lib -> state.update { it.copy(local = lib.titles.map { t -> t.item }, localOther = lib.other) } }
        }
        refresh()
        pickCandidates(round = 0)
        loadRows()
        viewModelScope.launch {
            // Прапорець потоком: перемкнули в налаштуваннях — ряди перебудовуються без
            // повторного заходу в Кінозал.
            settings.settings.collect { current ->
                state.update { it.copy(hideWatched = current.hideWatched, showAnime = current.showAnime) }
            }
        }
        viewModelScope.launch {
            state.update { it.copy(watched = watchProgress.finishedKeys()) }
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
                state.update { it.copy(ukrainianRows = ukrainian, uaAudio = it.uaAudio + ids) }
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
        state.update { it.copy(trendingPool = pool, slide = pool.firstOrNull(), slideForward = true) }
        ticker.restart()
        val ua = runCatching { uaAudio.of(pool.map { it.tmdbId }) }.getOrDefault(emptyMap())
        state.update { current -> current.copy(uaAudio = current.uaAudio + ua.filterValues { it.ua }.keys) }
    }

    fun selectTime(time: WatchTime) {
        if (time == state.value.time) return
        state.update { it.copy(time = time) }
        pickCandidates(round = 0)
    }

    fun selectMood(mood: WatchMood) {
        if (mood == state.value.mood) return
        state.update { it.copy(mood = mood) }
        pickCandidates(round = 0)
    }

    /**
     * Кандидати перезапитуються на кожну зміну відповіді.
     *
     * Попередній запит скасовується: користувач швидко клацає пігулки, і показати результат
     * позаминулої відповіді гірше, ніж показати нічого.
     */
    private fun pickCandidates(round: Int) {
        candidateRound = round
        candidatesJob?.cancel()
        candidatesJob = viewModelScope.launch {
            state.update { it.copy(pickingCandidates = true) }
            val current = state.value
            val picked = runCatching { catalog.candidates(current.time, current.mood, round) }.getOrDefault(emptyList())
            state.update { it.copy(candidates = picked, pickingCandidates = false) }
        }
    }

    /** Позначка з ряду; ряди перезбираються тихо, без лоадера — картка просто зникає. */
    val rowMarks = RowMarks(marks, viewModelScope) { item, ok ->
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
        state.update { it.copy(focused = null, advisorPick = null, inRows = true) }
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
            it.copy(focused = null, advisorPick = null, inRows = false)
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
        val next = pool[slideIndex]
        state.update {
            it.copy(
                slide = next,
                focused = null,
                advisorPick = null,
                slideForward = by > 0,
            )
        }
    }

    /**
     * «Показати» в колонці радника: підбір їде у превʼю.
     *
     * Повторне натискання дає наступного кандидата — саме так і просив замовник: набір
     * лишається, а картка в слайдері міняється.
     */
    fun showAdvisorPick() {
        val picked = state.value.candidates
        if (picked.isEmpty()) {
            pickCandidates(candidateRound)
            return
        }
        advisorIndex = (advisorIndex + 1) % picked.size
        val candidate = picked[advisorIndex]
        state.update {
            it.copy(advisorPick = candidate, focused = null)
        }
    }

    /**
     * Ряди Кінозалу — одним запитом і в порядку сервера.
     *
     * Раніше клієнт збирав їх сам: чотири добірки з enum плюс окремі ручки «свіжого
     * українською». Тепер екран описаний на сервері, разом із підбірками стрімінгів,
     * тож клієнту лишається намалювати список і взяти готові мітки.
     */
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
            val slider = rows.firstOrNull()?.takeIf { it.title == "Слайдер" }
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
        /** Скільки екран лишається «свіжим» без повторного збирання. */
        private const val REFRESH_AFTER_MS = 30 * 60 * 1000L
        private const val SLIDES = 10

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
