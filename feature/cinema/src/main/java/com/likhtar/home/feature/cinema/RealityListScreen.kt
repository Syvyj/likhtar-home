package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.navigation.toRoute
import com.likhtar.home.core.catalog.rotatingPick
import com.likhtar.home.core.catalog.RowLayout
import com.likhtar.home.core.catalog.CatalogRow
import com.likhtar.home.core.catalog.CatalogListing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ResolverClient
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.designsystem.LanternLoaderScreen
import com.likhtar.home.core.designsystem.PageHeader
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.cachedScrim
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.tv.RealityItem
import com.likhtar.home.core.tv.RealityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Відкрити всі» для ряду «Реаліті»: весь ряд сіткою.
 *
 * Сторінка тієї ж природи, що `CatalogListScreen` у Кінозалі, — те саме тло, той самий
 * заголовок, ті самі картки ряду. Окремий екран, а не її параметр, лише тому, що та
 * ходить у каталог TMDB за `CatalogListingKind`, а тут позиції вже зібрані репозиторієм.
 */
@Composable
fun RealityListScreen(
    onOpenShow: (String) -> Unit,
    onOpenTitle: (CatalogItem) -> Unit,
    viewModel: RealityListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstCard = rememberInitialFocusRequester(state.items.size)
    // Ті самі три дороги, що в самому розділі: сторінка не має поводитись інакше за ряд.
    val open: (CatalogItem) -> Unit = { viewModel.open(it, onOpenShow, onOpenTitle) }
    state.page?.let { page ->
        ListingPage(
            listing = page,
            loading = false,
            hideWatched = false,
            watched = emptySet(),
            uaAudio = state.uaConfirmed,
            onItemFocused = {},
            open = open,
            marks = viewModel.rowMarks,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Тло статичне: кадр, що міняється від кожного руху по картках, смикав би всю
        // сторінку — те саме рішення, що на сторінці добірки Кінозалу.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cachedScrim(dim = Color.Transparent) { listOf(ListingBackground) },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LikhtarDimens.ScreenPadding),
            // Сітка: між рядами та сама видима відстань, що між картками (слот уже дає 2 × 8 dp).
            verticalArrangement = Arrangement.spacedBy(LikhtarDimens.CardGap - LikhtarDimens.CardSlotY * 2),
        ) {
            if (state.title.isNotBlank() || !state.loading) item(key = "head") {
                PageHeader(title = state.title.ifBlank { stringResource(R.string.cinema_collection) })
            }
            if (state.loading) {
                item(key = "loader") {
                    LanternLoaderScreen(modifier = Modifier.fillParentMaxHeight(0.85f), phrases = collectionPhrases())
                }
            }
            // Рядками по чотири: картка ряду — банер 16:9, і шість таких у рядок не
            // влазять навіть без полів.
            items(state.items.chunked(GRID_COLUMNS), key = { chunk -> chunk.first().id }) { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(LikhtarDimens.RailGap)) {
                    chunk.forEach { entry ->
                        CatalogBannerCard(
                            item = entry,
                            uaAudio = entry.tmdbId in state.uaConfirmed,
                            audioLabel = if (entry.tmdbId in state.russian) "RU" else "UA",
                            onClick = { open(entry) },
                            onLongClick = if (entry.markable()) ({ viewModel.rowMarks.open(entry) }) else null,
                            modifier = Modifier
                                .cardSlot()
                                .then(
                                    if (entry === state.items.firstOrNull()) {
                                        Modifier.focusRequester(firstCard)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ряди бренду → сторінка стрімінга: перший — великими картками, далі постери там, де є
 * справжні постери, і горизонтальні по черзі; банер одного проєкту — після другого ряду.
 */
internal fun brandListing(title: String, sections: List<RealitySection>): CatalogListing {
    val rows = mutableListOf<CatalogRow>()
    sections.forEachIndexed { index, section ->
        val posters = section.items.count(::hasRealPoster) * 2 >= section.items.size
        rows += CatalogRow(
            id = "reality-brand-$index", title = section.title, source = "reality", path = "",
            params = emptyMap(), items = section.items,
            layout = when {
                index == 0 -> RowLayout.FEATURED
                posters && index % 2 == 1 -> RowLayout.POSTERS
                else -> RowLayout.DEFAULT
            },
        )
        if (index == 1) {
            // Банер посеред сторінки — теж не завжди перший: свій на годину, як і шапка.
            rotatingPick("$title-spotlight", sections.flatMap { it.items }.filter { it.overview.length >= 40 })
                ?.let { pick ->
                    rows += CatalogRow(
                        id = "reality-brand-spotlight", title = "", source = "reality", path = "",
                        params = emptyMap(), items = listOf(pick), layout = RowLayout.SPOTLIGHT, keepOrder = true,
                    )
                }
        }
    }
    return CatalogListing(
        title = title,
        rows = rows,
        backdropPath = rotatingPick(title, sections.take(3).flatMap { it.items })?.backdropPath,
    )
}

data class RealityListUiState(
    val loading: Boolean = true,
    val title: String = "",
    /** Сторінка бренду рядами; `null` — звичайна сітка одного ряду. */
    val page: CatalogListing? = null,
    val items: List<CatalogItem> = emptyList(),
    /** Позиції з підтвердженою доріжкою — мітка на картці, така сама, як у ряду. */
    val uaConfirmed: Set<Int> = emptySet(),
    /** Позиції, чия мітка має казати `RU`. */
    val russian: Set<Int> = emptySet(),
)

@HiltViewModel
class RealityListViewModel @Inject constructor(
    private val rows: RealityRows,
    private val resolver: ResolverClient,
    private val apps: AppsRepository,
    marks: com.likhtar.home.core.catalog.TitleMarksRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Куди веде картка — див. [RealityRows.open]. */
    fun open(card: CatalogItem, onShow: (String) -> Unit, onTitle: (CatalogItem) -> Unit) =
        rows.open(card, onShow, onTitle, ::openExternally)

    /** Позиція без нашої сторінки вмикається там, де лежить. Див. RealityViewModel. */
    fun openExternally(card: CatalogItem) {
        viewModelScope.launch {
            val options = runCatching { resolver.watch(card) }.getOrDefault(emptyList())
            options.any { option -> option.packages.any { apps.launchIntentUri(option.deeplink, it) } }
        }
    }

    private val state = MutableStateFlow(RealityListUiState())
    val uiState: StateFlow<RealityListUiState> = state.asStateFlow()

    private val title = savedStateHandle.toRoute<RealityListRoute>().title

    /** Позначка з ряду — сторінка перезбирається (сервер уже прибрав позначене). */
    val rowMarks = RowMarks(marks, viewModelScope) { _, ok -> if (ok) load() }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        // Той самий склад рядів, що в розділі: інакше сітка за «Відкрити всі» показувала
        // б інше, ніж ряд, з якого в неї зайшли. Плитка бренду — той самий маршрут із
        // префіксом у назві: окремий екран був би копією цього з іншим запитом.
        // Плитка бренду з кількома рядами — сторінкою стрімінга, як у Кінозалі.
        if (title.startsWith(BRAND_PREFIX)) {
            // Сторінка мережі на холодну — кілька запитів TMDB, і перша спроба буває
            // порожньою: сервер добудовує її вже після нашого таймауту. Друга бере готове
            // (HBO відкрилась порожнім екраном із заголовком «brand:hbo»).
            var (name, sections) = rows.brandSections(title.removePrefix(BRAND_PREFIX))
            if (sections.isEmpty()) {
                kotlinx.coroutines.delay(RETRY_MS)
                rows.brandSections(title.removePrefix(BRAND_PREFIX)).let { name = it.first; sections = it.second }
            }
            if (sections.size > 1) {
                state.update {
                    it.copy(
                        loading = false,
                        title = name,
                        page = brandListing(name, sections),
                        uaConfirmed = sections.flatMap { s -> s.confirmed }.toSet(),
                        russian = sections.flatMap { s -> s.russian }.toSet(),
                    )
                }
                return
            }
        }
        val row = if (title.startsWith(BRAND_PREFIX)) {
            runCatching { rows.brandRow(title.removePrefix(BRAND_PREFIX)) }.getOrNull()
        } else {
            runCatching { rows.rows() }.getOrDefault(emptyList()).firstOrNull { it.title == title }
        }
        state.update {
            it.copy(
                loading = false,
                // Службовий ключ («brand:hbo») людині не показуємо — назву плитки або «Добірка».
                title = row?.title ?: if (title.startsWith(BRAND_PREFIX)) {
                    runCatching { rows.brands() }.getOrDefault(emptyList())
                        .firstOrNull { b -> b.key == title.removePrefix(BRAND_PREFIX) }?.title.orEmpty()
                } else title,
                items = row?.items.orEmpty(),
                uaConfirmed = row?.confirmed.orEmpty(),
                russian = row?.russian.orEmpty(),
            )
        }
    }

    companion object {
        /** Плитка бренду їде тим самим маршрутом, що й ряд: назва з префіксом. */
        private const val BRAND_PREFIX = "brand:"
        private const val RETRY_MS = 3_000L

        fun brandTitle(key: String): String = BRAND_PREFIX + key

        fun route(title: String): RealityListRoute = RealityListRoute(title)
    }
}

/** Чотири банери в рядок — рівно стільки вміщує канва з полями. */
private const val GRID_COLUMNS = 4
