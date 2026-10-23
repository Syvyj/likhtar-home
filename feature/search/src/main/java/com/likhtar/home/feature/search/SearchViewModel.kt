package com.likhtar.home.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.UaLookup
import com.likhtar.home.core.catalog.AskEvent
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.SearchShelf
import kotlinx.coroutines.launch
import com.likhtar.home.core.data.SearchHistory
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TitleSearch
import com.likhtar.home.core.tv.RealityItem
import com.likhtar.home.core.tv.ShowSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class SearchStatus {
    IDLE,
    TYPING,
    LOADING,
    RESULTS,
    EMPTY,
    ERROR,
}

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: List<CatalogItem> = emptyList(),
    val status: SearchStatus = SearchStatus.IDLE,
    @androidx.annotation.StringRes val message: Int? = null,
    /**
     * Позиції з підтвердженою українською доріжкою — мітка на картці результату.
     *
     * У пошуку це САМЕ мітка й порядок, а не відсів, як у рядах: людина, яка набрала
     * конкретну назву, шукає її, а не «щось із озвучкою». Не знайти те, що існує, —
     * гірша поломка за відсутню мітку.
     */
    val uaAudio: Set<Int> = emptySet(),
    /** Наші шоу у видачі (їхні синтетичні tmdbId) — окремий ряд «Шоу». */
    val shows: Set<Int> = emptySet(),
    /** Недавні запити — плашками, поки поле порожнє. */
    val recent: List<String> = emptyList(),
    /** Ряди «з чого почати»: на порожньому екрані й коли нічого не знайшлося. */
    val starters: List<SearchShelf> = emptyList(),
    /** Що Ліхтарик (LLM) зрозумів з опису — вже звірене з TMDB. */
    val ai: List<CatalogItem> = emptyList(),
    /** Модель ще думає над цим запитом. */
    val aiPending: Boolean = false,
    /** Що Ліхтарик подумав про запит — наростає, поки модель пише. */
    val aiThought: String = "",
    /** «Подумав ще» — друга думка, коли її дав сильніший радник. */
    val aiRefined: String = "",
    /** «Чому саме це» до кожної його картки, за [CatalogItem.id]. */
    val aiWhy: Map<String, String> = emptyMap(),
    /** Що зараз у полі. Шукаємо не його, а [query] — те, що відправили «Пошуком». */
    val draft: String = "",
) {
    /**
     * Опис словами, а не назва: «фільм про синіх інопланетян». Тоді найкращий збіг —
     * від Ліхтарика: TMDB шукає ці слова в назвах і приносить випадкове.
     */
    val descriptive: Boolean get() = query.trim().split(WORDS).size >= DESCRIPTION_WORDS

    /** Найкращий збіг — великою карткою над рядами. */
    val best: CatalogItem? get() = (if (descriptive) ai.firstOrNull() else null) ?: results.firstOrNull() ?: ai.firstOrNull()
    val aiRest: List<CatalogItem> get() = ai.filter { it.id != best?.id }
    private val rest: List<CatalogItem> get() = results.filter { it.id != best?.id }
    val movies: List<CatalogItem> get() = rest.filter { it.kind == TitleKind.MOVIE && it.tmdbId !in shows }
    val series: List<CatalogItem> get() = rest.filter { it.kind == TitleKind.TV && it.tmdbId !in shows }
    val showItems: List<CatalogItem> get() = rest.filter { it.tmdbId in shows }

    internal companion object {
        private val WORDS = Regex("\\s+")
        const val DESCRIPTION_WORDS = 3
    }
}

/**
 * Пошук по каталогу TMDB.
 *
 * Запит іде не на кожну літеру, а після паузи: на пульті користувач набирає повільно,
 * і без затримки один «Термінатор» коштував би одинадцяти запитів до TMDB.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalog: TitleSearch,
    private val shows: ShowSearch,
    private val recentHistory: SearchHistory,
    private val uaAudio: UaLookup,
) : ViewModel() {

    /**
     * Чим відкривати нашу позицію: TMDB-id у неї синтетичний, а сторінку шоу відкриває
     * власний id. Тримаємо саме зіставлення, бо картка в сітці — спільна модель.
     */
    private var portalIds: Map<Int, String> = emptyMap()

    /** Підказка «порадь…» — лише коли є кому порадити (публічна збірка без AI — немає). */
    val canAsk: StateFlow<Boolean> = flow { emit(runCatching { catalog.canAsk() }.getOrDefault(false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), !com.likhtar.home.core.model.Distribution.store)

    /** `null` — це звичайний тайтл TMDB, і відкривати його треба сторінкою Кінозалу. */
    fun portalId(item: CatalogItem): String? = portalIds[item.tmdbId]

    private val query = MutableStateFlow("")
    private val draft = MutableStateFlow("")

    /** Останні успішні результати: під час набору сітка не блимає порожнім і не губить фокус. */
    private var lastResults: List<CatalogItem> = emptyList()
    private var lastSuggestions: List<String> = emptyList()
    private var lastAi: List<CatalogItem> = emptyList()

    private val starters = MutableStateFlow<List<SearchShelf>>(emptyList())

    init {
        viewModelScope.launch { starters.value = runCatching { catalog.starters() }.getOrDefault(emptyList()) }
    }

    private val searchState = query.flatMapLatest { rawQuery ->
        val current = rawQuery.trimStart()
        flow {
            if (current.isBlank()) {
                lastResults = emptyList()
                lastSuggestions = emptyList()
                lastAi = emptyList()
                emit(SearchUiState(query = rawQuery))
                return@flow
            }

            if (current.length < MIN_QUERY_LENGTH) {
                emit(
                    SearchUiState(
                        query = rawQuery,
                        suggestions = lastSuggestions,
                        results = lastResults,
                        status = SearchStatus.TYPING,
                        message = R.string.search_min_chars,
                    ),
                )
                return@flow
            }

            // Текст з'являється одразу; пауза стосується виключно мережевого запиту.
            // Результати в TYPING/LOADING — попередні, а не порожні: інакше на кожну
            // літеру сітка зникала, і фокус із неї падав.
            emit(
                SearchUiState(
                    query = rawQuery,
                    suggestions = lastSuggestions,
                    results = lastResults,
                    ai = lastAi,
                    status = SearchStatus.TYPING,
                ),
            )
            delay(NETWORK_DEBOUNCE_MS)
            emit(
                SearchUiState(
                    query = rawQuery,
                    suggestions = lastSuggestions,
                    results = lastResults,
                    ai = lastAi,
                    status = SearchStatus.LOADING,
                ),
            )
            try {
                val typed = current.trimEnd()
                coroutineScope<Unit> {
                    val words = typed.split(Regex("\\s+")).size
                    val smart = words >= AI_MIN_WORDS
                    // Опис чи прохання: TMDB шукає ці слова в назвах і приносить випадкове —
                    // тут першим говорить Ліхтарик, а TMDB лише запасний.
                    val descriptive = smart && words >= SearchUiState.DESCRIPTION_WORDS
                    // Усе паралельно: Ліхтарик не чекає TMDB (повільний TMDB тримав і думку).
                    val answers = if (smart) catalog.ask(typed).catch { }.produceIn(this) else null
                    val tmdb = async {
                        // Два каталоги одним списком: TMDB і наші шоу.
                        val ours = async { runCatching { shows.find(typed) }.getOrDefault(emptyList()) }
                        val found = merge(catalog.search(typed), ours.await())
                        val confirmed = runCatching {
                            uaAudio.of(found.map { it.tmdbId }).filterValues { it.ua }.keys
                        }.getOrDefault(emptySet())
                        // Стабільне сортування: підтверджені підіймаються, але всередині обох
                        // частин порядок лишається тим, який дав пошук, — релевантністю.
                        found.sortedByDescending { it.tmdbId in confirmed } to confirmed
                    }
                    var state = SearchUiState(query = rawQuery, suggestions = lastSuggestions, aiPending = smart, status = SearchStatus.LOADING)
                    var results = emptyList<CatalogItem>()
                    var confirmed = emptySet<Int>()
                    suspend fun takeTmdb() {
                        val (sorted, ua) = runCatching { tmdb.await() }.getOrElse {
                            if (it is CancellationException) throw it
                            if (!smart) throw it
                            emptyList<CatalogItem>() to emptySet()
                        }
                        results = if (descriptive) sorted else withBestLogo(sorted)
                        confirmed = ua
                        lastResults = results
                        lastSuggestions = results.take(4).map { it.title }
                    }
                    if (!descriptive) {
                        takeTmdb()
                        state = state.copy(
                            suggestions = lastSuggestions,
                            results = results,
                            uaAudio = confirmed,
                            shows = portalIds.keys,
                            status = when {
                                results.isNotEmpty() -> SearchStatus.RESULTS
                                smart -> SearchStatus.LOADING
                                else -> SearchStatus.EMPTY
                            },
                        )
                    }
                    emit(state)
                    if (answers == null) return@coroutineScope

                    // Ліхтарик відповідає потоком: думка друкується, картки з'являються по одній.
                    val ranked = sortedMapOf<Int, CatalogItem>()
                    val why = mutableMapOf<String, String>()
                    for (event in answers) {
                        when (event) {
                            is AskEvent.Thought -> state = state.copy(aiThought = event.text)
                            is AskEvent.Refined -> state = state.copy(aiRefined = event.text)
                            is AskEvent.Found -> {
                                // Лого лише банеру, і лише коли банер — від Ліхтарика.
                                val item = if (event.rank == 0 && descriptive) {
                                    runCatching { catalog.withLogo(event.item) }.getOrDefault(event.item)
                                } else {
                                    event.item
                                }
                                ranked[event.rank] = item
                                why[item.id] = event.why
                                state = state.copy(ai = ranked.values.toList(), aiWhy = why.toMap(), status = SearchStatus.RESULTS)
                            }
                        }
                        emit(state)
                    }
                    val ai = ranked.values.toList()
                    lastAi = ai
                    if (descriptive) takeTmdb()
                    // Опис, на який модель певна одного твору («Аватар»), — ряди під банером
                    // добираємо пошуком за цією назвою: продовження й однойменне. Ліхтарик
                    // мовчить (сервер недоступний) — показуємо, що знайшов TMDB.
                    val around = when {
                        !descriptive || ai.isEmpty() -> results
                        ai.size == 1 -> runCatching { catalog.search(ai.first().title) }.getOrDefault(emptyList())
                            .filterNot { hit -> ai.any { it.id == hit.id } }
                        else -> emptyList()
                    }
                    if (descriptive) lastResults = around
                    val aiUa = runCatching { uaAudio.of(ai.map { it.tmdbId }).filterValues { it.ua }.keys }.getOrDefault(emptySet())
                    emit(
                        state.copy(
                            results = around,
                            shows = portalIds.keys,
                            aiPending = false,
                            uaAudio = confirmed + aiUa,
                            status = if (around.isEmpty() && ai.isEmpty()) SearchStatus.EMPTY else SearchStatus.RESULTS,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emit(
                    SearchUiState(
                        query = rawQuery,
                        status = SearchStatus.ERROR,
                        message = R.string.search_failed_network,
                    ),
                )
            }
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(searchState, recentHistory.recent(), starters, draft) { state, recent, shelves, typed ->
        state.copy(recent = recent.take(RECENT_SHOWN), starters = shelves, draft = typed)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SearchUiState(),
        )

    fun clearRecent() {
        viewModelScope.launch { recentHistory.clear() }
    }

    fun append(text: String) {
        query.value += text
    }

    /**
     * Текст у полі без пошуку. Шукаємо лише на «Пошук» ([submit]): видача на кожну третю
     * літеру смикала екран і ганяла Ліхтарика по уламках запиту.
     */
    fun type(text: String) {
        draft.value = text
    }

    fun submit() {
        query.value = draft.value
    }

    /** Поставити й одразу шукати — голос, недавній запит, телефон. */
    fun setQuery(text: String) {
        draft.value = text
        query.value = text
    }

    fun clear() {
        draft.value = ""
        query.value = ""
    }

    /**
     * Запит потрапляє в історію тільки тоді, коли з нього щось відкрили.
     *
     * Писати на кожне натискання клавіші не можна: історія забилася б уламками
     * («о», «ок», «оке»…), а це саме те, що робить списки недавнього непотрібними.
     */
    fun rememberQuery() {
        val query = uiState.value.query
        viewModelScope.launch { recentHistory.remember(query) }
    }

    fun useSuggestion(suggestion: String) {
        setQuery(suggestion)
    }

    /**
     * Наші шоу перед світовими — і без тих, що вже є у видачі TMDB.
     *
     * Попереду, бо запит українською здебільшого і є про наше. Дублікат знімаємо за
     * назвою: у портальної позиції немає TMDB-id, тож склеїти їх нічим іншим, а дві
     * однакові картки поруч читаються як поломка. Коли обидві є, лишаємо TMDB — у неї є
     * логотип, сезони й опис.
     */
    private fun merge(world: List<CatalogItem>, ours: List<RealityItem>): List<CatalogItem> {
        val known = world.map { it.title.lowercase() }.toSet()
        val fresh = ours.filter { it.title.lowercase() !in known }
        portalIds = fresh.associate { it.id.hashCode() to it.id }
        return fresh.map(::asCard) + world
    }

    /**
     * Портальна позиція як картка сітки.
     *
     * Те саме правило, що в `RealityRows.asCatalogItem` у Кінозалі: синтетичний id — хеш
     * власного, кадр спершу чистий, і лише потім промо-банер. Спільного місця для цього
     * зараз немає — `RealityItem` живе в `:core:tv`, `CatalogItem` у `:core:catalog`, і
     * заводити модуль на шість рядків дорожче, ніж їх повторити.
     */
    private fun asCard(item: RealityItem): CatalogItem = CatalogItem(
        tmdbId = item.id.hashCode(),
        kind = TitleKind.TV,
        title = item.title,
        year = "",
        backdropPath = item.bestFrame,
        posterPath = item.image.takeIf { it.isNotBlank() },
        overview = item.description,
    )

    /** Найкращий збіг — банером з лого, як у Кінозалі: лого добираємо лише йому. */
    private suspend fun withBestLogo(items: List<CatalogItem>): List<CatalogItem> =
        items.firstOrNull()?.takeIf { it.tmdbId !in portalIds }?.let { top ->
            listOf(runCatching { catalog.withLogo(top) }.getOrDefault(top)) + items.drop(1)
        } ?: items

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** З двох слів запит уже може бути описом («про драконів»); одне слово — назва. */
        const val AI_MIN_WORDS = 2

        const val MIN_QUERY_LENGTH = 3
        const val RECENT_SHOWN = 8
        const val NETWORK_DEBOUNCE_MS = 275L
    }
}
