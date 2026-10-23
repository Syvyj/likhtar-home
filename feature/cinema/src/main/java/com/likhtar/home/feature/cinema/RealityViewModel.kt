package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.TmdbImages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.ResumeRows
import com.likhtar.home.core.catalog.openIn
import com.likhtar.home.core.catalog.ResolverClient
import com.likhtar.home.core.catalog.SectionBrand
import com.likhtar.home.core.catalog.SectionGenre
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.tv.RealityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class RealityUiState(
    val loading: Boolean = true,
    val rows: List<RealitySection> = emptyList(),
    /** Пул слайдера. Порожній, доки не приїхали дані — тоді сторінка показує самі ряди. */
    val pool: List<CatalogItem> = emptyList(),
    val slide: CatalogItem? = null,
    val slideForward: Boolean = true,
    /** Ми в рядах, а не в слайдері: та сама двостанність, що в Кінозалі. */
    val inRows: Boolean = false,
    /**
     * «Продовжити перегляд» — випуски порталів, на яких зупинились. Один рядок на проєкт:
     * так вирішив сервер, і ламати це з клієнта не можна, інакше ряд стане історією.
     */
    val resume: List<ResumeCard> = emptyList(),
    /** Плитки внизу: сервіси й мовники, чиє це неігрове. */
    val brands: List<SectionBrand> = emptyList(),
    /** «Жанри», як в Apple TV: ряд плиток між рядами. */
    val genres: List<SectionGenre> = emptyList(),
    /**
     * Логотипи назв, добрані на фокусі: `discover` їх не віддає взагалі, а окремий запит
     * на кожну картку ряду коштував би двадцять запитів на відкриття екрана. Тому
     * підвантажуємо саме ту, на яку дивляться.
     */
    val logos: Map<Int, String> = emptyMap(),
    /**
     * Велика обкладинка для шапки, коли вона приїхала. Ключ — картка, для якої питали:
     * поки відповідь у дорозі, шапка малює кадр картки, а не порожнечу.
     */
    val heroArt: Pair<CatalogItem, String>? = null,
    val message: String? = null,
)

/**
 * Стан розділу «Реаліті».
 *
 * Розділ не має власного вигляду: він малюється ТИМИ САМИМИ компонентами, що Кінозал
 * (`CinemaHero`, `RailBackdrop`, `RailHeroBlock`, `CollectionRail`, `CatalogBannerCard`), і
 * саме тому живе в його модулі. Уся робота тут — двостанність «слайдер ↔ ряди»; звідки
 * беруться позиції, знає [RealityRows].
 *
 * Пул слайдера — початок першого ряду, а він упорядкований датою останнього випуску. Тобто
 * в шапці стоїть те, що вийшло останнім, і це не домисел клієнта: дату рахує сервер.
 */
@HiltViewModel
class RealityViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val rows: RealityRows,
    private val resumeRows: ResumeRows,
    private val resolver: ResolverClient,
    private val apps: AppsRepository,
    marks: com.likhtar.home.core.catalog.TitleMarksRepository,
    private val continuePlayback: com.likhtar.home.core.catalog.ContinuePlayback,
) : ViewModel() {

    /** Картка «Продовжити» шукає джерело — PreparingScrim. */
    val preparing = continuePlayback.preparing

    /** Позначка з ряду — ряди перезбираються (сервер уже прибрав позначене). */
    val rowMarks = RowMarks(marks, viewModelScope) { _, ok -> if (ok) refreshIfStale() }

    private val state = MutableStateFlow(RealityUiState())
    val uiState: StateFlow<RealityUiState> = state.asStateFlow()

    private var slideIndex = 0

    // До init: той викликає restart() одразу.

    init {
        reload()
    }

    /** Ряди, «Продовжити» й слайдер наново — і на старті, і з «Спробувати ще». */
    fun reload() {
        state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // «Продовжити перегляд» збираємо ПАРАЛЕЛЬНО з рядами й кладемо в стан одним
            // оновленням. Доти він приїжджав окремо й пізніше, тож ряд вставлявся в уже
            // намальований список — і було видно, що першим стоїть «Суто українське», а
            // «Продовжити» з'являється десь нижче після першого руху фокуса.
            val resumeTask = async { resumeRows.reality() }
            val all = runCatching { rows.rows() }.getOrDefault(emptyList())
            val sections = withoutSlider(all)
            // Герой — з УСІХ рядів, а не з першого.
            //
            // Доти пул слайдера був `sections.first().items`, тобто завжди «Суто
            // українське»: решта розділу в героя не потрапляла взагалі, хоч би що там
            // вийшло. Беремо по одній позиції з кожного ряду — кожна з них уже перша у
            // своєму, тобто найсвіжіша в своїй темі, — і слайдер заразом перестає бути
            // одноманітним.
            // У шапку — лише те, у чого є широкий кадр. Позиції сервісів приходять із
            // портретним постером 193×272, і на весь екран він розтягується в мило: краще
            // взяти наступну позицію ряду, ніж малювати шапку обрізком.
            // Слайдер приходить окремим рядом із сервера — свіже неігрове, десять позицій.
            // Коли його немає (старий сервер), лишається попередній спосіб: по одній
            // позиції з кожного ряду, кожна з них перша у своєму, тобто найсвіжіша в темі.
            val slider = all.firstOrNull()?.takeIf { it.title == SLIDER_ROW }
            // Слайдера бракує (не українцю сервер вирізає з нього портали) — добираємо першими з рядів.
            val pool = (
                slider?.items.orEmpty() +
                    sections.mapNotNull { row -> row.items.firstOrNull { !it.backdropPath.isNullOrBlank() } }
                )
                // У шапку — лише те, у чого є широкий кадр: портретний постер сервісу
                // розтягується на весь екран у мило.
                .filter { !it.backdropPath.isNullOrBlank() }
                .distinctBy { it.id }
                .take(HERO_POOL)
            state.update {
                it.copy(
                    loading = false,
                    rows = sections,
                    resume = resumeTask.await(),
                    pool = pool,
                    brands = rows.brands(),
                    genres = rows.genres(),
                    slide = pool.firstOrNull(),
                    message = if (sections.isEmpty()) context.getString(R.string.cinema_reality_nothing_found) else null,
                )
            }
            pool.firstOrNull()?.let(::loadHeroArt)
            pool.firstOrNull()?.let(::loadLogo)
            // Слайдер не гортається сам (прототип): шапку веде фокус у «Нових випусках».
        }
    }

    /**
     * Те саме, що в Кінозалі: повернення в розділ перезбирає ряди, якщо вони застояні.
     *
     * Сама розкладка має власний термін життя всередині [RealityRows]; тут ми лише даємо
     * привід у неї зазирнути — інакше ViewModel тримала б перший зріз, доки маршрут
     * лежить у стеку, тобто тижнями.
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - loadedAt < REFRESH_AFTER_MS &&
            loadedAt >= com.likhtar.home.core.catalog.TitleMarksRevision.changedAt
        ) return
        loadedAt = System.currentTimeMillis()
        viewModelScope.launch {
            val all = runCatching { rows.rows() }.getOrDefault(emptyList())
            val sections = withoutSlider(all)
            if (sections.isEmpty()) return@launch
            state.update { it.copy(rows = sections) }
        }
    }

    private var loadedAt = System.currentTimeMillis()

    /** Ряд слайдера малюється шапкою, а не рядом постерів: у переліку його бути не має. */
    private fun withoutSlider(all: List<RealitySection>): List<RealitySection> =
        if (all.firstOrNull()?.title == SLIDER_ROW) all.drop(1) else all

    /** Кадр проєкту для картки «Продовжити»: у рядку прогресу зображень немає. */
    fun artOf(card: ResumeCard): String? =
        card.showKey?.let(RealityTitleViewModel::idOfExtKey)?.let(rows::artOf) ?: card.imageUrl

    /** Картка MEGOGO: вмикаємо там, де воно лежить. */
    fun openInApp(card: ResumeCard) {
        if (!card.openIn(apps)) state.update { it.copy(message = context.getString(R.string.cinema_open_failed_megogo, card.title)) }
    }

    /** Оновити ряд «Продовжити»: після повернення з плеєра позиція вже інша. */
    fun loadResume() {
        viewModelScope.launch {
            val resume = resumeRows.reality()
            state.update { it.copy(resume = resume) }
        }
    }

    /** Куди веде картка — див. [RealityRows.open]. */
    fun open(card: CatalogItem, onShow: (String) -> Unit, onTitle: (CatalogItem) -> Unit) =
        rows.open(card, onShow, onTitle, ::openExternally)

    /**
     * Позиція, у якої немає нашої сторінки: вмикаємо там, де вона лежить.
     *
     * Сторінки матеріалу для неї не буде й не має бути — у ютуб-плейлиста чи позиції
     * MEGOGO немає ні торентів, ні серій у нашому вигляді. Сервер віддає готовий діплінк
     * (`/api/watch`), клієнт відповідає лише на те, чого сервер не знає: який із
     * застосунків стоїть на цій приставці.
     */
    fun openExternally(card: CatalogItem) {
        viewModelScope.launch {
            val options = runCatching { resolver.watch(card) }.getOrDefault(emptyList())
            val launched = options.any { option ->
                option.packages.any { apps.launchIntentUri(option.deeplink, it) }
            }
            if (!launched) {
                state.update { it.copy(message = context.getString(R.string.cinema_no_app_for, card.title)) }
            }
        }
    }

    /**
     * Фокус став на ряд, за яким немає позиції розділу — «Продовжити перегляд».
     *
     * Стан рядів мусить увімкнутись однаково, звідки б людина в них не зайшла: інакше
     * шапка лишається слайдером, а фокус уже внизу, і ↑/↓ починають ходити навпіл.
     */
    fun onRowFocus() = state.update { it.copy(inRows = true) }

    fun onRailFocus(item: CatalogItem) {
        // Поле, а не UiState — як у Кінозалі: кожен рух D-pad перекомпоновував розділ, а в
        // рядах шапки немає. Обкладинку проєкту тут не питаємо: вона потрібна лише слайдеру.
        railItem = item
        state.update { it.copy(inRows = true) }
        loadLogo(item)
    }

    /**
     * Логотип назви для картки під курсором.
     *
     * Один запит на картку, а не двадцять на ряд: логотипа немає в жодній відповіді
     * `discover`, і доливати їх наперед означало б платити за те, чого людина не бачить.
     * Для наших позицій не питаємо — TMDB про них не знає.
     */
    private fun loadLogo(card: CatalogItem) {
        if (card.logoPath != null || rows.ukrainianOf(card) != null) return
        if (state.value.logos.containsKey(card.tmdbId)) return
        viewModelScope.launch {
            val logo = runCatching { rows.logoOf(card) }.getOrNull() ?: return@launch
            state.update { it.copy(logos = it.logos + (card.tmdbId to logo)) }
        }
    }

    /**
     * Догнати шапку найбільшим зображенням позиції.
     *
     * Окремим запитом, бо в каталозі його немає: обкладинка 1920×730 лежить на сторінці
     * проєкту. Відповідь кешується в [RealityRows], тож повторний наїзд на ту саму картку
     * нічого не коштує.
     */
    private fun loadHeroArt(card: CatalogItem) {
        viewModelScope.launch {
            val art = runCatching { rows.heroArtOf(card) }.getOrNull() ?: return@launch
            state.update { current ->
                // Поки відповідь ішла, курсор міг поїхати далі — тоді вона вже не про те,
                // що на екрані, і підміняти кадр нею означало б показати чужу обкладинку.
                if (current.slide != card) current else current.copy(heroArt = card to art)
            }
        }
    }

    /** Повернення з рядів у слайдер — той самий крок назад, що в Кінозалі. */
    fun resetPreview() {
        railItem = null
        state.update { it.copy(inRows = false) }
    }

    /** Картка у фокусі рядів — лише щоб повернутися на неї зі сторінки шоу. */
    var railItem: CatalogItem? = null
        private set

    fun nextSlide() = step(+1)

    /** Випуск у фокусі «Нових випусків» — у шапку (прототип). */
    fun showSlide(index: Int) {
        val pool = state.value.pool
        if (index !in pool.indices || pool[index] == state.value.slide) return
        slideIndex = index
        state.update { it.copy(slide = pool[index], slideForward = true) }
    }

    fun prevSlide() = step(-1)

    private fun step(by: Int) {
        val pool = state.value.pool
        if (pool.isEmpty()) return
        slideIndex = (slideIndex + by + pool.size) % pool.size
        val next = pool[slideIndex]
        state.update { it.copy(slide = next, slideForward = by > 0) }
        loadHeroArt(next)
        loadLogo(next)
    }

    private companion object {
        /** Скільки розділ лишається «свіжим» без повторного збирання. */
        const val REFRESH_AFTER_MS = 30 * 60 * 1000L

        /** Пунктів у списку шапки (шість). */
        const val HERO_POOL = 6


        /** Назва ряду, який сервер віддає під шапку розділу. */
        const val SLIDER_ROW = "Слайдер"
    }
}
