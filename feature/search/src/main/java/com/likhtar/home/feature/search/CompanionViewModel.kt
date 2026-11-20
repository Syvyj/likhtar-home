package com.likhtar.home.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.Advice
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ContinuePlayback
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.TitleSearch
import com.likhtar.home.core.catalog.WatchProgressRepository
import com.likhtar.home.core.catalog.WatchlistRepository
import com.likhtar.home.core.catalog.openIn
import com.likhtar.home.core.catalog.ResumeRows
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Те, що Ліхтарик знає про людину: історія, закладки й поради на їх основі.
 *
 * Окремо від [SearchViewModel]: пошук живе запитом, а це — полиці, які від запиту не
 * залежать і не мусять перераховуватись на кожну літеру.
 */
@HiltViewModel
class CompanionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchProgress: WatchProgressRepository,
    private val continuePlayback: ContinuePlayback,
    private val resumeRows: ResumeRows,
    private val apps: AppsRepository,
    private val watchlist: WatchlistRepository,
    private val catalog: TitleSearch,
    private val settings: SettingsRepository,
    val phone: PhoneRemote,
    private val tracker: com.likhtar.home.core.catalog.TrackerRepository,
    private val titleMarks: com.likhtar.home.core.catalog.TitleMarksRepository,
    private val sectionRows: com.likhtar.home.core.catalog.SectionRowsRepository,
    private val catalogRepo: com.likhtar.home.core.catalog.CatalogRepository,
    trivia: com.likhtar.home.core.catalog.TriviaRepository,
) : ViewModel() {

    /** «Правда чи брехня»: раунд дня, далі — ще питання. */
    val game = com.likhtar.home.feature.cinema.TriviaGame(trivia, viewModelScope, withRound = true)

    private val logos = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Знак назви для героя: у розкладі TMDB і в порадах його немає. Кеш — на час життя екрана. */
    suspend fun logoOf(item: CatalogItem): String? {
        logos[item.id]?.let { return it }
        val path = runCatching { catalogRepo.attachLogos(listOf(item)).firstOrNull()?.logoPath }.getOrNull() ?: return null
        logos[item.id] = path
        return path
    }

    /** Картка історії шукає джерело — PreparingScrim, як на інших екранах. */
    val preparing = continuePlayback.preparing

    private val cards = MutableStateFlow<List<ResumeCard>?>(null)

    /** `null` — ще вантажиться: екран чекає, щоб поставити фокус саме на історію, а не на закладки. */
    val history: StateFlow<List<ResumeCard>?> = cards.asStateFlow()

    val bookmarks: StateFlow<List<CatalogItem>> =
        watchlist.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val advised = MutableStateFlow<Advice?>(null)

    /** `null` — Ліхтарик ще думає; порожньо — нема з чого радити чи модель недоступна. */
    val advice: StateFlow<Advice?> = advised.asStateFlow()

    /** Кнопка «Голосом» — лише коли увімкнено в налаштуваннях. */
    val voiceOnTv: StateFlow<Boolean> =
        settings.settings.map { it.voiceSearchOnTv }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val personalShelves = MutableStateFlow<List<Advice>>(emptyList())

    /** Щоденні особисті добірки від Antigravity — показуються миттєво, без очікування моделі. */
    val personal: StateFlow<List<Advice>> = personalShelves.asStateFlow()

    private val notice = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = notice.asStateFlow()

    private fun now(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private val todayFlow = MutableStateFlow(now())

    /** Сьогодні — перераховується при кожному поверненні: Ліхтарик, лишений з неділі, у понеділок показує новий тиждень. */
    val today: StateFlow<String> = todayFlow.asStateFlow()

    private val trackedShows = MutableStateFlow<List<com.likhtar.home.core.catalog.TrackedShow>>(emptyList())

    /** «Мої серіали»: закладки й автопрогрес зі статусом. */
    val shows: StateFlow<List<com.likhtar.home.core.catalog.TrackedShow>> = trackedShows.asStateFlow()

    private val weekEvents = MutableStateFlow<List<com.likhtar.home.core.catalog.CalendarEvent>>(emptyList())

    /** «Цього тижня»: серії моїх серіалів і фільми із закладок. */
    val week: StateFlow<List<com.likhtar.home.core.catalog.CalendarEvent>> = weekEvents.asStateFlow()

    /** Порада дня для публічної збірки, де полиць Ліхтарика немає: «Нове на моїх сервісах». */
    private val freshAdvice = MutableStateFlow<List<Pair<CatalogItem, String>>>(emptyList())

    /** Герой угорі: нова серія → «Продовжити» → порада дня (без моделі). */
    val hero: StateFlow<HeroPick?> = kotlinx.coroutines.flow.combine(trackedShows, cards, personalShelves, freshAdvice, todayFlow) { shows, history, personal, fresh, today ->
        val cont = history.orEmpty().mapNotNull { it.continueItem?.takeIf { c -> !c.finished } }
        val advice = personal.flatMap { shelf -> shelf.items.map { it to shelf.title } }.ifEmpty { fresh }
            .filter { it.first.backdropPath != null }
        pickHero(shows, cont, advice, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Довге OK на «Моїх серіалах» — спільна панель позначок; після вибору трекер перераховує. */
    val marks = com.likhtar.home.feature.cinema.RowMarks(titleMarks, viewModelScope, watchlist) { _, _ ->
        tracker.invalidate()
        loadTracker()
    }

    private val pending = MutableStateFlow<com.likhtar.home.core.catalog.TrackedShow?>(null)

    /** Публічна збірка: серію запустили з героя — після повернення спитати «Подивились?». */
    val askWatched: StateFlow<com.likhtar.home.core.catalog.TrackedShow?> = pending.asStateFlow()
    private var startedAt = 0L

    /** Герой «Дивитись» у публічній збірці: прогресу сервер не пише — питаємо самі. */
    fun watchingFromHero(show: com.likhtar.home.core.catalog.TrackedShow) {
        if (!com.likhtar.home.core.model.Distribution.store) return
        pending.value = null
        startedAt = System.currentTimeMillis()
        pendingShow = show
    }

    private var pendingShow: com.likhtar.home.core.catalog.TrackedShow? = null

    /** Повернулись у Ліхтарик: якщо йшли дивитись серію — питаємо. */
    fun onResumed() {
        // Позначку могли змінити деінде (сторінка тайтла, ряд Кінозалу) — трекер скине кеш сам.
        viewModelScope.launch { loadTracker() }
        game.load()
        val show = pendingShow ?: return
        // Питаємо, лише якщо Ліхтар ховався за застосунком сервісу; просто сторінка тайтла — ні.
        if (shouldAskWatched(startedAt, com.likhtar.home.core.catalog.AppForeground.stoppedAt)) pending.value = show
        pendingShow = null
        startedAt = 0L
    }

    fun answerWatched(yes: Boolean) {
        val show = pending.value ?: return
        pending.value = null
        pendingShow = null
        if (!yes) return
        viewModelScope.launch {
            titleMarks.setMark(show.item, "watched", "upto", season = show.season, episode = show.episode)
            tracker.invalidate()
            loadTracker()
        }
    }

    private suspend fun loadTracker() {
        val today = now().also { todayFlow.value = it }
        trackedShows.value = runCatching { tracker.shows() }.getOrDefault(emptyList())
        val days = com.likhtar.home.core.catalog.weekDays(today)
        weekEvents.value = runCatching { tracker.events(days.first(), days.last()) }.getOrDefault(emptyList())
    }

    private suspend fun loadFreshAdvice() {
        freshAdvice.value = runCatching {
            sectionRows.cinemaBrands(onlyPlayable = false).take(3).flatMap { brand ->
                sectionRows.cinemaBrandRows(brand.key, limit = 6).firstOrNull()?.items.orEmpty().take(5).map { it to context.getString(R.string.search_hero_fresh_on, brand.title) }
            }
        }.getOrDefault(emptyList())
    }

    init {
        viewModelScope.launch { loadTracker() }
        game.load()
        viewModelScope.launch {
            val profile = settings.settings.first().activeProfileId
            personalShelves.value = runCatching { catalog.shelves(profile) }.getOrDefault(emptyList())
            if (personalShelves.value.isEmpty()) loadFreshAdvice()
        }
        viewModelScope.launch {
            refresh()
            runCatching { watchlist.sync() }
            advise()
        }
    }

    private suspend fun refresh() {
        cards.value = resumeRows.history(HISTORY_MAX)
    }

    /**
     * Поради — за тим, що дивились і відклали. Список назв той самий, поки нічого нового,
     * тож сервер віддає з кешу, а не ганяє модель щоразу.
     */
    private suspend fun advise() {
        val like = (cards.value.orEmpty().mapNotNull { it.continueItem?.title } + watchlist.items.first().map { it.title })
            .distinct().take(ADVISE_FROM)
        if (like.isEmpty()) {
            advised.value = Advice()
            return
        }
        val known = cards.value.orEmpty().mapNotNull { it.continueItem?.tmdbId }.toSet() + watchlist.items.first().map { it.tmdbId }
        val advice = runCatching { catalog.advise(like) }.getOrDefault(Advice())
        advised.value = advice.copy(items = advice.items.filterNot { it.tmdbId in known })
    }

    /** Картка історії: наша — вмикаємо з місця зупинки, MEGOGO — у MEGOGO. `false` — не вийшло. */
    fun play(card: ResumeCard): Boolean {
        card.continueItem?.let { item ->
            viewModelScope.launch {
                if (!continuePlayback.play(item)) notice.value = context.getString(R.string.search_play_failed, item.title)
                refresh()
            }
            return true
        }
        return card.openIn(apps)
    }

    fun forget(card: ResumeCard) {
        val item = card.continueItem ?: return
        viewModelScope.launch {
            cards.value = cards.value.orEmpty().filterNot { it.continueItem?.id == item.id }
            val forgotten = watchProgress.forget(item)
            notice.value = if (forgotten) context.getString(R.string.search_history_removed, item.title) else context.getString(R.string.search_server_no_reply)
            if (!forgotten) refresh()
        }
    }

    fun toggleBookmark(item: CatalogItem) {
        viewModelScope.launch {
            val added = watchlist.toggle(item)
            notice.value = context.getString(if (added) R.string.search_bookmark_added else R.string.search_bookmark_removed, item.title)
        }
    }

    fun isBookmarked(item: CatalogItem): Boolean = bookmarks.value.any { it.id == item.id }

    fun say(text: String) {
        notice.value = text
    }

    fun clearMessage() {
        notice.value = null
    }

    private companion object {
        const val HISTORY_MAX = 30
        const val ADVISE_FROM = 12
    }
}
