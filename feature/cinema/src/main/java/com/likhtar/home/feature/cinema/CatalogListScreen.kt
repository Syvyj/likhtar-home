package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.likhtar.home.core.designsystem.ChipKind
import com.likhtar.home.core.designsystem.PageHeader
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.MetaChip
import androidx.compose.foundation.layout.FlowRow
import com.likhtar.home.core.designsystem.MinimalBringIntoView
import com.likhtar.home.core.designsystem.NoBringIntoView
import com.likhtar.home.core.designsystem.centerItem
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import com.likhtar.home.core.designsystem.VoiceBadge
import com.likhtar.home.core.catalog.opensListing
import com.likhtar.home.core.catalog.RowLayout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.CatalogListing
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.likhtar.home.core.catalog.TitleKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import com.likhtar.home.core.designsystem.requestWithRetry
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.likhtar.home.core.catalog.CatalogListingKind
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.CatalogRow
import com.likhtar.home.core.catalog.TmdbImages
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.likhtar.home.core.designsystem.CatalogPosterCard
import com.likhtar.home.core.designsystem.cachedScrim
import com.likhtar.home.core.designsystem.HeroBackdrop
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LanternLoaderScreen
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester

/**
 * Сторінка добірки: уся курація сервісу, хронологія всесвіту або частини франшизи.
 *
 * Верхнього меню тут немає — як і на сторінці фільму: це підсторінка Кінозалу, і Back веде
 * назад у каталог, а не на Головну. Тому й ↑ із першого рядка нікуди не веде.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogListScreen(
    onOpenTitle: (CatalogItem) -> Unit,
    onOpenListing: (CatalogListingKind, String) -> Unit,
    onOpenShow: (String) -> Unit = {},
    viewModel: CatalogListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listing = state.listing
    // Картка ряду «Франшизи й всесвіти» веде на сторінку, а не на тайтл.
    val open: (CatalogItem) -> Unit = open@{ item ->
        if (viewModel.openTrailer(item)) return@open
        if (item.showId.isNotBlank()) {
            onOpenShow(item.showId)
            return@open
        }
        item.opensListing?.let { (kind, key) -> onOpenListing(kind, key) } ?: onOpenTitle(item)
    }

    ListingPage(
        listing = listing,
        loading = state.loading,
        hideWatched = state.hideWatched,
        watched = state.watched,
        uaAudio = state.uaAudio,
        onItemFocused = viewModel::onItemFocused,
        open = open,
        marks = viewModel.rowMarks,
        // «Відкрити всі» в кінці кожного ряду сторінки — сітка саме цього ряду.
        onOpenAll = { row -> onOpenListing(CatalogListingKind.COLLECTION, viewModel.rowKey(row)) },
    )
}

/**
 * Сторінка добірки чи стрімінга — голова з кадром і ряди в міксі форм. Окремо від
 * [CatalogListScreen], бо так само малюється й сторінка мовника в Реаліті:
 * ті самі ряди, лише свої дороги з картки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListingPage(
    listing: CatalogListing,
    loading: Boolean,
    hideWatched: Boolean,
    watched: Set<String>,
    uaAudio: Set<Int>,
    onItemFocused: (CatalogItem) -> Unit,
    open: (CatalogItem) -> Unit,
    /** Довге OK на картці — «Позначити»: у кожної сторінки з рядами, без винятків. */
    marks: RowMarks,
    /** Сітка одного ряду; null — сторінка без «Відкрити всі». */
    onOpenAll: ((CatalogRow) -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLongOpen provides marks::open) {
        ListingPageBody(listing, loading, hideWatched, watched, uaAudio, onItemFocused, open, onOpenAll)
    }
    RowMarkPanel(marks)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListingPageBody(
    listing: CatalogListing,
    loading: Boolean,
    hideWatched: Boolean,
    watched: Set<String>,
    uaAudio: Set<Int>,
    onItemFocused: (CatalogItem) -> Unit,
    open: (CatalogItem) -> Unit,
    onOpenAll: ((CatalogRow) -> Unit)?,
) {
    // Пам'ять фокуса: Back з тайтла чи з «Відкрити всі» повертає на ту саму картку. Досі
    // сторінка просила першу картку, а список уже стояв прокручений — фокус губився.
    val lastFocus = rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    val returning = remember { Snapshot.withoutReadObservation { lastFocus.value } != null }
    LaunchedEffect(listing.rows.isNotEmpty()) {
        if (listing.rows.isEmpty()) return@LaunchedEffect
        if (returning && restoreFocus.requestWithRetry()) return@LaunchedEffect
        firstCardFocus.requestWithRetry()
    }
    val memory = remember { FocusMemory(lastFocus, restoreFocus) }
    // Ряд сторінки — 10 карток і «Відкрити всі», як у Кінозалі. Сітка
    // одного ряду сама й є «всі», тож там кнопки немає.
    val openAllOf: (CatalogRow) -> (() -> Unit)? = { row ->
        onOpenAll?.takeIf { listing.rows.size > 1 && row.items.size > RAIL_ITEMS }?.let { f -> { f(row) } }
    }
    // Сторінка з рядами прокручується лише пришпиленням: сфокусований ряд — по центру,
    // зверху шматок попереднього, знизу шматок наступного, як у Кінозалі й у Disney+.
    // Звичайний bringIntoView смикав сторінку на кожен рух ліворуч-праворуч: збільшена
    // фокусом картка виходила за межі ряду, і колонка доганяла її на кілька пікселів.
    val listState = rememberLazyListState()
    val hero = listing.backdropPath != null
    // Сітка одного ряду («Відкрити всі») — теж рядами по центру: інакше картка впиралась у
    // низ без запасу, а шапка сторінки не поверталась на першому ряду.
    val gridRows = !hero && listing.rows.size == 1
    val pinRows = hero || gridRows || listing.sectioned || (listing.rows.size > 1 && listing.rows.none { it.numbered })
    // Індекс елемента колонки, у якому фокус: 0 — голова сторінки.
    var focusedItem by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusedItem, pinRows) {
        if (pinRows && focusedItem >= 0) listState.centerItem(focusedItem)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Фон статичний: кадр, що міняється від кожного руху по картках, смикає всю
        // сторінку — на рядах постерів арт і так несе колір.
        // Сторінка франшизи — кадром на весь екран (подача Disney+). Теж статичний.
        // Тло рівне завжди: кадр франшизи — лише в голові сторінки й прокручується разом
        // із нею, як на сторінці фільму. Під рядами кадр заважав читати.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cachedScrim(dim = Color.Transparent, hero) {
                    listOf(if (hero) UniverseBackground else ListingBackground)
                },
        )

        CompositionLocalProvider(
            LocalBringIntoViewSpec provides if (pinRows) NoBringIntoView else MinimalBringIntoView,
            LocalFocusMemory provides memory,
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LikhtarDimens.SectionGap),
            // Бічні поля — у contentPadding, не в modifier: колонка обрізає вміст за своїми
            // межами (+30 dp), і кадр голови (fullBleed) не діставав країв — смуги по 18 dp
            // з боків. Запас унизу: інакше останній ряд не стає по центру.
            contentPadding = PaddingValues(
                start = LikhtarDimens.ScreenPadding,
                end = LikhtarDimens.ScreenPadding,
                bottom = 120.dp,
            ),
        ) {
            if (listing.backdropPath != null) item(key = "head") { FranchiseHead(listing) }
            // Поки назви ще немає — жодного заголовка: лише ліхтар по центру.
            else if (listing.title.isNotBlank() || !loading) item(key = "head") {
                Column {
                    PageHeader(title = listing.title.ifBlank { stringResource(R.string.cinema_collection) }, subtitle = listing.subtitle)
                    listing.message?.let {
                        Text(
                            text = it,
                            style = LikhtarType.MonoSmall,
                            color = LikhtarColors.TextMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Порожнє тіло під час завантаження: заголовок уже стоїть, а рядів ще немає —
            // саме тут очікування й видно.
            if (loading && listing.rows.isEmpty()) {
                item(key = "loader") {
                    LanternLoaderScreen(
                        modifier = Modifier.fillParentMaxHeight(0.85f),
                        phrases = collectionPhrases(),
                    )
                }
            }

            // Номер елемента колонки для кожного ряду: пришпилення знає, що центрувати.
            var lazyIndex = 1
            listing.rows.forEachIndexed { index, row ->
                // Прибирання переглянутого — тут, а не в репозиторії: галочка потрібна й
                // тоді, коли прапорець вимкнений, тож набір усе одно мусить доїхати сюди.
                val shown = if (hideWatched) {
                    row.copy(items = row.items.filterNot { it.id in watched })
                } else {
                    row
                }
                val spotlight = shown.spotlight
                if (listing.sectioned) {
                    // «Відкрити всі» частинами: великі горизонтальні, постери й горизонтальні по
                    // черзі, банер після першої (CatalogRepository.openAllSections).
                    if (shown.layout == RowLayout.SPOTLIGHT) {
                        shown.items.firstOrNull()?.let { item ->
                            val at = lazyIndex++
                            item(key = row.id) {
                                TitleSpotlight(
                                    item = item,
                                    ua = item.tmdbId in uaAudio,
                                    onClick = { open(item) },
                                    onLongClick = LocalLongOpen.current?.takeIf { item.markable() }?.let { f -> { f(item) } },
                                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                                )
                            }
                        }
                        return@forEachIndexed
                    }
                    val posters = shown.layout == RowLayout.POSTERS
                    val columns = when (shown.layout) {
                        RowLayout.POSTERS -> GRID_COLUMNS
                        RowLayout.FEATURED -> FEATURED_COLUMNS
                        else -> BANNER_COLUMNS
                    }
                    shown.items.chunked(columns).forEachIndexed { chunkIndex, chunk ->
                        val at = lazyIndex++
                        item(key = "${row.id}#$chunkIndex") {
                            Column(
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (chunkIndex == 0) SectionTitle(text = shown.title)
                                if (posters) {
                                    GridChunk(
                                        items = chunk, startPosition = chunkIndex * columns, numbered = false,
                                        watched = watched, uaConfirmed = uaAudio,
                                        firstFocus = if (at == 1) firstCardFocus else null,
                                        onFocused = onItemFocused, onClick = open,
                                    )
                                } else {
                                    BannerGridRow(
                                        items = chunk, startPosition = chunkIndex * columns, numbered = false,
                                        watched = watched, uaConfirmed = uaAudio,
                                        firstFocus = if (at == 1) firstCardFocus else null,
                                        onFocused = onItemFocused, onClick = open, columns = columns,
                                    )
                                }
                            }
                        }
                    }
                    return@forEachIndexed
                }
                if (hero && spotlight == null && listing.rows.size == 1) {
                    // Сторінка франшизи чи всесвіту — горизонтальні картки (подача Disney+),
                    // кожен ряд сітки — окремий елемент колонки, щоб центрувався сам.
                    shown.items.chunked(BANNER_COLUMNS).forEachIndexed { chunkIndex, chunk ->
                        val at = lazyIndex++
                        item(key = "${row.id}#$chunkIndex") {
                            BannerGridRow(
                                items = chunk,
                                startPosition = chunkIndex * BANNER_COLUMNS,
                                numbered = shown.numbered,
                                watched = watched,
                                uaConfirmed = uaAudio,
                                firstFocus = if (at == 1) firstCardFocus else null,
                                onFocused = onItemFocused,
                                onClick = open,
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                            )
                        }
                    }
                    return@forEachIndexed
                }
                if (gridRows && spotlight == null) {
                    shown.items.chunked(GRID_COLUMNS).forEachIndexed { chunkIndex, chunk ->
                        val at = lazyIndex++
                        item(key = "${row.id}#$chunkIndex") {
                            GridChunk(
                                items = chunk,
                                startPosition = chunkIndex * GRID_COLUMNS,
                                numbered = shown.numbered,
                                watched = watched,
                                uaConfirmed = uaAudio,
                                firstFocus = if (at == 1) firstCardFocus else null,
                                onFocused = onItemFocused,
                                onClick = open,
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                            )
                        }
                    }
                    return@forEachIndexed
                }
                val at = lazyIndex++
                item(key = row.id) {
                    // Нумерований список — сітка, а не рядок: у хронології MCU 34 позиції, і
                    // дійти до останньої горизонтальним рядком означає 33 натискання «праворуч».
                    // Сітка й тоді, коли добірка одна («Відкрити всі» з ряду).
                    //
                    // Кілька добірок (сторінка стрімінга) лишаються рядами: там сенс саме в
                    // тому, щоб побачити заголовки всіх підбірок одразу, не гортаючи вниз.
                    val pageRow = shown.items.isNotEmpty() && shown.items.all { it.opens.isNotBlank() }
                    if (shown.layout == RowLayout.GENRES) {
                        // Жанри сервісу: плитка веде в жанр усередині цього сервісу.
                        GenreRail(
                            genres = shown.genres,
                            onOpen = { genre ->
                                open(CatalogItem(0, TitleKind.MOVIE, "", year = "", opens = "studio:${genre.key}"))
                            },
                            onFocused = {},
                            modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                            // Back зі сторінки жанру — на ту саму плитку (досі — на картку перед нею).
                            tileModifier = { genre -> Modifier.remembered("g:${genre.key}") },
                        )
                    } else if (hero && shown.layout == RowLayout.SPOTLIGHT) {
                        shown.items.firstOrNull()?.let { item ->
                            TitleSpotlight(
                                item = item,
                                ua = item.tmdbId in uaAudio,
                                onClick = { open(item) },
                                onLongClick = LocalLongOpen.current?.takeIf { item.markable() }?.let { f -> { f(item) } },
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                            )
                        }
                    } else if (hero && shown.layout == RowLayout.POSTERS) {
                        CollectionRail(
                            posters = true,
                            title = shown.title,
                            items = shown.items,
                            watched = watched,
                            uaConfirmed = uaAudio,
                            numbered = shown.numbered,
                            anchor = if (index == 0) firstCardFocus else null,
                            onOpen = open,
                            onFocused = onItemFocused,
                            onOpenAll = openAllOf(shown).takeIf { !pageRow },
                            modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                        )
                    } else if (hero && spotlight == null && !pageRow) {
                        // Сторінка всесвіту — ряди, як Marvel у Disney+: горизонтальні картки
                        // рейкою. Три на екран і край четвертої, як «Нове й популярне»: чотири
                        // з краєм п'ятої виходили дрібними — лого й жанр не читались з дивана.
                        BoxWithConstraints(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at }) {
                            CollectionRail(
                                title = shown.title,
                                items = shown.items,
                                watched = watched,
                                uaConfirmed = uaAudio,
                                numbered = shown.numbered || shown.layout == RowLayout.TOP,
                                note = { it.note.ifBlank { null } },
                                cardWidth = (maxWidth - LikhtarDimens.CardGap * RAIL_COLUMNS) / (RAIL_COLUMNS + 0.3f),
                                gap = LikhtarDimens.CardGap - LikhtarDimens.CardSlotX * 2,
                                anchor = if (index == 0) firstCardFocus else null,
                                onOpen = open,
                                onFocused = onItemFocused,
                                onOpenAll = openAllOf(shown),
                            )
                        }
                    } else if (spotlight != null) {
                        SpotlightBand(
                            row = shown,
                            uaConfirmed = uaAudio,
                            onDetails = { open(CatalogItem(0, TitleKind.MOVIE, "", year = "", opens = spotlight.opens)) },
                            onFocused = onItemFocused,
                            onClick = open,
                            modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                        )
                    } else if (shown.numbered || listing.rows.size == 1) {
                        ListingGrid(
                            row = shown,
                            watched = watched,
                            uaConfirmed = uaAudio,
                            firstFocus = if (index == 0) firstCardFocus else null,
                            onFocused = onItemFocused,
                            onClick = open,
                        )
                    } else {
                        CollectionRail(
                            posters = true,
                            title = shown.title,
                            items = shown.items,
                            watched = watched,
                            uaConfirmed = uaAudio,
                            numbered = shown.numbered,
                            anchor = if (index == 0) firstCardFocus else null,
                            onOpen = open,
                            onFocused = onItemFocused,
                            onOpenAll = openAllOf(shown).takeIf { !pageRow },
                            modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedItem = at },
                        )
                    }
                }
            }
        }
        }
    }
}

/** Сітка: постер 150dp — [GRID_COLUMNS] стовпців уміщує канва 960dp із полями. */
@Composable
private fun ListingGrid(
    row: CatalogRow,
    watched: Set<String>,
    /** Позиції з підтвердженою доріжкою. У нумерованих рядах порожня — ми їх не питали. */
    uaConfirmed: Set<Int>,
    firstFocus: FocusRequester?,
    onFocused: (CatalogItem) -> Unit,
    onClick: (CatalogItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = row.title)
        row.items.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(LikhtarDimens.RailGap)) {
                chunk.forEachIndexed { columnIndex, item ->
                    val position = rowIndex * GRID_COLUMNS + columnIndex
                    CatalogPosterCard(
                        title = item.title,
                        year = item.year,
                        posterUrl = TmdbImages.poster(item.posterPath),
                        overlayTitle = item.logoPath == null && !hasRealPoster(item),
                        rating = item.rating,
                        onClick = { onClick(item) },
                    onLongClick = LocalLongOpen.current?.takeIf { item.markable() }?.let { f -> { f(item) } },
                        uaAudio = item.tmdbId in uaConfirmed,
                        order = if (row.numbered) position + 1 else null,
                        watched = item.id in watched,
                        modifier = Modifier
                            .cardSlot()
                            .onFocusChanged { if (it.isFocused) onFocused(item) }
                            .remembered("i:${item.id}")
                            .then(
                                if (position == 0 && firstFocus != null) {
                                    Modifier.focusRequester(firstFocus)
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

/** Пам'ять фокуса сторінки: ключ картки в фокусі й запит, яким на неї повернутись. */
internal class FocusMemory(val key: MutableState<String?>, val requester: FocusRequester)

internal val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/**
 * Картка, на яку можна повернутись після Back. Ключ читається без підписки: інакше кожен
 * рух фокуса перекомпоновував би всю сторінку.
 */
@Composable
internal fun Modifier.remembered(key: String): Modifier {
    val memory = LocalFocusMemory.current ?: return this
    val mine = remember(key) { Snapshot.withoutReadObservation { memory.key.value } == key }
    return (if (mine) focusRequester(memory.requester) else this)
        .onFocusChanged { if (it.isFocused) memory.key.value = key }
}

/** Ряд сітки однієї добірки — окремий елемент колонки, щоб центрувався сам. */
@Composable
private fun GridChunk(
    items: List<CatalogItem>,
    startPosition: Int,
    numbered: Boolean,
    watched: Set<String>,
    uaConfirmed: Set<Int>,
    firstFocus: FocusRequester?,
    onFocused: (CatalogItem) -> Unit,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(LikhtarDimens.RailGap)) {
        items.forEachIndexed { column, item ->
            val position = startPosition + column
            CatalogPosterCard(
                title = item.title,
                year = item.year,
                posterUrl = TmdbImages.poster(item.posterPath),
                overlayTitle = item.logoPath == null && !hasRealPoster(item),
                rating = item.rating,
                onClick = { onClick(item) },
                onLongClick = LocalLongOpen.current?.takeIf { item.markable() }?.let { f -> { f(item) } },
                uaAudio = item.tmdbId in uaConfirmed,
                order = if (numbered) position + 1 else null,
                watched = item.id in watched,
                modifier = Modifier
                    .cardSlot()
                    .onFocusChanged { if (it.isFocused) onFocused(item) }
                    .remembered("i:${item.id}")
                    .then(if (position == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            )
        }
    }
}

/** Довге OK картки сторінки — без протягування крізь кожен ряд (див. [ListingPage]). */
internal val LocalLongOpen = staticCompositionLocalOf<((CatalogItem) -> Unit)?> { null }

/** П'ять, а не шість: шоста картка 150dp не влазила в канву й обрізалась. */
private const val GRID_COLUMNS = 5


/**
 * Тло сторінок добірок — той самий графіт, що на всесвітах. Майже чорне тло каталогу
 * (#0A0D0F) з'їдало темні постери: межі карток зникали.
 */
internal val ListingBackground = Brush.verticalGradient(0f to LikhtarColors.UniverseTone, 1f to LikhtarColors.UniverseToneLow)

/**
 * Голова сторінки франшизи чи всесвіту: кадр на всю ширину з лого по центру, як у Disney+.
 * Кадр прокручується разом із головою, а знизу тане в рівне тло сторінки.
 */
@Composable
private fun FranchiseHead(listing: CatalogListing) {
    Box(modifier = Modifier.fullBleed().height(FranchiseHeadHeight)) {
        if (listing.backdropPath == CatalogListing.NEUTRAL_BACKDROP) {
            // Спільно-кіношний кадр — зал із кріслами, без жодного тайтлу (Commons, Author0612,
            // CC BY-SA 4.0; атрибуція в NOTICE.md).
            Image(
                painter = painterResource(R.drawable.ua_feed_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = TmdbImages.backdrop(listing.backdropPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(HeadFade))
        val brand = StreamingBrand.fromTitle(listing.title)
        if (listing.logoPath != null) {
            AsyncImage(
                model = TmdbImages.logo(listing.logoPath),
                contentDescription = listing.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).height(120.dp).fillMaxWidth(0.45f),
            )
        } else if (brand != null) {
            // Стрімінг — його wordmark фірмовим кольором, як на плитці, тільки великим.
            Text(
                text = brand.wordmark,
                style = TextStyle(
                    fontFamily = LikhtarType.DisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (brand.fontSize * 4).sp,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 3f), blurRadius = 16f),
                ),
                color = brand.color,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Text(
                text = listing.title.uppercase(),
                style = TextStyle(fontFamily = LikhtarType.Poster, fontSize = 54.sp, letterSpacing = 1.sp),
                color = LikhtarColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Банер одного тайтлу посеред сторінки всесвіту (Disney+ з «Війною нескінченності»): кадр на
 * всю ширину, ліворуч лого, рік і жанри, опис і кнопка.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleSpotlight(
    item: CatalogItem,
    ua: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Мітка над плашками («ФРАНШИЗА», «✦ Вибір Ліхтарика»); `null` — без неї. */
    kicker: String? = null,
    /** Замість «Фільм»/«Серіал» — наприклад, «Шоу». */
    kindLabel: String? = null,
    buttonLabel: String = stringResource(R.string.cinema_open_play),
    buttonModifier: Modifier = Modifier,
    /** Довге OK на кнопці — «Позначити», як на картках ряду. */
    onLongClick: (() -> Unit)? = null,
    /** Подача Реаліті (.spot прототипу reality): кадр праворуч, текст по центру ліворуч. */
    reality: Boolean = false,
) {
    if (reality) {
        RealitySpot(item, kindLabel, buttonLabel, buttonModifier, onClick, onLongClick, modifier)
        return
    }
    // .spot прототипу: панель 1728×460 px у полях сторінки, скруглення 24 px; кадр
    // center 22 % / cover, у фокусі ледь наближається (1.03 за .6s); ліворуч — завіса й текст.
    var focused by remember { mutableStateOf(false) }
    val zoom by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.03f else 1f, tween(600), label = "spot")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TitleSpotlightHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(LikhtarColors.Surface)
            .onFocusChanged { focused = it.hasFocus },
    ) {
        AsyncImage(
            model = TmdbImages.hero(item.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.56f),
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
            },
        )
        Box(modifier = Modifier.fillMaxSize().background(SpotlightScrim))
        // .in: 64 px від лівого краю, 52 px від низу, 760 px завширшки.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 26.dp)
                .width(380.dp),
        ) {
            // .mark img: до 520×110 px; чужий знак — з назвою плашкою.
            TitleLogoArea(
                logoPath = item.logoPath,
                title = item.title,
                height = 55.dp,
                widthFraction = 0.68f,
                localTitle = true,
                fallbackStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
            )
            // .meta: «★ 6.3 · 2025 · серіал · Драма · Детектив», UA, бурштиновий підпис (.soon).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 13.dp),
            ) {
                val series = item.kind == TitleKind.TV
                val meta = listOfNotNull(
                    item.rating.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "★ %.1f", it) },
                    item.year.takeIf { it.isNotBlank() },
                    kindLabel?.lowercase() ?: stringResource(if (series) R.string.cinema_kind_series_lower else R.string.cinema_kind_movie_lower),
                ) + item.genres.take(2)
                Text(
                    meta.joinToString(" · "),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 12.sp),
                    color = LikhtarColors.TextDim,
                    maxLines = 1,
                )
                if (ua) VoiceBadge()
                kicker?.let { Text(it, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 12.sp), color = LikhtarColors.Glow, maxLines = 1) }
            }
            // p: 23 px / 1.42, .88, 2 рядки, margin 14 px / 26 px.
            Text(
                text = item.overview,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp, lineHeight = 16.3.sp),
                color = LikhtarColors.TextPrimary.copy(alpha = 0.88f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp, bottom = 13.dp),
            )
            ProtoButton(
                label = buttonLabel,
                onClick = onClick,
                onLongClick = onLongClick,
                // Кнопка одна на всю ширину: ← → з неї вистрибували у верхнє меню.
                modifier = buttonModifier.focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
            )
        }
    }
}

/**
 * Широка секція Реаліті (.spot прототипу reality): панель 1728×440 px, кадр 1150 px праворуч
 * тане в --deep, ліворуч «Шоу · канал · рік», назва 60 px, опис у 3 рядки й «Відкрити».
 */
@Composable
private fun RealitySpot(
    item: CatalogItem,
    kindLabel: String?,
    buttonLabel: String,
    buttonModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val deep = LikhtarColors.BackgroundDeep
    Box(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(deep),
    ) {
        Box(Modifier.align(Alignment.CenterEnd).width(575.dp).fillMaxHeight()) {
            AsyncImage(
                model = TmdbImages.hero(item.backdropPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to deep, 0.3f to deep.copy(alpha = 0.6f), 0.62f to deep.copy(alpha = 0f))))
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
                .width(380.dp),
        ) {
            val kind = kindLabel ?: stringResource(R.string.cinema_kind_documentary)
            Text(
                listOf(kind, item.channel, item.year).filter(String::isNotBlank).joinToString(" · "),
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
                color = LikhtarColors.TextDim,
                maxLines = 1,
            )
            Text(
                item.title,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 30.sp, letterSpacing = (-0.9).sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                item.overview,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp, lineHeight = 16.3.sp),
                color = LikhtarColors.TextPrimary.copy(alpha = 0.78f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )
            ProtoButton(
                label = buttonLabel,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = buttonModifier.focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
            )
        }
    }
}

/** Розтягнути елемент колонки на всю ширину екрана поверх її бічних полів. */
private fun Modifier.fullBleed(): Modifier = layout { measurable, constraints ->
    val extra = (LikhtarDimens.ScreenPadding * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

private val TitleSpotlightHeight = 230.dp

private val UniverseTone = LikhtarColors.UniverseTone

/**
 * Кадр голови тане в тло сторінки знизу — довгим спадом у ТОЙ САМИЙ колір, що й тло.
 * Тло рівне, а не градієнт: голова прокручується, і під градієнтом її нижній край ставав
 * то світлішим, то темнішим за тло — виходив шов.
 */
private val HeadFade = Brush.verticalGradient(
    0.00f to UniverseTone.copy(alpha = 0f),
    0.35f to UniverseTone.copy(alpha = 0.15f),
    0.70f to UniverseTone.copy(alpha = 0.65f),
    1.00f to UniverseTone,
)

private val UniverseBackground = Brush.verticalGradient(0f to UniverseTone, 1f to UniverseTone)

/**
 * Банер франшизи дня (подача Disney+): кадр франшизи, ліворуч назва й «Детальніше»,
 * праворуч частини рядом. Лого — від фільму з назвою франшизи; немає — назва Oswald.
 */
@Composable
private fun SpotlightBand(
    row: CatalogRow,
    uaConfirmed: Set<Int>,
    onDetails: () -> Unit,
    onFocused: (CatalogItem) -> Unit,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SpotlightHeight)
            .clip(RoundedCornerShape(LikhtarDimens.CornerXLarge)),
    ) {
        AsyncImage(
            model = TmdbImages.backdrop(row.spotlight?.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(SpotlightScrim))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(280.dp).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val logo = row.spotlight?.logoPath
                if (logo != null) {
                    AsyncImage(
                        model = TmdbImages.logo(logo),
                        contentDescription = row.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(90.dp).fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = row.title.uppercase(),
                        style = TextStyle(fontFamily = LikhtarType.Poster, fontSize = 38.sp, lineHeight = 40.sp),
                        color = LikhtarColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                Text(
                    text = stringResource(R.string.cinema_collection_kicker),
                    style = LikhtarType.Label.copy(letterSpacing = 6.sp),
                    color = LikhtarColors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                LikhtarButton(label = stringResource(R.string.cinema_details_button), onClick = onDetails, primary = true)
            }
            LikhtarRail(modifier = Modifier.weight(1f)) {
                itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                    CatalogPosterCard(
                        title = item.title,
                        year = item.year,
                        posterUrl = TmdbImages.poster(item.posterPath),
                        overlayTitle = item.logoPath == null && !hasRealPoster(item),
                        rating = item.rating,
                        onClick = { onClick(item) },
                    onLongClick = LocalLongOpen.current?.takeIf { item.markable() }?.let { f -> { f(item) } },
                        uaAudio = item.tmdbId in uaConfirmed,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                            .onFocusChanged { if (it.isFocused) onFocused(item) }
                            .remembered("i:${item.id}"),
                    )
                }
            }
        }
    }
}

/**
 * Ряд сітки горизонтальних карток: [BANNER_COLUMNS] на всю ширину. Службового заголовка
 * («Знайдено 34 із 34») немає — голова сторінки вже сказала, що це; номер несе картка.
 */
@Composable
private fun BannerGridRow(
    items: List<CatalogItem>,
    startPosition: Int,
    numbered: Boolean,
    watched: Set<String>,
    uaConfirmed: Set<Int>,
    firstFocus: FocusRequester?,
    onFocused: (CatalogItem) -> Unit,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = BANNER_COLUMNS,
) {
    BoxWithConstraints(modifier = modifier) {
        val cardWidth = (maxWidth - LikhtarDimens.CardGap * (columns - 1)) / columns
        Row(horizontalArrangement = Arrangement.spacedBy(LikhtarDimens.CardGap)) {
            items.forEachIndexed { column, item ->
                val position = startPosition + column
                CatalogBannerCard(
                    item = item,
                    onClick = { onClick(item) },
                    onLongClick = LocalLongOpen.current?.takeIf { item.markable() }?.let { f -> { f(item) } },
                    width = cardWidth,
                    watched = item.id in watched,
                    uaAudio = item.tmdbId in uaConfirmed,
                    order = if (numbered) position + 1 else null,
                    note = item.note.ifBlank { null },
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) onFocused(item) }
                            .remembered("i:${item.id}")
                        .then(
                            if (position == 0 && firstFocus != null) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/** Горизонтальних карток на екран у рядах сторінки всесвіту (плюс край наступної). */
private const val RAIL_COLUMNS = 3

/** Чотири в ряд — як у Disney+: картка ширша, лого читається з дивана. */
private const val BANNER_COLUMNS = 4

/** Перша частина «Відкрити всі» — три великі горизонтальні в ряд. */
private const val FEATURED_COLUMNS = 3

private val FranchiseHeadHeight = 300.dp
private val SpotlightHeight = 280.dp

/** Ліва частина банера темна під текст, права — кадр майже на повну яскравість. */
// .spot .veil прототипу: 90deg .92 → .6 на 38 % → 0 на 68 %.
private val SpotlightScrim = Brush.horizontalGradient(
    0.00f to LikhtarColors.Background.copy(alpha = 0.92f),
    0.38f to LikhtarColors.Background.copy(alpha = 0.6f),
    0.68f to LikhtarColors.Background.copy(alpha = 0f),
)

/** Фрази очікування добірки: що справді відбувається, без «завантажую». */
@Composable
internal fun collectionPhrases(): List<String> = stringArrayResource(R.array.cinema_collection_phrases).toList()
