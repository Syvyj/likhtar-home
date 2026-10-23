package com.likhtar.home.feature.cinema

import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.CatalogListing
import com.likhtar.home.core.catalog.CatalogListingKind
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.UaAudioRepository
import com.likhtar.home.core.catalog.WatchProgressRepository
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogListUiState(
    val loading: Boolean = true,
    val listing: CatalogListing = CatalogListing(),
    /** Те, на чому фокус: керує фоном сторінки, як і в Кінозалі. */
    val focused: CatalogItem? = null,
    /** [CatalogItem.id] того, що профіль уже додивився. */
    val watched: Set<String> = emptySet(),
    /** Прибирати переглянуте зі сторінки, а не лише позначати. */
    val hideWatched: Boolean = false,
    /** Позиції, у яких ми бачили українську доріжку, — для мітки на картці. */
    val uaAudio: Set<Int> = emptySet(),
)

/**
 * Сторінка добірки: сервіс, хронологія або франшиза.
 *
 * Один екран на три джерела навмисно — див. `CatalogRepository.listing`. Аргументи маршруту
 * лише тип і ключ, тому сюди можна прийти і з Кінозалу, і зі сторінки фільму.
 */
@HiltViewModel
class CatalogListViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val catalog: CatalogRepository,
    private val uaAudio: UaAudioRepository,
    private val watchProgress: WatchProgressRepository,
    private val settings: SettingsRepository,
    private val apps: com.likhtar.home.core.data.AppsRepository,
    marks: com.likhtar.home.core.catalog.TitleMarksRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CatalogListRoute>()
    private val kind = CatalogListingKind.fromRoute(route.kind)
    private val key = route.key

    /** Ключ сітки одного ряду цієї сторінки — для «Відкрити всі» (CatalogRepository.rowListing). */
    fun rowKey(row: com.likhtar.home.core.catalog.CatalogRow): String = "row|${route.kind}|$key|${row.id}"

    /** Позначка з ряду — добірка перезбирається тихо, позначене зникає. */
    val rowMarks = RowMarks(marks, viewModelScope) { _, ok ->
        if (ok) {
            state.update { it.copy(watched = watchProgress.finishedKeys()) }
            kind?.let { load(it) }
        }
    }

    private val state = MutableStateFlow(CatalogListUiState())
    val uiState: StateFlow<CatalogListUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.update {
                it.copy(
                    watched = watchProgress.finishedKeys(),
                    hideWatched = settings.settings.first().hideWatched,
                )
            }
        }
        if (kind == null || key.isBlank()) {
            state.update {
                it.copy(loading = false, listing = CatalogListing(message = context.getString(R.string.cinema_unknown_collection)))
            }
        } else {
            viewModelScope.launch { load(kind) }
        }
    }

    private suspend fun load(kind: CatalogListingKind) {
        val listing = applyUaRule(catalog.listing(kind, key))
        // Саме `update`, а не присвоєння нового стану.
        //
        // `state.value = CatalogListUiState(...)` будує стан із нуля й затирає
        // все, що вже встигло приїхати паралельно: мітки озвучки, які щойно
        // поклав `applyUaRule`, і `watched`/`hideWatched` із сусідньої корутини.
        // Видно це було на сторінці «Суто українське»: у ряду картки з міткою, а
        // на «Відкрити всі» — ті самі картки без жодної.
        state.update {
            it.copy(
                loading = false,
                listing = listing,
                // Після позначки фокус лишається, де був, а не стрибає на першу картку.
                focused = it.focused ?: listing.rows.firstOrNull()?.items?.firstOrNull(),
            )
        }
    }

    fun onItemFocused(item: CatalogItem) {
        state.update { it.copy(focused = item) }
    }

    /**
     * Те саме правило, що в головному Кінозалі: у списку лишається те, що можна
     * ввімкнути. «Відкрити всі» не має бути місцем, де назад повертається все відсіяне,
     * — інакше правило є лише на першому екрані, а один крок убік його скасовує.
     *
     * Хронології та франшизи навмисно не чіпаємо: їхній ручний порядок — сам зміст
     * списку, і випадати з нього частини не можна.
     */
    private suspend fun applyUaRule(listing: CatalogListing): CatalogListing {
        // Ряд франшиз — картки сторінок, а не тайтли: озвучки в них питати нема про що.
        // Нумеровані ряди питаємо теж — без цього хронологія стояла приглушеною вся,
        // хоча переставляти в ній нічого не можна.
        val ids = listing.rows.filterNot { it.isPageRow }
            .flatMap { row -> row.items.map { it.tmdbId } }.distinct()
        if (ids.isEmpty()) return listing
        val confirmed = runCatching {
            uaAudio.of(ids).filterValues { it.ua }.keys
        }.getOrDefault(emptySet())
        state.update { it.copy(uaAudio = confirmed) }
        val fill = settings.settings.first().showWithoutUaAudio
        return listing.copy(
            rows = listing.rows.map { row ->
                if (row.numbered || row.keepOrder || row.isPageRow || row.spotlight != null) row else row.copy(
                    items = uaAudioRow(
                        items = row.items,
                        confirmedUaIds = confirmed,
                        freshness = { item ->
                            item.availabilityDate.takeIf { it.length >= 10 }
                                ?: item.releaseDate.takeIf { it.length >= 10 }
                                ?: item.year.takeIf { it.isNotBlank() }?.plus("-00-00") ?: ""
                        },
                        fillWithUnconfirmed = fill,
                    ),
                )
            },
        )
    }

    companion object {
        fun route(kind: CatalogListingKind, key: String): CatalogListRoute = CatalogListRoute(kind.name, key)
    }

    /** Картка ряду «Трейлери» — ролик у YouTube; `false` — не трейлер. */
    fun openTrailer(item: CatalogItem): Boolean = apps.openTrailer(item)

}

private val com.likhtar.home.core.catalog.CatalogRow.isPageRow: Boolean
    get() = items.isNotEmpty() && items.all { it.opens.isNotBlank() }
