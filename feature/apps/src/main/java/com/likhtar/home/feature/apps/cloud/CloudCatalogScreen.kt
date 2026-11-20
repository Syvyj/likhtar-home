package com.likhtar.home.feature.apps.cloud


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.likhtar.home.core.designsystem.RailRestore
import com.likhtar.home.core.designsystem.ScrollRailTo
import com.likhtar.home.core.designsystem.leave
import com.likhtar.home.core.designsystem.rememberRailRestore
import com.likhtar.home.core.designsystem.returnPoint
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.likhtar.home.core.designsystem.pinTop
import com.likhtar.home.core.designsystem.cardSlot
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.data.cloud.CloudBlock
import com.likhtar.home.core.data.cloud.CloudFilter
import com.likhtar.home.core.data.cloud.CloudGame
import com.likhtar.home.core.data.cloud.CloudService
import com.likhtar.home.core.data.cloud.arrangeBlocks
import com.likhtar.home.core.data.cloud.cloudBlocks
import com.likhtar.home.core.data.cloud.mainService
import com.likhtar.home.core.data.cloud.passes
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.TextInputDialog
import com.likhtar.home.core.designsystem.requestAfterFrame
import com.likhtar.home.feature.apps.R
import kotlinx.coroutines.launch

private const val ROW_MAX = 10
private const val GRID_COLS = 10
private const val PLAIN_COLS = 7

/** Що показує сітка: блок з «Відкрити всі» чи весь відфільтрований список («▦ Сіткою»). */
private const val GRID_ALL = "*"

/**
 * Сторінка каталогу хмарних ігор (макет: #page): ряди-блоки, як головна Кінозалу, угорі — пошук і
 * фільтри. «Відкрити всі» блоку і «▦ Сіткою» — сітка з переходом за літерою (макет: #all).
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CloudCatalogScreen(
    search: Boolean,
    mine: Boolean,
    /** «Відкрити всі» з ряду приставки на «Іграх»: одразу сітка її образів, як «Стрімінги» Кінозалу. */
    system: String? = null,
    onOpenGame: (String) -> Unit,
    viewModel: CloudGamesViewModel = hiltViewModel(),
) {
    val catalog by viewModel.catalog.collectAsState()
    val library by viewModel.library.collectAsState()
    val prefs by viewModel.blocks.collectAsState()
    var menu by remember { mutableStateOf<CloudGame?>(null) }
    var filter by remember { viewModel.catalogFilter ?: mutableStateOf(CloudFilter(mine = mine, retro = system != null, system = system)).also { viewModel.catalogFilter = it } }
    var grid by rememberSaveable { mutableStateOf(if (system != null) GRID_ALL else null) }
    var searching by rememberSaveable { mutableStateOf(search) }
    var pick by remember { mutableStateOf<String?>(null) }
    var editingBlocks by remember { mutableStateOf(false) }

    // Лише підключені сервіси («Мої сервіси»); «Лише мої» — правило бібліотеки.
    val all by viewModel.games.collectAsState()
    val visible = remember(all, library.services) { all.filter(library::visible) }
    val games = remember(visible, filter, library) { visible.filter { it.passes(filter, library::isMine) } }
    val allBlocks = remember(games) { cloudBlocks(games) }
    val blocks = remember(allBlocks, prefs) {
        if (allBlocks.firstOrNull()?.key.isNullOrEmpty()) allBlocks else arrangeBlocks(allBlocks, prefs.order, prefs.hidden)
    }
    val gridGames = remember(grid, games, allBlocks) {
        when (grid) {
            null -> emptyList()
            GRID_ALL -> games
            "popular" -> games.sortedByDescending { it.popularity }
            else -> allBlocks.firstOrNull { it.key == grid }?.games.orEmpty().sortedBy { it.title.lowercase() }
        }
    }
    BackHandler(enabled = grid != null && system == null) { grid = null }

    val firstFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Повернення з картки гри — на ту саму картку, спільним RailRestore (як Кінозал і «Ігри»).
    val returnTile = rememberSaveable { mutableStateOf<String?>(null) }
    val restore = rememberRailRestore(returnTile) { it != null }
    LaunchedEffect(catalog.loaded, grid) {
        if (catalog.loaded && !searching && !restore.pending) runCatching { firstFocus.requestAfterFrame() }
    }
    LaunchedEffect(catalog.loaded) {
        if (catalog.loaded) restore.restore(list) { runCatching { firstFocus.requestAfterFrame() } }
    }

    Box(Modifier.fillMaxSize().background(LikhtarColors.Background)) {
        if (!catalog.loaded || (visible.isEmpty() && catalog.refreshing)) {
            Text(stringResource(R.string.cloud_loading), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 15.sp), color = LikhtarColors.TextDim, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        // Як RailsColumn Кінозалу: колонка сама фокус не доганяє (інакше спершу доїжджав ряд, а тоді
        // підсвічувалась картка — «фріз»), а ряд у фокусі одним рухом стає вгорі, лише якщо він не там.
        var pinned by remember { mutableStateOf<Any?>(null) }
        var pinTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(pinned, pinTick) {
            val key = pinned ?: return@LaunchedEffect
            val index = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index ?: return@LaunchedEffect
            // Шапка з пошуком і фільтрами — 0 і 1; перший блок (2) теж лишає її на екрані.
            list.pinTop(if (index <= 2) 0 else index)
        }
        val onRow: (Any) -> Unit = { key -> pinned = key; pinTick++ }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.likhtar.home.core.designsystem.NoBringIntoView) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = LikhtarDimens.ScreenPadding, end = LikhtarDimens.ScreenPadding, top = 20.dp, bottom = 60.dp),
        ) {
            item(key = "head") {
                Box(Modifier.onFocusChanged { if (it.hasFocus) onRow("head") }) {
                Header(
                    title = when {
                        system != null && grid == GRID_ALL -> system
                        grid == GRID_ALL || grid == null -> stringResource(if (filter.mine) R.string.cloud_mine else R.string.cloud_all)
                        else -> blockTitle(grid!!)
                    },
                    count = stringResource(R.string.cloud_count, if (grid != null) gridGames.size else games.size, visible.size),
                    query = filter.query,
                    onSearch = { searching = true },
                    stale = catalog.failed,
                )
                }
            }
            if (grid == null) {
                item(key = "chips") {
                    Box(Modifier.onFocusChanged { if (it.hasFocus) onRow("chips") }) {
                    FilterChips(filter, games.isEmpty(), library.services, visible.any { it.rom != null }, onFilter = { filter = it }, onPick = { pick = it }, onGrid = { grid = GRID_ALL }, onBlocks = { editingBlocks = true })
                    }
                }
                blocksContent(blocks, firstFocus, onOpenGame, onOpenAll = { grid = it }, onMenu = { menu = it }, services = library.services, onRow = onRow, restore = restore)
            } else {
                gridContent(gridGames, firstFocus, onOpenGame, onLetter = { row -> scope.launch { list.scrollToItem(row + 2) } }, onMenu = { menu = it }, services = library.services, onRow = onRow, restore = restore)
            }
        }
        }
    }

    menu?.let { g -> CloudGameMenu(g, viewModel, onDismiss = { menu = null }) }
    if (searching) {
        TextInputDialog(
            title = stringResource(R.string.cloud_search_title),
            initialValue = filter.query,
            placeholder = stringResource(R.string.cloud_search_hint),
            allowClear = true,
            onSubmit = { q -> filter = filter.copy(query = q); searching = false },
            onDismiss = { searching = false },
        )
    }
    pick?.let { key ->
        PickPanel(
            title = stringResource(if (key == "stores") R.string.cloud_store else R.string.cloud_genre),
            options = remember(visible, filter, key) {
                val base = visible.filter { it.passes(if (key == "stores") filter.copy(stores = emptySet()) else filter.copy(genres = emptySet())) }
                base.flatMap { if (key == "stores") it.stores else it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
            },
            label = { if (key == "stores") it else genreLabel(it) },
            selected = if (key == "stores") filter.stores else filter.genres,
            onChange = { sel -> filter = if (key == "stores") filter.copy(stores = sel) else filter.copy(genres = sel) },
            onDismiss = { pick = null },
        )
    }
    if (editingBlocks) {
        BlocksPanel(
            blocks = arrangeBlocks(cloudBlocks(visible), prefs.order, prefs.hidden, withHidden = true),
            hidden = prefs.hidden,
            onMove = viewModel::moveBlock,
            onHidden = viewModel::setHidden,
            onReset = viewModel::resetBlocks,
            onDismiss = { editingBlocks = false },
        )
    }
}

@Composable
private fun Header(title: String, count: String, query: String, onSearch: () -> Unit, stale: Set<CloudService>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 23.sp), color = LikhtarColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(count, style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = LikhtarColors.TextDim)
            Spacer(Modifier.weight(1f))
            FocusableSurface(
                onClick = onSearch,
                modifier = Modifier.width(310.dp).height(24.dp),
                shape = RoundedCornerShape(7.dp),
                background = LikhtarColors.SurfaceSoft,
                focusedBackground = LikhtarColors.Paper,
                borderColor = LikhtarColors.Border,
                focusedScale = 1.02f,
            ) { focused ->
                Text(
                    if (query.isBlank()) stringResource(R.string.cloud_search_ph) else stringResource(R.string.cloud_search_label, query),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.sp),
                    color = if (focused) LikhtarColors.OnGlassActive else if (query.isBlank()) LikhtarColors.TextDim else LikhtarColors.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 9.dp),
                )
            }
        }
        stale.forEach { s ->
            Text(stringResource(R.string.cloud_stale, serviceName(s)), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.WarmGold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Пігулка фільтра (макет: .f): увімкнений — «✓» і світліше тло, у фокусі — світле скло. */
@Composable
private fun FChip(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Color? = null) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(21.dp),
        shape = RoundedCornerShape(6.dp),
        background = if (on) LikhtarColors.BorderStrong else LikhtarColors.SurfaceSoft,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1.04f,
    ) { focused ->
        Row(Modifier.padding(horizontal = 9.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (on) Text("✓", style = TextStyle(fontSize = 8.sp), color = if (focused) LikhtarColors.OnGlassActive else Color.White)
            dot?.let { Box(Modifier.size(6.dp).clip(CircleShape).background(it)) }
            Text(text, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = if (focused) LikhtarColors.OnGlassActive else if (on) Color.White else LikhtarColors.WarmSand, maxLines = 1)
        }
    }
}

@Composable
private fun pickedLabel(values: Set<String>, label: @Composable (String) -> String): String = when {
    values.isEmpty() -> stringResource(R.string.cloud_f_any)
    values.size <= 2 -> values.map { label(it) }.joinToString(", ")
    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.cloud_f_n, values.size, values.size)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(
    filter: CloudFilter,
    empty: Boolean,
    services: Set<CloudService>,
    hasRetro: Boolean,
    onFilter: (CloudFilter) -> Unit,
    onPick: (String) -> Unit,
    onGrid: () -> Unit,
    onBlocks: () -> Unit,
) {
    Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FChip(stringResource(R.string.cloud_f_mine), filter.mine, { onFilter(filter.copy(mine = !filter.mine)) })
            if (hasRetro) FChip(stringResource(R.string.cloud_f_retro), filter.retro, { onFilter(filter.copy(retro = !filter.retro)) }, dot = RetroColor)
            CloudService.entries.filter { it in services }.forEach { s ->
                FChip(serviceName(s), filter.service == s, { onFilter(filter.copy(service = if (filter.service == s) null else s)) }, dot = brand(s))
            }
            FChip(stringResource(R.string.cloud_f_store, pickedLabel(filter.stores) { it }), filter.stores.isNotEmpty(), { onPick("stores") })
            FChip(stringResource(R.string.cloud_f_genre, pickedLabel(filter.genres) { genreLabel(it) }), filter.genres.isNotEmpty(), { onPick("genres") })
        }
        // .pchips .f.grid { margin-left: auto } — сітка й блоки праворуч.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FChip(stringResource(R.string.cloud_grid), false, onGrid)
            FChip(stringResource(R.string.cloud_blocks), false, onBlocks)
        }
        }
        if (empty) Text(stringResource(R.string.cloud_nothing), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(vertical = 25.dp))
    }
}

/** Блоки: перший — великими картками, далі постери й горизонтальні по черзі; після «Популярних» — банер. */
private fun androidx.compose.foundation.lazy.LazyListScope.blocksContent(
    blocks: List<CloudBlock>,
    firstFocus: FocusRequester,
    onOpenGame: (String) -> Unit,
    onOpenAll: (String) -> Unit,
    onMenu: (CloudGame) -> Unit,
    services: Set<CloudService>,
    onRow: (Any) -> Unit,
    restore: RailRestore,
) {
    // Індекс елемента колонки для точки повернення: 0 — шапка, 1 — фільтри.
    var at = 2
    fun opening(key: String, action: () -> Unit): () -> Unit = { restore.leave(key); action() }
    blocks.forEachIndexed { i, block ->
        if (block.key.isEmpty()) {
            // Мало ігор (пошук): уся сторінка рядами постерів, без «Відкрити всі».
            val rows = block.games.chunked(PLAIN_COLS)
            val base = at
            itemsIndexed(rows, key = { r, _ -> "plain$r" }) { r, chunk ->
                Row(Modifier.onFocusChanged { if (it.hasFocus) onRow("plain$r") }, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    chunk.forEachIndexed { c, g ->
                        CloudCard(g, CardShape.POSTER, opening("p|" + g.key) { onOpenGame(g.key) }, Modifier.cardSlot().then(if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier).returnPoint(restore, "p|" + g.key, base + r), onLongClick = { onMenu(g) }, services = services)
                    }
                }
            }
            return@forEachIndexed
        }
        val shape = if (i == 0) CardShape.FEAT else if ((i - 1) % 2 == 0) CardShape.POSTER else CardShape.WIDE
        val row = at++
        item(key = "block-${block.key}") {
            val rail = androidx.compose.foundation.lazy.rememberLazyListState()
            val shown = block.games.take(ROW_MAX)
            restore.ScrollRailTo(rail) { tile -> (shown.map { "b|${block.key}|" + it.key } + "b|${block.key}|more").indexOf(tile) }
            // Як ряд Кінозалу (CollectionRail): заголовок поверх ряду, картки зі своїм запасом під збільшення.
            Column(Modifier.padding(top = 10.dp).onFocusChanged { if (it.hasFocus) onRow("block-${block.key}") }) {
                Row(Modifier.zIndex(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    com.likhtar.home.core.designsystem.SectionTitle(blockTitle(block.key))
                    Text(block.games.size.toString(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(bottom = 7.dp))
                }
                LikhtarRail(state = rail, gap = 11.dp) {
                    itemsIndexed(shown, key = { _, g -> g.key }) { c, g ->
                        val k = "b|${block.key}|" + g.key
                        CloudCard(g, shape, opening(k) { onOpenGame(g.key) }, Modifier.cardSlot().then(if (i == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier).returnPoint(restore, k, row), onLongClick = { onMenu(g) }, services = services)
                    }
                    if (block.games.size > ROW_MAX) item(key = "more") { OpenAllCard(block.games.size, shape, { onOpenAll(block.key) }, Modifier.cardSlot().returnPoint(restore, "b|${block.key}|more", row)) }
                }
            }
        }
        if (block.popular) (block.games.getOrNull(ROW_MAX) ?: block.games.getOrNull(1))?.let { g ->
            val spotRow = at++
            item(key = "spot") { Box(Modifier.onFocusChanged { if (it.hasFocus) onRow("spot") }) { Spot(g, services, Modifier.returnPoint(restore, "spot|" + g.key, spotRow), opening("spot|" + g.key) { onOpenGame(g.key) }) } }
        }
    }
}

/** Банер між блоками (макет: .spot) — гра, що не влізла в «Популярні». */
@Composable
private fun Spot(game: CloudGame, services: Set<CloudService>, modifier: Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().height(215.dp).then(modifier),
        shape = RoundedCornerShape(9.dp),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.Surface,
        borderColor = Color.Transparent,
        focusedScale = 1.02f,
    ) { _ ->
        FallbackImage(listOfNotNull(game.hero, game.cover, game.cover2).distinct(), null, Modifier.fillMaxSize(), Alignment.TopCenter)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to LikhtarColors.ShadeDeep.copy(alpha = 0.95f), 0.4f to LikhtarColors.ShadeDeep.copy(alpha = 0.70f), 0.75f to LikhtarColors.ShadeDeep.copy(alpha = 0f))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 28.dp).width(410.dp)) {
            Text(game.genres.firstOrNull()?.let { genreLabel(it) }?.uppercase() ?: stringResource(R.string.cloud_game).uppercase(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp, letterSpacing = 1.4.sp), color = LikhtarColors.WarmGold)
            GameTitle(game.title, LikhtarType.HeroTitle.copy(fontSize = 26.sp, lineHeight = 27.sp), modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
            CloudChips(game, services = services)
            game.mainService(services)?.let {
                Text(stringResource(R.string.cloud_play_in, serviceName(it)), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private val LETTERS = listOf('#') + ('A'..'Z')
private fun letterOf(g: CloudGame): Char = g.title.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/** Сітка по 10 з переходом за літерою (макет: #all). */
private fun androidx.compose.foundation.lazy.LazyListScope.gridContent(
    games: List<CloudGame>,
    firstFocus: FocusRequester,
    onOpenGame: (String) -> Unit,
    onLetter: (Int) -> Unit,
    onMenu: (CloudGame) -> Unit,
    services: Set<CloudService>,
    onRow: (Any) -> Unit,
    restore: RailRestore,
) {
    val rows = games.chunked(GRID_COLS)
    item(key = "letters") {
        val has = games.map(::letterOf).toSet()
        Row(Modifier.padding(top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            LETTERS.forEach { l ->
                val at = games.indexOfFirst { letterOf(it) == l }
                FocusableSurface(
                    onClick = { if (at >= 0) onLetter(at / GRID_COLS) },
                    modifier = Modifier.size(21.dp, 20.dp),
                    shape = RoundedCornerShape(5.dp),
                    background = if (l in has) LikhtarColors.Surface else Color.Transparent,
                    focusedBackground = LikhtarColors.Paper,
                    borderColor = Color.Transparent,
                    focusedScale = 1.06f,
                ) { focused ->
                    Text(l.toString(), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp), color = if (focused) LikhtarColors.OnGlassActive else if (l in has) LikhtarColors.TextPrimary else LikhtarColors.BorderMuted, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
    itemsIndexed(rows, key = { r, _ -> "grid$r" }) { r, chunk ->
        Row(Modifier.onFocusChanged { if (it.hasFocus) onRow("grid$r") }, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            chunk.forEachIndexed { c, g ->
                // 0 — шапка, 1 — абетка: ряд сітки r — елемент r + 2.
                CloudCard(g, CardShape.SMALL, { restore.leave("g|" + g.key); onOpenGame(g.key) }, Modifier.cardSlot().then(if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier).returnPoint(restore, "g|" + g.key, r + 2), onLongClick = { onMenu(g) }, services = services)
            }
        }
    }
}

/** Магазин чи жанр — бічною панеллю з чекбоксами (макет: #pick); кілька разом, застосовується одразу. */
@Composable
private fun PickPanel(
    title: String,
    options: List<Pair<String, Int>>,
    label: @Composable (String) -> String,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestAfterFrame() } }
    LikhtarDialog(onDismiss = onDismiss, width = 310.dp, side = true) {
        Text(title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp), color = LikhtarColors.TextPrimary)
        Text(stringResource(R.string.cloud_pick_hint), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 11.dp))
        LazyColumn {
            item { PickRow(stringResource(R.string.cloud_pick_all), null, selected.isEmpty(), { onChange(emptySet()) }, Modifier.focusRequester(first)) }
            items(options, key = { it.first }) { (value, n) ->
                PickRow(label(value), n, value in selected, { onChange(if (value in selected) selected - value else selected + value) })
            }
        }
    }
}

@Composable
private fun PickRow(text: String, count: Int?, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(26.dp),
        shape = RoundedCornerShape(6.dp),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1.0f,
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(if (on) LikhtarColors.WarmGold else Color.Transparent)
                    .then(if (on) Modifier else Modifier.background(Color.Transparent)),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Text("✓", style = TextStyle(fontSize = 9.sp), color = LikhtarColors.OnGlassActive)
                else Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(LikhtarColors.WarmSubtle.copy(alpha = 0.20f)))
            }
            Text(text, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            count?.let { Text(it.toString(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp), color = if (focused) LikhtarColors.BorderMuted else LikhtarColors.TextDim) }
        }
    }
}

/** «⚙ Блоки» — як групи каналів у ТБ Ефірі: ↑ Вище / ↓ Нижче / Сховати; сховані — унизу з «Показати». */
@Composable
private fun BlocksPanel(
    blocks: List<CloudBlock>,
    hidden: Set<String>,
    onMove: (List<String>, String, Int) -> Unit,
    onHidden: (String, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestAfterFrame() } }
    val visible = blocks.filter { it.key !in hidden }
    val off = blocks.filter { it.key in hidden }
    val keys = visible.map { it.key }
    LikhtarDialog(onDismiss = onDismiss, width = 450.dp, side = true) {
        Text(stringResource(R.string.cloud_blocks_title), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp), color = LikhtarColors.TextPrimary)
        Text(stringResource(R.string.cloud_blocks_hint), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 11.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            itemsIndexed(visible, key = { _, b -> "v" + b.key }) { i, b ->
                BlockRow(blockTitle(b.key), b.games.size, dim = false) {
                    BlockAction(if (i == 0) stringResource(R.string.cloud_block_first) else stringResource(R.string.cloud_block_up), i > 0, { onMove(keys, b.key, -1) }, if (i == 0) Modifier.focusRequester(first) else Modifier)
                    BlockAction(if (i == visible.lastIndex) stringResource(R.string.cloud_block_last) else stringResource(R.string.cloud_block_down), i < visible.lastIndex, { onMove(keys, b.key, 1) })
                    BlockAction(stringResource(R.string.cloud_block_hide), true, { onHidden(b.key, true) })
                }
            }
            if (off.isNotEmpty()) item(key = "hidden-head") {
                Text(stringResource(R.string.cloud_blocks_hidden, off.size).uppercase(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp, letterSpacing = 1.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 11.dp, bottom = 4.dp))
            }
            items(off, key = { "h" + it.key }) { b ->
                BlockRow(blockTitle(b.key), b.games.size, dim = true) { BlockAction(stringResource(R.string.cloud_block_show), true, { onHidden(b.key, false) }) }
            }
            item(key = "reset") { Box(Modifier.padding(top = 9.dp)) { BlockAction(stringResource(R.string.cloud_block_reset), true, onReset, width = 140.dp) } }
        }
    }
}

@Composable
private fun BlockRow(name: String, count: Int, dim: Boolean, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(name, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = if (dim) LikhtarColors.WarmMuted else LikhtarColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text(count.toString(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(end = 6.dp))
        actions()
    }
}

@Composable
private fun BlockAction(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 64.dp) {
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.height(21.dp).width(width),
        shape = RoundedCornerShape(6.dp),
        background = if (enabled) LikhtarColors.SurfaceElevated else Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1.04f,
    ) { focused ->
        Text(text, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp), color = if (focused) LikhtarColors.OnGlassActive else if (enabled) LikhtarColors.WarmSand else LikhtarColors.WarmSubtle, maxLines = 1, modifier = Modifier.align(Alignment.Center))
    }
}
